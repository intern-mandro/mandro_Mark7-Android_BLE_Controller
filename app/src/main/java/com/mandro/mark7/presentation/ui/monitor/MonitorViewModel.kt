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
    /** 최근 STATUS 표본(오래된 것 → 최신). 시계열 그래프용. */
    val history: List<HandStatus> = emptyList(),
    /** 오실로스코프 스위프 커서 쓰기 포인터 (0 until DISPLAY_SAMPLES) */
    val writePtr: Int = 0,
)

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState = _uiState.asStateFlow()

    /** 2채널 링버퍼 (오실로스코프 스위프용) — UI Canvas가 직접 읽음 */
    val emgBuffers: Array<FloatArray> = Array(2) { FloatArray(DISPLAY_SAMPLES) }
    private var firstEmgSample = true
    private var currentWritePtr = 0

    init {
        viewModelScope.launch {
            repo.bleState.collect { s -> _uiState.update { it.copy(connected = s is BleState.Connected) } }
        }
        viewModelScope.launch {
            repo.status.collect { st ->
                val v0 = st.emg.getOrElse(0) { 0 }.toFloat()
                val v1 = st.emg.getOrElse(1) { 0 }.toFloat()

                if (firstEmgSample) {
                    emgBuffers[0].fill(v0)
                    emgBuffers[1].fill(v1)
                    firstEmgSample = false
                }

                val ptr = currentWritePtr
                emgBuffers[0][ptr] = v0
                emgBuffers[1][ptr] = v1
                val nextPtr = (ptr + 1) % DISPLAY_SAMPLES
                currentWritePtr = nextPtr

                _uiState.update {
                    it.copy(
                        status = st,
                        history = (it.history + st).takeLast(HISTORY_MAX),
                        writePtr = nextPtr,
                    )
                }
            }
        }
    }

    fun disconnect() = viewModelScope.launch { repo.disconnect() }

    companion object {
        const val DISPLAY_SAMPLES = 120
        private const val HISTORY_MAX = 120
    }
}
