package com.mandro.mark7.presentation.ui.action

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.hand.HandConfig
import com.mandro.mark7.domain.model.action.ActionMapping
import com.mandro.mark7.domain.model.hand.HandStatus
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActionUiState(
    val mapping: ActionMapping = ActionMapping(),
    val syncedMapping: ActionMapping = ActionMapping(),
    val config: HandConfig = HandConfig.DEFAULT,
    val status: HandStatus? = null,
    val pushing: Boolean = false,
) {
    /**
     * 현재 상태 매핑이 마지막으로 전송된 상태와 다를 때 true.
     * Send 버튼은 이 값과 관계없이 재전송할 수 있다.
     */
    val hasUnsentChanges: Boolean
        get() = mapping.gestureIdByState != syncedMapping.gestureIdByState
}

/**
 * 모드 플로우 화면 상태. 상태(S1..S8)별 손 모양 매핑은 앱 로컬에 저장하고,
 * [pushConfig] 로 MSET 프레임에 실어 의수에 보낸다.
 */
@HiltViewModel
class ActionViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActionUiState())
    val uiState = _uiState.asStateFlow()

    /** SET 전송 1회 결과(성공=true) — 화면에서 토스트로 소비하는 일회성 이벤트. */
    private val _pushResult = Channel<Boolean>(Channel.BUFFERED)
    val pushResult = _pushResult.receiveAsFlow()

    init {
        viewModelScope.launch {
            repo.actionMapping.collect { m -> _uiState.update { it.copy(mapping = m) } }
        }
        viewModelScope.launch {
            repo.syncedActionMapping.collect { sm -> _uiState.update { it.copy(syncedMapping = sm) } }
        }
        viewModelScope.launch {
            repo.config.collect { c -> _uiState.update { it.copy(config = c) } }
        }
        viewModelScope.launch {
            repo.status.collect { s -> _uiState.update { it.copy(status = s) } }
        }
    }

    /** 기본 매핑을 저장하고, 저장소에 반영된 그 매핑을 MSET으로 전송한다. */
    fun resetToDefault() = viewModelScope.launch {
        _uiState.update { it.copy(pushing = true) }
        val m = _uiState.value.mapping
        val defaultMapping = m.copy(gestureIdByState = m.catalog.defaultStateGestures)
        repo.updateActionMapping(defaultMapping)
        repo.actionMapping.first { it == defaultMapping }
        pushAndReport()
    }

    fun clearAllGestures() = resetToDefault()

    fun pushConfig() = viewModelScope.launch {
        _uiState.update { it.copy(pushing = true) }
        pushAndReport()
    }

    private suspend fun pushAndReport() {
        val result = repo.pushConfig()
        _uiState.update { it.copy(pushing = false) }
        _pushResult.send(result.isSuccess)
    }
}
