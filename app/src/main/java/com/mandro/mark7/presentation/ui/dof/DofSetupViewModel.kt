package com.mandro.mark7.presentation.ui.dof

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.data.local.HandVersionStore
import com.mandro.mark7.domain.model.HandDof
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DofSetupUiState(
    val selectedDof: HandDof = HandDof.DEFAULT,
    val busy: Boolean = false,
)

@HiltViewModel
class DofSetupViewModel @Inject constructor(
    private val handVersionStore: HandVersionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DofSetupUiState())
    val uiState = _uiState.asStateFlow()

    /** 버전 선택 완료 → 다음 화면(의수 스캔/연결)으로 이동. 일회성 이벤트. */
    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            handVersionStore.activeDof.collect { active ->
                _uiState.update { it.copy(selectedDof = active) }
            }
        }
    }

    /** 자유도 선택 변경 및 즉시 영속화 */
    fun selectDof(dof: HandDof) {
        _uiState.update { it.copy(selectedDof = dof) }
        viewModelScope.launch {
            handVersionStore.setActiveDof(dof)
        }
    }

    /** 선택한 의수 버전으로 확정하고 다음 단계로 진행 */
    fun confirmSelection() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            handVersionStore.setActiveDof(_uiState.value.selectedDof)
            _done.send(Unit)
            _uiState.update { it.copy(busy = false) }
        }
    }
}
