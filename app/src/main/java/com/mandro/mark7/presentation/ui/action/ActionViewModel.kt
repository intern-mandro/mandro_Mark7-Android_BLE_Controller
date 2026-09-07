package com.mandro.mark7.presentation.ui.action

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.HandAction
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActionUiState(
    val mapping: ActionMapping = ActionMapping(),
    val config: HandConfig = HandConfig.DEFAULT,
    val status: HandStatus? = null,
    val pushing: Boolean = false,
    val modeChangeError: String? = null,
)

/**
 * 액션(굽히기/펴기/쥐기/휴식) 각각에 어떤 패턴(상태 S1..S8)을 연결할지,
 * 점진적 잡기를 켤지 관리한다. 매핑은 앱 로컬, 패턴 정의는 SET 프레임으로 의수에.
 */
@HiltViewModel
class ActionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
            repo.config.collect { c -> _uiState.update { it.copy(config = c) } }
        }
        viewModelScope.launch {
            repo.status.collect { s -> _uiState.update { it.copy(status = s) } }
        }
    }

    fun assignPattern(action: HandAction, patternIndex: Int) = viewModelScope.launch {
        val m = _uiState.value.mapping
        repo.updateActionMapping(
            m.copy(patternIndexByAction = m.patternIndexByAction + (action to patternIndex)),
        )
    }

    /** 상태 다이어그램 노드에서 손 모양 지정을 해제한다(빈 상태로). */
    fun clearGesture(stateId: Int) = viewModelScope.launch {
        val m = _uiState.value.mapping
        repo.updateActionMapping(m.copy(gestureIdByState = m.gestureIdByState - stateId))
    }

    /** 'All Clear' 버튼: 모든 상태의 손 모양 지정을 완전히 비운다(빈 상태). */
    fun clearAllGestures() = viewModelScope.launch {
        val m = _uiState.value.mapping
        repo.updateActionMapping(m.copy(gestureIdByState = emptyMap()))
    }

    /** program_mode 를 [mode](1/2)로 전환. 실패 시 [ActionUiState.modeChangeError] 에 사유. */
    fun setProgramMode(mode: Int) = viewModelScope.launch {
        _uiState.update { it.copy(modeChangeError = null) }
        repo.setProgramMode(mode).onFailure { e ->
            _uiState.update { it.copy(modeChangeError = e.message ?: context.getString(R.string.action_err_mode_failed)) }
        }
    }

    fun dismissModeError() = _uiState.update { it.copy(modeChangeError = null) }

    fun pushConfig() = viewModelScope.launch {
        _uiState.update { it.copy(pushing = true) }
        val result = repo.pushConfig()
        _uiState.update { it.copy(pushing = false) }
        _pushResult.send(result.isSuccess)
    }
}
