package com.mandro.mark7.data.ble

import com.mandro.mark7.core.ble.BleManager
import com.mandro.mark7.core.ble.MarkSevenProtocol
import com.mandro.mark7.data.local.HandConfigStore
import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.BleDevice
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.model.ConfigPushState
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.domain.model.MotorCommand
import com.mandro.mark7.domain.repository.HandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandRepositoryImpl @Inject constructor(
    private val bleManager: BleManager,
    private val configStore: HandConfigStore,
) : HandRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val bleState: Flow<BleState> = bleManager.state
    override val status: Flow<HandStatus> = bleManager.status

    private val _configPushState = MutableStateFlow<ConfigPushState>(ConfigPushState.Idle)
    override val configPushState: Flow<ConfigPushState> = _configPushState.asStateFlow()

    override val config: StateFlow<HandConfig> =
        configStore.config.stateIn(scope, SharingStarted.Eagerly, HandConfig.DEFAULT)

    override val actionMapping: StateFlow<ActionMapping> =
        configStore.actionMapping.stateIn(scope, SharingStarted.Eagerly, ActionMapping())

    override suspend fun startScan() = bleManager.startScan()
    override suspend fun stopScan() = bleManager.stopScan()
    override suspend fun connect(device: BleDevice) = bleManager.connect(device)
    override suspend fun disconnect() = bleManager.disconnect()

    override suspend fun sendCommand(command: MotorCommand) = withContext(Dispatchers.IO) {
        val frame = MarkSevenProtocol.buildCmd(
            select = command.select,
            speedRaw = command.speedRaw,
            currentMa = command.currentMa,
            posDeg = command.posDeg,
            dir = command.dir,
        )
        bleManager.writeFrame(frame)
        Unit
    }

    override suspend fun updateConfig(config: HandConfig) = configStore.saveConfig(config)

    override suspend fun pushConfig(): Result<Unit> = withContext(Dispatchers.IO) {
        _configPushState.value = ConfigPushState.Sending
        val ack = bleManager.awaitAck()
        val ok = bleManager.writeFrame(MarkSevenProtocol.buildSet(config.value))
        if (!ok) {
            _configPushState.value = ConfigPushState.Error("전송 실패 — 연결 상태를 확인하세요.")
            return@withContext Result.failure(IllegalStateException("write failed"))
        }
        val acked = withTimeoutOrNull(ACK_TIMEOUT_MS) { ack.await(); true } ?: false
        if (acked) {
            _configPushState.value = ConfigPushState.Acked
            Result.success(Unit)
        } else {
            _configPushState.value = ConfigPushState.Error("의수 응답(SETok)이 없습니다.")
            Result.failure(IllegalStateException("no ack"))
        }
    }

    override suspend fun updateActionMapping(mapping: ActionMapping) = configStore.saveMapping(mapping)

    private companion object {
        const val ACK_TIMEOUT_MS = 3_000L
    }
}
