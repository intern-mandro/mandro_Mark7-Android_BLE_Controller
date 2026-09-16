package com.mandro.mark7

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.connection.BleState
import com.mandro.mark7.domain.model.hand.HandDof
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {
    val bleState: StateFlow<BleState> = repo.bleState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleState.Idle)

    /**
     * 연결된 의수가 STATUS 로 알려 준 DOF 가 선택한 DOF 와 다르면 그 DOF, 아니면 null.
     * 연결 화면은 STATUS 를 기다리지 않고 바로 넘어오므로, 불일치는 메인 화면에서 알린다.
     * STATUS 가 아직 없으면(프로토콜 불일치로 안 오는 경우 포함) null.
     */
    val dofMismatch: StateFlow<HandDof?> = combine(repo.selectedDof, repo.connectedDof) { selected, connected ->
        connected?.takeIf { it != selected }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun disconnect() {
        viewModelScope.launch { repo.disconnect() }
    }

    fun disconnectAndRescan() {
        viewModelScope.launch {
            repo.disconnect()
            repo.startScan()
        }
    }
}
