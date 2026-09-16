package com.mandro.mark7

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** 연결 화면은 STATUS 를 기다리지 않고 바로 넘어오므로, DOF 불일치는 메인 화면에서 알린다. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeHandRepository : HandRepository {
        override val selectedDof = MutableStateFlow(HandDof.DOF_7)
        override val connectedDof = MutableStateFlow<HandDof?>(null)
        override val activeDof: StateFlow<HandDof> = selectedDof
        override val bleState = MutableStateFlow<BleState>(BleState.Idle)
        override val status: Flow<HandStatus> = emptyFlow()
        override val configPushState: Flow<ConfigPushState> = MutableStateFlow(ConfigPushState.Idle)
        override val config: StateFlow<HandConfig> = MutableStateFlow(HandConfig())
        override val actionMapping: StateFlow<ActionMapping> = MutableStateFlow(ActionMapping())
        override val syncedActionMapping: StateFlow<ActionMapping> = actionMapping
        override val manualPresets: StateFlow<List<ManualPreset>> = MutableStateFlow(emptyList())

        override suspend fun startScan() = Unit
        override suspend fun stopScan() = Unit
        override suspend fun connect(device: BleDevice) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun sendCommand(command: MotorCommand) = Unit
        override suspend fun updateConfig(config: HandConfig) = Unit
        override suspend fun pushConfig(config: HandConfig?): Result<Unit> = Result.success(Unit)
        override suspend fun updateActionMapping(mapping: ActionMapping) = Unit
        override suspend fun saveManualPresets(presets: List<ManualPreset>) = Unit
        override suspend fun resetManualPresets() = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no dof mismatch while no STATUS has reported a dof`() = runTest(dispatcher) {
        val viewModel = MainViewModel(FakeHandRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.dofMismatch.collect {} }
        testScheduler.runCurrent()

        assertNull(viewModel.dofMismatch.value)
    }

    @Test
    fun `reports the connected dof when STATUS disagrees with the selected dof`() = runTest(dispatcher) {
        val repo = FakeHandRepository()
        val viewModel = MainViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.dofMismatch.collect {} }

        repo.connectedDof.value = HandDof.DOF_6
        testScheduler.runCurrent()
        assertEquals(HandDof.DOF_6, viewModel.dofMismatch.value)

        repo.connectedDof.value = HandDof.DOF_7
        testScheduler.runCurrent()
        assertNull(viewModel.dofMismatch.value)
    }
}
