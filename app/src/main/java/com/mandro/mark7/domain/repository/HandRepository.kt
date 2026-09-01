package com.mandro.mark7.domain.repository

import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.BleDevice
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.model.ConfigPushState
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandStatus
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

    // ── 설정 (SET 프레임 + 로컬 저장) ─────────────────────────
    val config: StateFlow<HandConfig>
    suspend fun updateConfig(config: HandConfig)   // 로컬만 갱신
    suspend fun pushConfig(): Result<Unit>         // 현재 config 를 SET 프레임으로 전송, ACK 대기

    // ── 액션 매핑 (앱 로컬 전용) ─────────────────────────────
    val actionMapping: StateFlow<ActionMapping>
    suspend fun updateActionMapping(mapping: ActionMapping)
}
