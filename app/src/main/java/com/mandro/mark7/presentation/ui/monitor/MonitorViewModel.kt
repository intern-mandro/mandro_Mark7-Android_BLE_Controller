package com.mandro.mark7.presentation.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MonitorUiState(
    val connected: Boolean = false,
    val status: HandStatus? = null,
)

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.bleState.collect { s -> _uiState.update { it.copy(connected = s is BleState.Connected) } }
        }
        viewModelScope.launch {
            repo.status.collect { st -> _uiState.update { it.copy(status = st) } }
        }
    }

    fun disconnect() = viewModelScope.launch { repo.disconnect() }
}
