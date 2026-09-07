package com.mandro.mark7.presentation.ui.manual

import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.BleDevice
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.model.CmdDir
import com.mandro.mark7.domain.model.ConfigPushState
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.domain.model.ManualPreset
import com.mandro.mark7.domain.model.MotorCommand
import com.mandro.mark7.domain.repository.HandRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManualViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeHandRepository : HandRepository {
        val sentCommands = mutableListOf<MotorCommand>()
        override val bleState: Flow<BleState> = kotlinx.coroutines.flow.emptyFlow()
        override val status: Flow<HandStatus> = kotlinx.coroutines.flow.emptyFlow()
        override val configPushState: Flow<ConfigPushState> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun startScan() {}
        override suspend fun stopScan() {}
        override suspend fun connect(device: BleDevice) {}
        override suspend fun disconnect() {}
        override suspend fun sendCommand(command: MotorCommand) {
            sentCommands.add(command)
        }
        override val config: StateFlow<HandConfig> = MutableStateFlow(HandConfig())
        override suspend fun updateConfig(config: HandConfig) {}
        override suspend fun pushConfig(config: HandConfig?): Result<Unit> = Result.success(Unit)
        override val actionMapping: StateFlow<ActionMapping> = MutableStateFlow(ActionMapping())
        override suspend fun updateActionMapping(mapping: ActionMapping) {}
        override suspend fun setProgramMode(mode: Int): Result<Unit> = Result.success(Unit)
        override val manualPresets: StateFlow<List<ManualPreset>> = MutableStateFlow(emptyList())
        override suspend fun saveManualPresets(presets: List<ManualPreset>) {}
        override suspend fun resetManualPresets() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `send Grasp immediately sets lastCommandedDir and armedDir to GRASP`() = runTest(testDispatcher) {
        val repo = FakeHandRepository()
        val viewModel = ManualViewModel(repo)

        // Select fingers manually
        viewModel.toggleFinger(0)
        viewModel.toggleFinger(1)
        assertNull(viewModel.uiState.value.armedDir)
        assertNull(viewModel.uiState.value.lastCommandedDir)

        // Send Grasp
        viewModel.send(CmdDir.GRASP)

        // Verify that armedDir and lastCommandedDir are immediately GRASP
        assertEquals(CmdDir.GRASP, viewModel.uiState.value.lastCommandedDir)
        assertEquals(CmdDir.GRASP, viewModel.uiState.value.armedDir)

        testScheduler.runCurrent()
        assertEquals(1, repo.sentCommands.size)
        assertEquals(CmdDir.GRASP, repo.sentCommands[0].dir)

        // Advance past pulse
        advanceTimeBy(ManualViewModel.PULSE_MS + 50)

        // Verify everything resets
        assertNull(viewModel.uiState.value.lastCommandedDir)
        assertNull(viewModel.uiState.value.armedDir)
        assertTrue(viewModel.uiState.value.select.none { it })
    }

    @Test
    fun `send Release immediately sets lastCommandedDir and armedDir to RELEASE`() = runTest(testDispatcher) {
        val repo = FakeHandRepository()
        val viewModel = ManualViewModel(repo)

        // Select fingers manually
        viewModel.setAllFingers(true)
        assertNull(viewModel.uiState.value.armedDir)
        assertNull(viewModel.uiState.value.lastCommandedDir)

        // Send Release
        viewModel.send(CmdDir.RELEASE)

        // Verify that armedDir and lastCommandedDir are immediately RELEASE
        assertEquals(CmdDir.RELEASE, viewModel.uiState.value.lastCommandedDir)
        assertEquals(CmdDir.RELEASE, viewModel.uiState.value.armedDir)

        testScheduler.runCurrent()
        assertEquals(1, repo.sentCommands.size)
        assertEquals(CmdDir.RELEASE, repo.sentCommands[0].dir)

        // Advance past pulse
        advanceTimeBy(ManualViewModel.PULSE_MS + 50)

        assertNull(viewModel.uiState.value.lastCommandedDir)
        assertNull(viewModel.uiState.value.armedDir)
        assertTrue(viewModel.uiState.value.select.none { it })
    }

    @Test
    fun `executePreset arms preset on first tap and deselects completely on second tap`() = runTest(testDispatcher) {
        val repo = FakeHandRepository()
        val viewModel = ManualViewModel(repo)

        val preset = ManualPreset(
            id = "preset_grasp",
            name = "Grasp",
            emoji = "✊",
            fingers = listOf(true, true, true, true, true, true),
            direction = CmdDir.GRASP,
            isDefault = true,
        )

        // First tap: should arm preset
        val armed = viewModel.executePreset(preset)
        assertTrue(armed)
        assertEquals("preset_grasp", viewModel.uiState.value.lastExecutedPresetId)
        assertEquals(CmdDir.GRASP, viewModel.uiState.value.armedDir)
        assertEquals(listOf(true, true, true, true, true, true), viewModel.uiState.value.select)

        // Second tap: should toggle off (deselect) completely
        val disarmed = viewModel.executePreset(preset)
        assertFalse(disarmed)
        assertNull(viewModel.uiState.value.lastExecutedPresetId)
        assertNull(viewModel.uiState.value.armedDir)
        assertTrue(viewModel.uiState.value.select.none { it })
    }

    @Test
    fun `toggleFinger and setAllFingers clear armed preset state`() = runTest(testDispatcher) {
        val repo = FakeHandRepository()
        val viewModel = ManualViewModel(repo)

        val preset = ManualPreset(
            id = "preset_grasp",
            name = "Grasp",
            emoji = "✊",
            fingers = listOf(true, true, true, true, true, true),
            direction = CmdDir.GRASP,
            isDefault = true,
        )

        viewModel.executePreset(preset)
        assertEquals("preset_grasp", viewModel.uiState.value.lastExecutedPresetId)

        // Toggling a finger clears preset selection and armed direction
        viewModel.toggleFinger(0)
        assertNull(viewModel.uiState.value.lastExecutedPresetId)
        assertNull(viewModel.uiState.value.armedDir)

        // Re-arm and test setAllFingers clears preset selection
        viewModel.executePreset(preset)
        assertEquals("preset_grasp", viewModel.uiState.value.lastExecutedPresetId)

        viewModel.setAllFingers(false)
        assertNull(viewModel.uiState.value.lastExecutedPresetId)
        assertNull(viewModel.uiState.value.armedDir)
    }

    @Test
    fun `switching to another preset cleanly replaces armed preset`() = runTest(testDispatcher) {
        val repo = FakeHandRepository()
        val viewModel = ManualViewModel(repo)

        val preset1 = ManualPreset(
            id = "preset_1",
            name = "P1",
            emoji = "✊",
            fingers = listOf(true, true, true, true, true, true),
            direction = CmdDir.GRASP,
            isDefault = true,
        )
        val preset2 = ManualPreset(
            id = "preset_2",
            name = "P2",
            emoji = "🖐",
            fingers = listOf(false, false, false, false, false, true),
            direction = CmdDir.RELEASE,
            isDefault = true,
        )

        viewModel.executePreset(preset1)
        assertEquals("preset_1", viewModel.uiState.value.lastExecutedPresetId)
        assertEquals(CmdDir.GRASP, viewModel.uiState.value.armedDir)

        viewModel.executePreset(preset2)
        assertEquals("preset_2", viewModel.uiState.value.lastExecutedPresetId)
        assertEquals(CmdDir.RELEASE, viewModel.uiState.value.armedDir)
        assertEquals(listOf(false, false, false, false, false, true), viewModel.uiState.value.select)
    }

    @Test
    fun `deletePreset clears selection if deleted preset is currently armed`() = runTest(testDispatcher) {
        val repo = FakeHandRepository()
        val viewModel = ManualViewModel(repo)

        val preset = ManualPreset(
            id = "preset_to_delete",
            name = "Delete Me",
            emoji = "🗑️",
            fingers = listOf(true, true, false, false, false, false),
            direction = CmdDir.GRASP,
            isDefault = false,
        )

        viewModel.executePreset(preset)
        assertEquals("preset_to_delete", viewModel.uiState.value.lastExecutedPresetId)

        viewModel.deletePreset("preset_to_delete")
        testScheduler.runCurrent()

        assertNull(viewModel.uiState.value.lastExecutedPresetId)
        assertNull(viewModel.uiState.value.armedDir)
        assertTrue(viewModel.uiState.value.select.none { it })
    }
}
