package com.mandro.mark7.presentation.ui.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.CmdDir
import com.mandro.mark7.domain.model.MotorCommand
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManualUiState(
    val select: List<Boolean> = List(6) { true },
    val posDeg: List<Int> = List(6) { 0 },
    val speedRaw: Int = 20_000,
    val currentMa: Int = 900,
)

/**
 * 직접 구동(CMD 프레임) 화면. 손가락 선택 + 위치/속도/전류 + GRASP/RELEASE/STOP.
 */
@HiltViewModel
class ManualViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualUiState())
    val uiState = _uiState.asStateFlow()

    fun toggleFinger(index: Int) = _uiState.update {
        it.copy(select = it.select.toMutableList().apply { this[index] = !this[index] })
    }

    fun setPos(index: Int, value: Int) = _uiState.update {
        it.copy(posDeg = it.posDeg.toMutableList().apply { this[index] = value })
    }

    fun setSpeed(value: Int) = _uiState.update { it.copy(speedRaw = value) }
    fun setCurrent(value: Int) = _uiState.update { it.copy(currentMa = value) }

    fun send(dir: CmdDir) = viewModelScope.launch {
        val s = _uiState.value
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

    fun resetPower() = viewModelScope.launch {
        repo.sendCommand(MotorCommand.allFingers(CmdDir.RESET_POWER))
    }
}
