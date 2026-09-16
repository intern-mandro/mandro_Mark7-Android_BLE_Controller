package com.mandro.mark7.presentation.ui.scan

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeHandRepository : HandRepository {
        var scanCount = 0

        override val selectedDof = MutableStateFlow(HandDof.DOF_7)
        override val connectedDof = MutableStateFlow<HandDof?>(null)
        override val activeDof: StateFlow<HandDof> = selectedDof
        override val bleState = MutableStateFlow<BleState>(BleState.Idle)
        override val status: Flow<HandStatus> = kotlinx.coroutines.flow.emptyFlow()
        override val configPushState: Flow<ConfigPushState> = MutableStateFlow(ConfigPushState.Idle)
        override val config: StateFlow<HandConfig> = MutableStateFlow(HandConfig())
        override val actionMapping: StateFlow<ActionMapping> = MutableStateFlow(ActionMapping())
        override val syncedActionMapping: StateFlow<ActionMapping> = actionMapping
        override val manualPresets: StateFlow<List<ManualPreset>> = MutableStateFlow(emptyList())

        override suspend fun startScan() { scanCount++ }
        override suspend fun stopScan() = Unit
        override suspend fun connect(device: BleDevice) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun sendCommand(command: MotorCommand) = Unit
        override suspend fun updateConfig(config: HandConfig) = Unit
        override suspend fun pushConfig(config: HandConfig?): Result<Unit> = Result.success(Unit)
        override suspend fun updateActionMapping(mapping: ActionMapping) = Unit
        override suspend fun setProgramMode(mode: Int): Result<Unit> = Result.success(Unit)
        override suspend fun saveManualPresets(presets: List<ManualPreset>) = Unit
        override suspend fun resetManualPresets() = Unit
    }

    private class FakeMockModeController(initial: Boolean = false) : MockModeController {
        override val isMockMode = MutableStateFlow(initial)
        override suspend fun setMockMode(enabled: Boolean) { isMockMode.value = enabled }
    }

    private val device = BleDevice(name = "Mark7", address = "AA:BB:CC:DD:EE:FF", rssi = -50)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connected follows the BLE state so the screen can move on right away`() = runTest(dispatcher) {
        val repo = FakeHandRepository()
        val viewModel = ScanViewModel(repo, FakeMockModeController())
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.connected)

        // STATUS(DOF) 가 아직 없어도 BLE 연결만 되면 넘어갈 수 있다.
        repo.bleState.value = BleState.Connected(device)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.connected)
    }

    @Test
    fun `ui starts with the current mock mode`() = runTest(dispatcher) {
        val viewModel = ScanViewModel(FakeHandRepository(), FakeMockModeController(initial = true))

        assertTrue(viewModel.uiState.value.mockMode)
    }

    @Test
    fun `switching mock mode drops devices from the old source and rescans`() = runTest(dispatcher) {
        val repo = FakeHandRepository()
        val controller = FakeMockModeController(initial = false)
        val viewModel = ScanViewModel(repo, controller)
        repo.bleState.value = BleState.DevicesFound(listOf(device))
        testScheduler.runCurrent()
        assertEquals(listOf(device), viewModel.uiState.value.devices)
        val scansBefore = repo.scanCount

        viewModel.setMockMode(true)
        testScheduler.runCurrent()

        assertTrue(controller.isMockMode.value)
        assertTrue(viewModel.uiState.value.mockMode)
        assertTrue(viewModel.uiState.value.devices.isEmpty())
        assertEquals(scansBefore + 1, repo.scanCount)
    }
}
