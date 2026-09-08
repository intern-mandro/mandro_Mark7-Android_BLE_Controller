package com.mandro.mark7.data.ble

import com.mandro.mark7.data.local.HandConfigStore
import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.BleDevice
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.model.ConfigPushState
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.domain.model.ManualPreset
import com.mandro.mark7.domain.model.MotorCommand
import com.mandro.mark7.domain.repository.HandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * 실기기 없이 UI 를 돌리기 위한 가짜 구현. `BuildConfig.USE_MOCK_BLE` 가 true 일 때
 * [com.mandro.mark7.di.RepositoryModule] 이 이걸 바인딩한다.
 *
 * - 항상 연결된 것으로 보고([BleState.Connected]) 스플래시가 바로 메인 탭으로 간다.
 * - STATUS 는 약 2Hz 로 그럴듯한 값을 흘려보낸다.
 * - SET 전송은 짧은 지연 뒤 성공(Acked)으로 응답한다.
 * - 설정·매핑 저장은 실제 [HandConfigStore] 를 그대로 써서 모드 플로우 매핑이 유지된다.
 */
@Singleton
class FakeHandRepository @Inject constructor(
    private val configStore: HandConfigStore,
) : HandRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fakeDevice = MOCK_DEVICE

    private val _bleState = MutableStateFlow<BleState>(BleState.Connected(fakeDevice))
    override val bleState: Flow<BleState> = _bleState.asStateFlow()

    override val activeDof: StateFlow<com.mandro.mark7.domain.model.HandDof> =
        configStore.activeDof.stateIn(scope, SharingStarted.Eagerly, com.mandro.mark7.domain.model.HandDof.DEFAULT)

    /** null 이면 자동 전환(=close 신호 시늉)을 따르고, 값이 있으면 그 모드로 고정. */
    private val forcedMode = MutableStateFlow<Int?>(null)

    override val status: Flow<HandStatus> = flow {
        var tick = 0
        while (true) {
            emit(sampleStatus(tick++))
            delay(STATUS_PERIOD_MS)
        }
    }.flowOn(Dispatchers.Default)

    private val _configPushState = MutableStateFlow<ConfigPushState>(ConfigPushState.Idle)
    override val configPushState: Flow<ConfigPushState> = _configPushState.asStateFlow()

    override val config: StateFlow<HandConfig> =
        configStore.config.stateIn(scope, SharingStarted.Eagerly, HandConfig.DEFAULT)

    override val actionMapping: StateFlow<ActionMapping> =
        configStore.actionMapping.stateIn(scope, SharingStarted.Eagerly, ActionMapping())

    override suspend fun startScan() {
        _bleState.value = BleState.DevicesFound(listOf(fakeDevice))
    }

    override suspend fun stopScan() = Unit

    override suspend fun connect(device: BleDevice) {
        _bleState.value = BleState.Connecting(device)
        delay(200)
        _bleState.value = BleState.Connected(device)
    }

    override suspend fun disconnect() {
        _bleState.value = BleState.Disconnected
    }

    override suspend fun sendCommand(command: MotorCommand) = Unit

    override suspend fun updateConfig(config: HandConfig) = configStore.saveConfig(config)

    override suspend fun pushConfig(config: HandConfig?): Result<Unit> {
        _configPushState.value = ConfigPushState.Sending
        delay(PUSH_DELAY_MS)
        _configPushState.value = ConfigPushState.Acked
        return Result.success(Unit)
    }

    override suspend fun updateActionMapping(mapping: ActionMapping) = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        configStore.saveMapping(mapping)
    }

    override suspend fun setProgramMode(mode: Int): Result<Unit> {
        forcedMode.value = mode.coerceIn(1, 2)
        return Result.success(Unit)
    }

    override val manualPresets: StateFlow<List<ManualPreset>> =
        configStore.manualPresets.stateIn(scope, SharingStarted.Eagerly, ManualPreset.DEFAULT_PRESETS)

    override suspend fun saveManualPresets(presets: List<ManualPreset>) = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        configStore.saveManualPresets(presets)
    }

    override suspend fun resetManualPresets() = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        configStore.resetManualPresets()
    }

    /** 온도/전류/EMG 가 완만히 흔들리고, 현재 상태·모드가 정해진 경로를 따라 바뀌는 표시용 샘플. */
    private fun sampleStatus(tick: Int): HandStatus {
        fun wobble(base: Int, amp: Int): Int = base + (sin(tick / 60.0 + base) * amp).toInt()
        val state = STATE_PATH[(tick / TICKS_PER_STATE) % STATE_PATH.size]
        // close 신호로 자동 전환되는 시늉: 한 바퀴마다 MODE 를 번갈아. 수동 지정이 있으면 그쪽 고정.
        val autoMode = if ((tick / MODE_CYCLE_TICKS) % 2 == 0) 1 else 2

        // 실시간 근전도 생체 신호(EMG) 파형: 주파수 합성 + 근육 수축 시뮬레이션
        fun wobbleEmg(base: Int, amp: Int, freq: Double, seed: Int): Int {
            val wave = base + sin(tick * freq) * amp + sin(tick * freq * 2.4 + seed) * (amp * 0.35) + ((tick + seed) % 7) * 9
            return wave.toInt().coerceAtLeast(0)
        }

        return HandStatus(
            motorTemp = IntArray(6) { wobble(37 + it * 2, 2) },
            motorCurrentAvg = IntArray(6) { wobble(300 + it * 60, 40).coerceAtLeast(0) },
            motorTurn = IntArray(6) { wobble(if (it == 0 || it == 5) 10 else 300, 8) },
            emg = intArrayOf(
                wobbleEmg(512, 160, 0.16, 1),
                wobbleEmg(480, 190, 0.13, 3),
            ),
            currentState = state,
            programMode = forcedMode.value ?: autoMode,
            checksumOk = true,
        )
    }

    companion object {
        /** mock 전용 합성 기기. 실제 스캔 결과와 구분되는 고정 주소를 가진다. */
        const val MOCK_ADDRESS = "00:00:00:00:00:00"
        val MOCK_DEVICE = BleDevice(name = "Mark7 (mock)", address = MOCK_ADDRESS, rssi = -48)

        const val STATUS_PERIOD_MS = 50L // 20Hz (실기기 Mark7 BLE STATUS 주기 약 10~20Hz와 일치)
        /** 현재 상태가 이 경로를 따라 한 칸씩 오간다: 대기→굽힘체인→대기→폄체인. */
        val STATE_PATH = listOf(1, 2, 3, 2, 1, 4, 5, 4)
        const val TICKS_PER_STATE = 60 // 60 * 50ms = 상태당 약 3초
        const val MODE_CYCLE_TICKS = 480 // 480 * 50ms = 약 24초마다 MODE 자동 전환
        const val PUSH_DELAY_MS = 400L
    }
}
