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
import com.mandro.mark7.domain.model.ManualPreset
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
import android.content.Context
import com.mandro.mark7.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
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

    override suspend fun pushConfig(config: HandConfig?): Result<Unit> = withContext(Dispatchers.IO) {
        val target = config ?: this@HandRepositoryImpl.config.value
        _configPushState.value = ConfigPushState.Sending
        val ack = bleManager.awaitAck()
        val ok = bleManager.writeFrame(MarkSevenProtocol.buildSet(target))
        if (!ok) {
            _configPushState.value = ConfigPushState.Error(context.getString(R.string.ble_err_tx_failed))
            return@withContext Result.failure(IllegalStateException("write failed"))
        }
        val acked = withTimeoutOrNull(ACK_TIMEOUT_MS) { ack.await(); true } ?: false
        if (acked) {
            _configPushState.value = ConfigPushState.Acked
            Result.success(Unit)
        } else {
            _configPushState.value = ConfigPushState.Error(context.getString(R.string.ble_err_no_ack))
            Result.failure(IllegalStateException("no ack"))
        }
    }

    override suspend fun updateActionMapping(mapping: ActionMapping) = withContext(kotlinx.coroutines.NonCancellable) {
        configStore.saveMapping(mapping)
    }

    override suspend fun setProgramMode(mode: Int): Result<Unit> =
        // TODO(protocol): 모드 전환 프레임이 아직 규약에 없다. PROTOCOL.md 확정 후 구현.
        Result.failure(UnsupportedOperationException(context.getString(R.string.ble_err_mode_unsupported)))

    override val manualPresets: StateFlow<List<ManualPreset>> =
        configStore.manualPresets.stateIn(scope, SharingStarted.Eagerly, ManualPreset.DEFAULT_PRESETS)

    override suspend fun saveManualPresets(presets: List<ManualPreset>) = withContext(kotlinx.coroutines.NonCancellable) {
        configStore.saveManualPresets(presets)
    }

    override suspend fun resetManualPresets() = withContext(kotlinx.coroutines.NonCancellable) {
        configStore.resetManualPresets()
    }

    private companion object {
        const val ACK_TIMEOUT_MS = 3_000L
    }
}
