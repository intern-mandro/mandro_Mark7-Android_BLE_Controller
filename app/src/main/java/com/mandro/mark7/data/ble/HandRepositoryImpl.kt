package com.mandro.mark7.data.ble

import com.mandro.mark7.core.ble.BleManager
import com.mandro.mark7.core.ble.MarkSevenProtocol
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    override val selectedDof: StateFlow<HandDof> =
        configStore.activeDof.stateIn(scope, SharingStarted.Eagerly, HandDof.DEFAULT)

    override val connectedDof: StateFlow<HandDof?> = bleManager.detectedDof

    override val activeDof: StateFlow<HandDof> =
        combine(selectedDof, connectedDof) { selected, connected ->
            connected ?: selected
        }.stateIn(scope, SharingStarted.Eagerly, HandDof.DEFAULT)

    override val bleState: Flow<BleState> = bleManager.state
    override val status: Flow<HandStatus> = bleManager.status

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

    override suspend fun startScan() = bleManager.startScan()
    override suspend fun stopScan() = bleManager.stopScan()
    override suspend fun connect(device: BleDevice) = bleManager.connect(device)
    override suspend fun disconnect() = bleManager.disconnect()

    override suspend fun sendCommand(command: MotorCommand) = withContext(Dispatchers.IO) {
        val frame = MarkSevenProtocol.buildCmd(
            dof = activeDof.value.dof,
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
        val g = (config ?: this@HandRepositoryImpl.config.value).settings
        _configPushState.value = ConfigPushState.Sending
        val frame = MarkSevenProtocol.buildMset(
            dof = activeDof.value.dof,
            actionIds = actionMapping.value.toActionIds(),
            maxCurrentMa = g.maxCurrent.toIntArray(),
            motorSpeed = g.motorSpeed.toIntArray(),
            emgAmp = g.emgAmp.toIntArray(),
        )
        val ok = bleManager.writeFrame(frame)
        if (!ok) {
            _configPushState.value = ConfigPushState.Error(context.getString(R.string.ble_err_tx_failed))
            return@withContext Result.failure(IllegalStateException("write failed"))
        }
        _configPushState.value = ConfigPushState.Acked
        _syncedActionMapping.value = actionMapping.value
        Result.success(Unit)
    }

    override suspend fun updateActionMapping(mapping: ActionMapping) = withContext(kotlinx.coroutines.NonCancellable) {
        configStore.saveMapping(mapping)
    }

    override val manualPresets: StateFlow<List<ManualPreset>> =
        configStore.manualPresetsForDof(activeDof)
            .stateIn(scope, SharingStarted.Eagerly, CmdPresetCatalogs.forDof(HandDof.DEFAULT))

    override suspend fun saveManualPresets(presets: List<ManualPreset>) = withContext(kotlinx.coroutines.NonCancellable) {
        configStore.saveManualPresets(presets, activeDof.value)
    }

    override suspend fun resetManualPresets() = withContext(kotlinx.coroutines.NonCancellable) {
        configStore.resetManualPresets(activeDof.value)
    }
}
