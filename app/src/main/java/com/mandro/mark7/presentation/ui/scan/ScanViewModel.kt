package com.mandro.mark7.presentation.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.BleDevice
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val bleState: BleState = BleState.Idle,
    val devices: List<BleDevice> = emptyList(),
    val connected: Boolean = false,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.bleState.collect { state ->
                _uiState.update {
                    it.copy(
                        bleState = state,
                        devices = (state as? BleState.DevicesFound)?.devices ?: it.devices,
                        connected = state is BleState.Connected,
                    )
                }
            }
        }
        rescan()
    }

    fun rescan() = viewModelScope.launch { repo.startScan() }
    fun disconnectAndRescan() {
        _uiState.update {
            it.copy(
                bleState = BleState.Scanning,
                connected = false,
            )
        }
        viewModelScope.launch {
            repo.disconnect()
            repo.startScan()
        }
    }
    fun connect(device: BleDevice) = viewModelScope.launch { repo.connect(device) }
    fun stopScan() = viewModelScope.launch { repo.stopScan() }
}
