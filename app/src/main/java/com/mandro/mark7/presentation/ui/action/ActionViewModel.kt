package com.mandro.mark7.presentation.ui.action

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.HandAction
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActionUiState(
    val mapping: ActionMapping = ActionMapping(),
    val config: HandConfig = HandConfig.DEFAULT,
    val pushing: Boolean = false,
)

/**
 * 액션(굽히기/펴기/쥐기/휴식) 각각에 어떤 패턴(상태 S1..S8)을 연결할지,
 * 점진적 잡기를 켤지 관리한다. 매핑은 앱 로컬, 패턴 정의는 SET 프레임으로 의수에.
 */
@HiltViewModel
class ActionViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.actionMapping.collect { m -> _uiState.update { it.copy(mapping = m) } }
        }
        viewModelScope.launch {
            repo.config.collect { c -> _uiState.update { it.copy(config = c) } }
        }
    }

    fun assignPattern(action: HandAction, patternIndex: Int) = viewModelScope.launch {
        val m = _uiState.value.mapping
        repo.updateActionMapping(
            m.copy(patternIndexByAction = m.patternIndexByAction + (action to patternIndex)),
        )
    }

    fun setGradual(enabled: Boolean) = viewModelScope.launch {
        repo.updateActionMapping(_uiState.value.mapping.copy(gradualGraspEnabled = enabled))
    }

    fun pushConfig() = viewModelScope.launch {
        _uiState.update { it.copy(pushing = true) }
        repo.pushConfig()
        _uiState.update { it.copy(pushing = false) }
    }
}
