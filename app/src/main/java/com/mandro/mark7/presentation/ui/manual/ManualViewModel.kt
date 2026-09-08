package com.mandro.mark7.presentation.ui.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.CmdDir
import com.mandro.mark7.domain.model.ManualPreset
import com.mandro.mark7.domain.model.MotorCommand
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.mandro.mark7.domain.model.HandDof

data class ManualUiState(
    val dof: HandDof = HandDof.DEFAULT,
    val select: List<Boolean> = List(HandDof.DEFAULT.dof) { false },
    val useCustomPower: Boolean = false,
    val speedRaw: Int = 20_000,
    val currentMa: Int = 900,
    val posDeg: List<Int> = List(HandDof.DEFAULT.dof) { 0 },
    /** 전송 직후 짧게 점등되는 방향 (버튼 플래시용). */
    val lastCommandedDir: CmdDir? = null,
    /** 프리셋 탭으로 "준비"된 방향. 이 값이 있으면 해당 방향 버튼만 활성화되고, 그 버튼을 눌러야 실제 전송된다. */
    val armedDir: CmdDir? = null,
    val lastExecutedPresetId: String? = null,
) {
    val selectedCount: Int get() = select.count { it }
}

/**
 * 직접 구동(CMD 프레임) ViewModel.
 * - 손가락 선택
 * - 동작 실행 (쥐기 / 펴기)
 * - 세부 파워 조절 (속도, 전류)
 * - 원터치 동작 프리셋 (기본 6개 + 사용자 생성/수정/삭제)
 */
@HiltViewModel
class ManualViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualUiState())
    val uiState = _uiState.asStateFlow()

    val presets: StateFlow<List<ManualPreset>> = repo.manualPresets

    init {
        viewModelScope.launch {
            repo.activeDof.collect { dof ->
                _uiState.update { current ->
                    if (current.dof == dof && current.select.size == dof.dof) {
                        current
                    } else {
                        current.copy(
                            dof = dof,
                            select = List(dof.dof) { i -> current.select.getOrElse(i) { false } },
                            posDeg = List(dof.dof) { i -> current.posDeg.getOrElse(i) { 0 } },
                        )
                    }
                }
            }
        }
    }

    // 액션 실행 후 손가락 선택·쥐기/펴기 버튼이 함께 점등됐다가 같은 시점에 중립으로 복귀하도록
    // 단일 타이머로 관리한다. 화면에는 별도 펄스 상태를 두지 않는다.
    private var pulseJob: Job? = null

    /** 진행 중인 모든 펄스(프리셋 자동 복귀 / 쥐기·펴기 점등)를 즉시 취소한다. */
    private fun cancelPulse() {
        pulseJob?.cancel()
        pulseJob = null
    }

    fun toggleFinger(index: Int) {
        cancelPulse()
        _uiState.update {
            it.copy(
                select = it.select.toMutableList().apply { this[index] = !this[index] },
                lastExecutedPresetId = null,
                lastCommandedDir = null,
                armedDir = null,   // 손가락을 직접 만지면 프리셋 준비 상태 해제 → 두 방향 다 사용 가능
            )
        }
    }

    fun setAllFingers(selected: Boolean) {
        cancelPulse()
        _uiState.update {
            it.copy(
                select = List(it.select.size) { selected },
                lastExecutedPresetId = null,
                lastCommandedDir = null,
                armedDir = null,
            )
        }
    }

    /**
     * 하단 탭을 다시 눌러(또는 페이저에서 화면이 재구성돼) Manual 화면이 새로 들어올 때 호출.
     * 손가락 선택·프리셋 준비·세부 조절 임시값을 모두 처음 상태로 되돌린다 → 다른 탭과 동일하게
     * "탭을 다시 누르면 초기 화면"으로 보인다. (저장되는 프리셋 목록은 repo 소유라 영향 없음.)
     */
    fun resetTransientState() {
        cancelPulse()
        val dof = repo.activeDof.value
        _uiState.value = ManualUiState(
            dof = dof,
            select = List(dof.dof) { false },
            posDeg = List(dof.dof) { 0 },
        )
    }

    fun setUseCustomPower(enabled: Boolean) = _uiState.update {
        it.copy(useCustomPower = enabled)
    }

    fun resetPowerSettings() = _uiState.update {
        it.copy(useCustomPower = false, speedRaw = DEFAULT_SPEED_RAW, currentMa = DEFAULT_CURRENT_MA)
    }

    fun setSpeed(value: Int) = _uiState.update {
        it.copy(speedRaw = value, useCustomPower = true)
    }

    fun setCurrent(value: Int) = _uiState.update {
        it.copy(currentMa = value, useCustomPower = true)
    }

    fun setPos(index: Int, value: Int) = _uiState.update {
        it.copy(posDeg = it.posDeg.toMutableList().apply { this[index] = value })
    }

    /**
     * 쥐기(GRASP) 또는 펴기(RELEASE) 명령 전송.
     * armedDir·select·lastExecutedPresetId 는 펄스가 끝나는 시점에 한꺼번에 리셋한다 →
     * 점등되는 0.55초 동안 반대(안 누른) 버튼이 잠깐 활성화되는 일이 없다.
     */
    fun send(dir: CmdDir) {
        cancelPulse()
        _uiState.update {
            it.copy(
                lastCommandedDir = dir,
                armedDir = dir,
            )
        }
        val s = _uiState.value
        viewModelScope.launch {
            repo.sendCommand(
                MotorCommand(
                    select = s.select.toBooleanArray(),
                    speedRaw = s.speedRaw,
                    currentMa = s.currentMa,
                    posDeg = s.posDeg.toIntArray(),
                    dir = dir,
                ),
            )
        }
        pulseJob = viewModelScope.launch {
            delay(PULSE_MS)
            _uiState.update {
                it.copy(
                    lastCommandedDir = null,
                    armedDir = null,
                    lastExecutedPresetId = null,
                    select = List(it.select.size) { false },
                )
            }
        }
    }

    /**
     * 프리셋 탭 = "준비"만 한다. 손가락 선택을 프리셋 값으로 채우고 해당 방향을 armedDir 로 설정 →
     * 쥐기/펴기 버튼 중 그 방향만 활성화된다. 실제 전송은 사용자가 그 버튼을 눌렀을 때([send]).
     * 이미 준비된 프리셋을 다시 탭하면 해제. 준비 시 true, 해제 시 false 반환.
     */
    fun executePreset(preset: ManualPreset): Boolean {
        cancelPulse()

        if (_uiState.value.lastExecutedPresetId == preset.id) {
            // 이미 준비된 상태에서 한 번 더 탭하면 해제
            _uiState.update {
                it.copy(
                    lastExecutedPresetId = null,
                    select = List(6) { false },
                    armedDir = null,
                    lastCommandedDir = null,
                )
            }
            return false
        }

        // 준비만: 손가락 선택 채우고 방향 버튼 활성화. 전송 안 함.
        _uiState.update {
            it.copy(
                select = preset.fingers,
                armedDir = preset.direction,
                lastExecutedPresetId = preset.id,
                lastCommandedDir = null,
            )
        }

        return true
    }

    /** 새 프리셋 생성 */
    fun createPreset(
        name: String,
        emoji: String,
        imageUri: String?,
        imageBiasX: Float,
        imageBiasY: Float,
        fingers: List<Boolean>,
        direction: CmdDir,
    ) = viewModelScope.launch {
        val newPreset = ManualPreset(
            id = "preset_" + System.currentTimeMillis(),
            name = name.trim().ifEmpty { "내 동작" },
            emoji = emoji.trim().ifEmpty { "✊" },
            fingers = fingers,
            direction = direction,
            isDefault = false,
            imageUri = imageUri,
            imageBiasX = imageBiasX,
            imageBiasY = imageBiasY,
        )
        repo.saveManualPresets(presets.value + newPreset)
    }

    /** 프리셋 수정 */
    fun updatePreset(updated: ManualPreset) = viewModelScope.launch {
        if (_uiState.value.lastExecutedPresetId == updated.id) {
            _uiState.update {
                it.copy(
                    select = updated.fingers,
                    armedDir = updated.direction,
                )
            }
        }
        val list = presets.value.map { if (it.id == updated.id) updated else it }
        repo.saveManualPresets(list)
    }

    /** 프리셋 삭제 */
    fun deletePreset(id: String) = viewModelScope.launch {
        if (_uiState.value.lastExecutedPresetId == id) {
            _uiState.update {
                it.copy(
                    lastExecutedPresetId = null,
                    select = List(6) { false },
                    armedDir = null,
                )
            }
        }
        val list = presets.value.filterNot { it.id == id }
        repo.saveManualPresets(list)
    }

    /** 프리셋 순서 앞으로(왼쪽) 이동 */
    fun movePresetEarlier(id: String) = viewModelScope.launch {
        val list = presets.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx > 0) {
            val item = list.removeAt(idx)
            list.add(idx - 1, item)
            repo.saveManualPresets(list)
        }
    }

    /** 프리셋 순서 뒤로(오른쪽) 이동 */
    fun movePresetLater(id: String) = viewModelScope.launch {
        val list = presets.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx in 0 until list.size - 1) {
            val item = list.removeAt(idx)
            list.add(idx + 1, item)
            repo.saveManualPresets(list)
        }
    }

    /** 프리셋 두 항목 순서 맞교환 */
    fun swapPresets(fromIndex: Int, toIndex: Int) = viewModelScope.launch {
        val list = presets.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            repo.saveManualPresets(list)
        }
    }

    /** 갭(gap) 모델 기반 프리셋 이동 */
    fun movePreset(fromIndex: Int, toGap: Int) = viewModelScope.launch {
        val list = presets.value.toMutableList()
        if (fromIndex !in list.indices) return@launch
        val item = list.removeAt(fromIndex)
        val insertAt = (if (toGap > fromIndex) toGap - 1 else toGap).coerceIn(0, list.size)
        list.add(insertAt, item)
        repo.saveManualPresets(list)
    }

    /** 드래그 종료 시 1회 호출: fromIndex 항목을 toIndex 위치로 옮겨 저장 (드래그 중엔 화면 로컬만 재정렬). */
    fun movePresetToIndex(fromIndex: Int, toIndex: Int) = viewModelScope.launch {
        val list = presets.value.toMutableList()
        if (fromIndex !in list.indices || fromIndex == toIndex) return@launch
        val item = list.removeAt(fromIndex)
        list.add(toIndex.coerceIn(0, list.size), item)
        repo.saveManualPresets(list)
    }

    /**
     * 삭제된 기본 프리셋만 다시 채운다. 현재 프리셋(커스텀·편집·순서)은 그대로 두고,
     * id 기준으로 빠져 있는 [ManualPreset.DEFAULT_PRESETS] 항목만 뒤에 덧붙인다.
     */
    fun resetPresetsToDefault() = viewModelScope.launch {
        val current = presets.value
        val existingIds = current.mapTo(mutableSetOf()) { it.id }
        val missing = ManualPreset.DEFAULT_PRESETS.filter { it.id !in existingIds }
        if (missing.isEmpty()) return@launch
        repo.saveManualPresets(current + missing)
    }

    companion object {
        /** 액션 실행 후 손가락 선택·방향 버튼이 점등돼 있는 시간(ms). 화면 애니메이션과 공유. */
        const val PULSE_MS = 550L

        /** 세부 조절 '기본값'이 되돌리는 시스템 기본 전류/속도. 화면 슬라이더 리셋과 공유. */
        const val DEFAULT_CURRENT_MA = 900
        const val DEFAULT_SPEED_RAW = 20_000
    }
}
