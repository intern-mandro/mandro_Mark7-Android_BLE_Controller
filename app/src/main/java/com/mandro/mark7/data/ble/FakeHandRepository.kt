package com.mandro.mark7.data.ble

import com.mandro.mark7.data.local.HandConfigStore
import com.mandro.mark7.domain.model.hand.HandConfig
import com.mandro.mark7.domain.model.hand.ManualPreset
import com.mandro.mark7.domain.model.hand.CmdPresetCatalogs
import com.mandro.mark7.domain.model.connection.BleDevice
import com.mandro.mark7.domain.model.connection.BleState
import com.mandro.mark7.domain.model.connection.ConfigPushState
import com.mandro.mark7.domain.model.hand.MotorCommand
import com.mandro.mark7.domain.model.action.ActionMapping
import com.mandro.mark7.domain.model.hand.HandDof
import com.mandro.mark7.domain.model.hand.HandStatus
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * 실기기 없이 UI 를 돌리기 위한 가짜 구현. 개발용 mock 모드일 때
 * [SwitchableHandRepository] 가 모든 호출을 이쪽으로 보낸다 (BLE 는 전혀 쓰지 않는다).
 *
 * - 스캔하면 "Mark7 (mock)" 합성 기기 하나가 보이고, 연결하면 짧은 지연 뒤 [BleState.Connected].
 * - STATUS 는 약 20Hz 로 그럴듯한 값을 흘려보낸다.
 * - SET 전송은 짧은 지연 뒤 성공(Acked)으로 응답한다.
 * - 설정·매핑 저장은 실제 [HandConfigStore] 를 그대로 써서 모드 플로우 매핑이 유지된다.
 */
@Singleton
class FakeHandRepository @Inject constructor(
    private val configStore: HandConfigStore,
) : HandRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fakeDevice = MOCK_DEVICE

    private val _bleState = MutableStateFlow<BleState>(BleState.Idle)
    override val bleState: Flow<BleState> = _bleState.asStateFlow()

    override val selectedDof: StateFlow<HandDof> =
        configStore.activeDof.stateIn(scope, SharingStarted.Eagerly, HandDof.DEFAULT)

    /** 실기기처럼 연결된 동안에만 DOF 를 알린다. mock 은 늘 선택한 DOF 로 응답하므로 불일치가 없다. */
    override val connectedDof: StateFlow<HandDof?> =
        combine(_bleState, selectedDof) { state, dof -> dof.takeIf { state is BleState.Connected } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override val activeDof: StateFlow<HandDof> = selectedDof

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

    private val _syncedActionMapping = MutableStateFlow(ActionMapping())
    override val syncedActionMapping: StateFlow<ActionMapping> = _syncedActionMapping.asStateFlow()

    init {
        scope.launch {
            _syncedActionMapping.value = configStore.actionMapping.first()
        }
    }

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
        _syncedActionMapping.value = actionMapping.value
        return Result.success(Unit)
    }

    override suspend fun updateActionMapping(mapping: ActionMapping) = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        configStore.saveMapping(mapping)
    }

    override val manualPresets: StateFlow<List<ManualPreset>> =
        configStore.manualPresetsForDof(activeDof)
            .stateIn(scope, SharingStarted.Eagerly, CmdPresetCatalogs.forDof(HandDof.DEFAULT))

    override suspend fun saveManualPresets(presets: List<ManualPreset>) = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        configStore.saveManualPresets(presets, activeDof.value)
    }

    override suspend fun resetManualPresets() = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        configStore.resetManualPresets(activeDof.value)
    }

    /** 온도·회전량·EMG·전압이 완만히 흔들리는 표시용 샘플. */
    private fun sampleStatus(tick: Int): HandStatus {
        fun wobble(base: Int, amp: Int): Int = base + (sin(tick / 60.0 + base) * amp).toInt()

        fun wobbleEmg(base: Int, amp: Int, freq: Double, seed: Int): Int {
            val wave = base + sin(tick * freq) * amp + sin(tick * freq * 2.4 + seed) * (amp * 0.35) + ((tick + seed) % 7) * 9
            return wave.toInt().coerceAtLeast(0)
        }

        val dofCount = activeDof.value.dof
        return HandStatus(
            dof = dofCount,
            motorTemp = IntArray(dofCount) { wobble(37 + it * 2, 2) },
            motorTurn = IntArray(dofCount) { wobble(if (it == 0 || it == dofCount - 1) 10 else 300, 8) },
            emg = intArrayOf(
                wobbleEmg(512, 160, 0.16, 1),
                wobbleEmg(480, 190, 0.13, 3),
            ),
            voltage = 127,
            checksumOk = true,
        )
    }

    companion object {
        const val MOCK_ADDRESS = "00:00:00:00:00:00"
        val MOCK_DEVICE = BleDevice(name = "Mark7 (mock)", address = MOCK_ADDRESS, rssi = -48)

        const val STATUS_PERIOD_MS = 50L
        const val PUSH_DELAY_MS = 250L
    }
}
