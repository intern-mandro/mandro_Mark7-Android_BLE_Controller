package com.mandro.mark7.domain.repository

import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.BleDevice
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.model.ConfigPushState
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandDof
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.domain.model.ManualPreset
import com.mandro.mark7.domain.model.MotorCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 의수 하나와의 모든 상호작용 창구. ViewModel 은 이 인터페이스에만 의존한다.
 *
 * BLE 전송/수신은 [com.mandro.mark7.core.ble.BleManager] 가, 설정/매핑의 로컬
 * 저장은 [com.mandro.mark7.data.local] 의 store 들이 담당하고, 구현체
 * (`data/ble/HandRepositoryImpl`) 가 둘을 합친다.
 */
interface HandRepository {

    /** 현재 선택된 의수 자유도 버전 (5 DOF, 6 DOF, 7 DOF) */
    val activeDof: StateFlow<HandDof>

    // ── BLE 링크 ──────────────────────────────────────────────
    val bleState: Flow<BleState>
    val status: Flow<HandStatus>            // STATUS 프레임 (약 10~20Hz)
    val configPushState: Flow<ConfigPushState>

    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(device: BleDevice)
    suspend fun disconnect()

    // ── 직접 구동 (CMD 프레임) ────────────────────────────────
    suspend fun sendCommand(command: MotorCommand)

    // ── 의수의 모터 설정 값 (SET 프레임 + 로컬 저장) ─────────────────────────
    val config: StateFlow<HandConfig>
    suspend fun updateConfig(config: HandConfig)   // 로컬만 갱신
    /** [config] 를 SET 프레임으로 전송, ACK 대기. null 이면 현재 [HandRepository.config] 값을 보낸다. */
    suspend fun pushConfig(config: HandConfig? = null): Result<Unit>

    // ── 액션(제스처) 매핑 (앱 로컬 전용) ─────────────────────────────
    val actionMapping: StateFlow<ActionMapping>
    suspend fun updateActionMapping(mapping: ActionMapping)

    // ── program_mode (MODE1/MODE2) 전환 ─────────────────────
    /**
     * 의수 program_mode 를 [mode](1 또는 2)로 바꾼다. 펌웨어는 `close` 입력으로도
     * 자동 전환하며, 변경 결과는 STATUS([HandStatus.programMode])로 되돌아온다.
     */
    suspend fun setProgramMode(mode: Int): Result<Unit>

    // ── 수동 제어 동작 프리셋 (앱 로컬 전용) ───────────────────────────
    val manualPresets: StateFlow<List<ManualPreset>>
    suspend fun saveManualPresets(presets: List<ManualPreset>)
    suspend fun resetManualPresets()
}
