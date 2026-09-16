package com.mandro.mark7.presentation.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.connection.BleDevice
import com.mandro.mark7.domain.model.connection.BleState
import com.mandro.mark7.domain.repository.HandRepository
import com.mandro.mark7.domain.repository.MockModeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 연결 화면 상태. BLE 연결만 되면 STATUS 를 기다리지 않고 메인 화면으로 넘어간다 —
 * STATUS 대기 표시와 DOF 불일치 안내는 메인 화면이 맡는다.
 */
data class ScanUiState(
    val bleState: BleState = BleState.Idle,
    val devices: List<BleDevice> = emptyList(),
    val connected: Boolean = false,
    /** 개발용 mock 데이터 소스 사용 중. BLE 권한·스캔 없이 합성 기기만 보인다. */
    val mockMode: Boolean = false,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repo: HandRepository,
    private val mockModeController: MockModeController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState(mockMode = mockModeController.isMockMode.value))
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
        viewModelScope.launch {
            mockModeController.isMockMode.collect { mock -> _uiState.update { it.copy(mockMode = mock) } }
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

    /**
     * 개발용 mock ↔ 실제 BLE 전환. 이전 소스에서 찾은 기기가 목록에 남아 엉뚱한 소스로
     * 연결되지 않도록 목록을 비우고 새 소스로 다시 탐색한다.
     */
    fun setMockMode(enabled: Boolean) {
        if (enabled == mockModeController.isMockMode.value) return
        viewModelScope.launch {
            mockModeController.setMockMode(enabled)
            _uiState.update { it.copy(devices = emptyList(), connected = false) }
            repo.startScan()
        }
    }

    fun connect(device: BleDevice) = viewModelScope.launch { repo.connect(device) }
}
