package com.mandro.mark7.data.ble

import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.BleDevice
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.model.ConfigPushState
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandDof
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.domain.model.ManualPreset
import com.mandro.mark7.domain.model.MotorCommand
import android.util.Log
import com.mandro.mark7.domain.repository.HandRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * mock 빌드(`BuildConfig.USE_MOCK_BLE`)에서 [com.mandro.mark7.di.RepositoryModule] 이 바인딩한다.
 *
 * - "Mark7 (mock)" 합성 기기는 [FakeHandRepository] 가 그대로 담당한다 (동작 무변경).
 * - **동시에** 실제 BLE 스캔([HandRepositoryImpl] = [com.mandro.mark7.core.ble.BleManager])을
 *   돌려, `BleManager` 필터(`CHIPSEN…`/`mark…` 이름 또는 `0xFFF0`/`0xFFE0` 서비스)를
 *   통과한 실기기를 mock 아래에 함께 노출한다.
 * - 스캔 목록에서 mock 을 고르면 mock 경로로, 실기기를 고르면 실제 BLE 경로로 라우팅한다.
 *
 * 실기기 연결 이후의 CMD/STATUS/SET 은 전부 [real] 위임이라 별도 구현이 없다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HybridHandRepository @Inject constructor(
    private val real: HandRepositoryImpl,
    private val fake: FakeHandRepository,
) : HandRepository {

    private companion object {
        const val TAG = "Mark7Hybrid"
    }

    private enum class Route { SCAN, MOCK, REAL }

    /** 스캔 중(SCAN) / mock 선택(MOCK) / 실기기 선택(REAL). 시작은 항상 SCAN. */
    private val route = MutableStateFlow(Route.SCAN)

    private val active: HandRepository get() = if (route.value == Route.REAL) real else fake

    // ── BLE 링크 ──────────────────────────────────────────────
    /**
     * SCAN 에서는 `[mock] + 실제 스캔 결과` 를 합쳐서 노출한다.
     * MOCK 선택 후에는 [fake] 상태를, REAL 선택 후에는 [real] 상태를 그대로 통과시킨다.
     */
    override val bleState: Flow<BleState> =
        combine(route, fake.bleState, real.bleState) { r, fakeState, realState ->
            when (r) {
                Route.REAL -> realState
                Route.MOCK -> fakeState
                Route.SCAN -> {
                    Log.d(TAG, "scan tick — realState=$realState")
                    when (realState) {
                        // 실제 스캐너가 에러(권한 없음 / 블루투스 OFF / 스캔 실패)면 그대로 노출.
                        // 원인을 고치고 다시 스캔하면 아래 병합 목록으로 복구된다.
                        is BleState.Error -> realState
                        is BleState.DevicesFound ->
                            BleState.DevicesFound(listOf(FakeHandRepository.MOCK_DEVICE) + realState.devices)
                        // Idle / Scanning / (재)연결 상태 등 — 실제 기기 없음, mock 만.
                        else -> BleState.DevicesFound(listOf(FakeHandRepository.MOCK_DEVICE))
                    }
                }
            }
        }

    override val status: Flow<HandStatus> =
        route.flatMapLatest { if (it == Route.REAL) real.status else fake.status }

    override val configPushState: Flow<ConfigPushState> =
        route.flatMapLatest { if (it == Route.REAL) real.configPushState else fake.configPushState }

    override suspend fun startScan() {
        route.value = Route.SCAN
        Log.d(TAG, "startScan → delegating to real BleManager")
        real.startScan() // == BleManager.startScan()
    }

    override suspend fun stopScan() = real.stopScan()

    override suspend fun connect(device: BleDevice) {
        if (device.address == FakeHandRepository.MOCK_ADDRESS) {
            Log.d(TAG, "connect(mock)")
            route.value = Route.MOCK
            fake.connect(device)
        } else {
            Log.d(TAG, "connect(real ${device.address} ${device.name})")
            route.value = Route.REAL
            real.connect(device)
        }
    }

    override suspend fun disconnect() {
        real.disconnect()
        fake.disconnect()
        route.value = Route.SCAN
    }

    // ── 직접 구동 / 설정 : 활성 경로로 위임 ─────────────────────────
    override suspend fun sendCommand(command: MotorCommand) = active.sendCommand(command)

    override suspend fun pushConfig(config: HandConfig?): Result<Unit> = active.pushConfig(config)

    override suspend fun setProgramMode(mode: Int): Result<Unit> = active.setProgramMode(mode)

    // ── 로컬 저장소 기반 (fake/real 동일 store) : real 로 고정 위임 ──────
    override val activeDof: StateFlow<HandDof> = real.activeDof
    override val config: StateFlow<HandConfig> = real.config
    override val actionMapping: StateFlow<ActionMapping> = real.actionMapping
    override val manualPresets: StateFlow<List<ManualPreset>> = real.manualPresets

    override suspend fun updateConfig(config: HandConfig) = real.updateConfig(config)
    override suspend fun updateActionMapping(mapping: ActionMapping) = real.updateActionMapping(mapping)
    override suspend fun saveManualPresets(presets: List<ManualPreset>) = real.saveManualPresets(presets)
    override suspend fun resetManualPresets() = real.resetManualPresets()
}
