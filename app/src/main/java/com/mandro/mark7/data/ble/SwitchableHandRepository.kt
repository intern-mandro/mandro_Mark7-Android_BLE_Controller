package com.mandro.mark7.data.ble

import com.mandro.mark7.domain.model.hand.HandConfig
import com.mandro.mark7.domain.model.hand.ManualPreset
import com.mandro.mark7.domain.model.connection.BleDevice
import com.mandro.mark7.domain.model.connection.BleState
import com.mandro.mark7.domain.model.connection.ConfigPushState
import com.mandro.mark7.domain.model.hand.MotorCommand
import com.mandro.mark7.domain.model.action.ActionMapping
import com.mandro.mark7.domain.model.hand.HandDof
import com.mandro.mark7.domain.model.hand.HandStatus
import com.mandro.mark7.domain.repository.HandRepository
import com.mandro.mark7.domain.repository.MockModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 앱이 실제로 바인딩하는 [HandRepository]. BLE ↔ mock 데이터 소스를 **실행 중에** 바꾼다
 * ([MockModeController]). 바인딩은 [com.mandro.mark7.di.RepositoryModule].
 *
 * - mock 모드: 모든 호출을 [mock] 으로 보낸다. BLE 는 전혀 쓰지 않는다.
 * - 실제 모드: [realFactory] 가 만든 BLE 구현으로 보낸다. 처음 필요할 때 한 번만 만들므로
 *   mock 으로 시작해 계속 mock 이면 `BleManager` 는 생성조차 되지 않는다.
 *
 * 설정·매핑·프리셋은 두 구현이 같은 `HandConfigStore` 를 쓰므로, BLE 와 무관한 [mock] 쪽에 고정 위임한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableHandRepository(
    private val mock: HandRepository,
    realFactory: () -> HandRepository,
    initialMockMode: Boolean,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : HandRepository, MockModeController {

    private val real: HandRepository by lazy(realFactory)

    private val _isMockMode = MutableStateFlow(initialMockMode)
    override val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private val switchLock = Mutex()

    private val active: HandRepository get() = if (_isMockMode.value) mock else real

    /** 활성 소스의 흐름을 따르다가, 스위치가 바뀌면 새 소스의 흐름으로 갈아탄다. */
    private fun <T> routed(select: (HandRepository) -> Flow<T>): Flow<T> =
        _isMockMode.flatMapLatest { useMock -> select(if (useMock) mock else real) }

    override suspend fun setMockMode(enabled: Boolean) {
        switchLock.withLock {
            if (_isMockMode.value == enabled) return
            // 이전 소스를 먼저 정리한다 — 실제 BLE 스캔/GATT 연결이 뒤에 남지 않도록.
            active.stopScan()
            active.disconnect()
            _isMockMode.value = enabled
        }
    }

    // ── BLE 링크 · 직접 구동 · SET 전송 : 활성 소스로 라우팅 ──────────
    override val bleState: Flow<BleState> = routed { it.bleState }
    override val status: Flow<HandStatus> = routed { it.status }
    override val configPushState: Flow<ConfigPushState> = routed { it.configPushState }

    override suspend fun startScan() = active.startScan()
    override suspend fun stopScan() = active.stopScan()
    override suspend fun connect(device: BleDevice) = active.connect(device)
    override suspend fun disconnect() = active.disconnect()
    override suspend fun sendCommand(command: MotorCommand) = active.sendCommand(command)
    override suspend fun pushConfig(config: HandConfig?): Result<Unit> = active.pushConfig(config)
    override suspend fun setProgramMode(mode: Int): Result<Unit> = active.setProgramMode(mode)

    // ── 자유도 ────────────────────────────────────────────────
    override val selectedDof: StateFlow<HandDof> = mock.selectedDof
    override val connectedDof: StateFlow<HandDof?> =
        routed { it.connectedDof }.stateIn(scope, SharingStarted.Eagerly, null)
    override val activeDof: StateFlow<HandDof> =
        combine(selectedDof, connectedDof) { selected, connected -> connected ?: selected }
            .stateIn(scope, SharingStarted.Eagerly, selectedDof.value)

    // ── 로컬 저장소 기반 : 두 구현이 같은 store 를 쓰므로 mock 쪽으로 고정 위임 ──────
    override val config: StateFlow<HandConfig> = mock.config
    override val actionMapping: StateFlow<ActionMapping> = mock.actionMapping
    override val syncedActionMapping: StateFlow<ActionMapping> =
        routed { it.syncedActionMapping }.stateIn(scope, SharingStarted.Eagerly, ActionMapping())
    override val manualPresets: StateFlow<List<ManualPreset>> = mock.manualPresets

    override suspend fun updateConfig(config: HandConfig) = mock.updateConfig(config)
    override suspend fun updateActionMapping(mapping: ActionMapping) = mock.updateActionMapping(mapping)
    override suspend fun saveManualPresets(presets: List<ManualPreset>) = mock.saveManualPresets(presets)
    override suspend fun resetManualPresets() = mock.resetManualPresets()
}
