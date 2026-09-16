package com.mandro.mark7.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.hand.GlobalSettings
import com.mandro.mark7.domain.model.hand.HandConfig
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

import com.mandro.mark7.domain.model.hand.HandDof

data class SettingsUiState(
    val dof: HandDof = HandDof.DEFAULT,
    val config: HandConfig = HandConfig.DEFAULT,
    /** SET 전송 진행 중 (버튼 비활성/문구 전환용). */
    val pushing: Boolean = false,
)

/**
 * SET 프레임의 전역 설정 편집. 슬라이더 조작은 화면 로컬 draft 로만 반영되고, 토글의 '전송' 버튼을
 * 눌러 [applyAndPush] 가 **의수 전송에 성공했을 때만** 로컬에 저장한다. 그래서 저장된 config(= 토글을
 * 다시 열거나 앱을 다시 켤 때 화면이 보여 주는 값)는 항상 의수에 마지막으로 보낸 값이다.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel(), SettingsEditActions {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    /** SET 전송 1회 결과(성공=true). 화면 상단 배너로 소비하는 일회성 이벤트 — Mode 탭과 동일 패턴. */
    private val _pushResult = Channel<Boolean>(Channel.BUFFERED)
    val pushResult = _pushResult.receiveAsFlow()

    init {
        viewModelScope.launch { repo.config.collect { c -> _uiState.update { it.copy(config = c) } } }
        viewModelScope.launch { repo.activeDof.collect { d -> _uiState.update { it.copy(dof = d) } } }
    }

    override fun applyAndPush(settings: GlobalSettings) {
        viewModelScope.launch {
            val newConfig = _uiState.value.config.copy(settings = settings)
            _uiState.update { it.copy(pushing = true) }
            val result = repo.pushConfig(newConfig) // 이 config 의 튜닝값을 MSET(0xE7) 프레임으로 전송
            if (result.isSuccess) {
                // 의수에 들어간 값만 "마지막으로 보낸 값"으로 저장한다. 도중에 화면을 떠나도 저장은 끝까지 한다.
                withContext(NonCancellable) { repo.updateConfig(newConfig) }
            }
            _uiState.update { it.copy(pushing = false) }
            _pushResult.send(result.isSuccess)
        }
    }
}
