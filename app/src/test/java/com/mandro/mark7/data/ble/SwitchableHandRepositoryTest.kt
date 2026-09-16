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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableHandRepositoryTest {

    /** 호출된 BLE 동작을 순서대로 기록하는 가짜 소스. */
    private class RecordingRepository(connected: HandDof) : HandRepository {
        val calls = mutableListOf<String>()

        override val selectedDof = MutableStateFlow(HandDof.DOF_6)
        override val connectedDof = MutableStateFlow<HandDof?>(connected)
        override val activeDof: StateFlow<HandDof> = selectedDof
        override val bleState = MutableStateFlow<BleState>(BleState.Idle)
        override val status: Flow<HandStatus> = emptyFlow()
        override val configPushState: Flow<ConfigPushState> = MutableStateFlow(ConfigPushState.Idle)
        override val config: StateFlow<HandConfig> = MutableStateFlow(HandConfig())
        override val actionMapping: StateFlow<ActionMapping> = MutableStateFlow(ActionMapping())
        override val syncedActionMapping: StateFlow<ActionMapping> = actionMapping
        override val manualPresets: StateFlow<List<ManualPreset>> = MutableStateFlow(emptyList())

        override suspend fun startScan() { calls += "startScan" }
        override suspend fun stopScan() { calls += "stopScan" }
        override suspend fun connect(device: BleDevice) { calls += "connect" }
        override suspend fun disconnect() { calls += "disconnect" }
        override suspend fun sendCommand(command: MotorCommand) { calls += "sendCommand" }
        override suspend fun pushConfig(config: HandConfig?): Result<Unit> {
            calls += "pushConfig"
            return Result.success(Unit)
        }
        override suspend fun updateConfig(config: HandConfig) = Unit
        override suspend fun updateActionMapping(mapping: ActionMapping) = Unit
        override suspend fun saveManualPresets(presets: List<ManualPreset>) = Unit
        override suspend fun resetManualPresets() = Unit
    }

    private val device = BleDevice(name = "Mark7", address = "AA:BB:CC:DD:EE:FF", rssi = -50)
    private val mock = RecordingRepository(connected = HandDof.DOF_5)
    private val real = RecordingRepository(connected = HandDof.DOF_7)
    private var realCreated = 0

    private fun TestScope.switchable(initialMockMode: Boolean) = SwitchableHandRepository(
        mock = mock,
        realFactory = { realCreated++; real },
        initialMockMode = initialMockMode,
        scope = backgroundScope,
    )

    @Test
    fun `mock mode routes BLE calls to the mock and never creates the real BLE repository`() = runTest {
        val repo = switchable(initialMockMode = true)

        repo.startScan()
        repo.connect(device)
        repo.pushConfig()
        testScheduler.runCurrent()

        assertEquals(listOf("startScan", "connect", "pushConfig"), mock.calls)
        assertEquals(0, realCreated)
        assertEquals(HandDof.DOF_5, repo.connectedDof.value)
        assertEquals(HandDof.DOF_5, repo.activeDof.value)
    }

    @Test
    fun `switching to real tears down the mock and follows the real BLE state`() = runTest {
        mock.bleState.value = BleState.DevicesFound(listOf(device))
        real.bleState.value = BleState.Scanning
        val repo = switchable(initialMockMode = true)
        assertSame(mock.bleState.value, repo.bleState.first())

        repo.setMockMode(false)
        repo.startScan()
        testScheduler.runCurrent()

        assertFalse(repo.isMockMode.value)
        assertEquals(listOf("stopScan", "disconnect"), mock.calls)
        assertEquals(listOf("startScan"), real.calls)
        assertEquals(1, realCreated)
        assertSame(real.bleState.value, repo.bleState.first())
        assertEquals(HandDof.DOF_7, repo.connectedDof.value)
    }

    @Test
    fun `switching back to mock stops the real scan and connection`() = runTest {
        val repo = switchable(initialMockMode = false)
        repo.startScan()

        repo.setMockMode(true)

        assertTrue(repo.isMockMode.value)
        assertEquals(listOf("startScan", "stopScan", "disconnect"), real.calls)
        assertTrue(mock.calls.isEmpty())
    }

    @Test
    fun `setting the current mode again does nothing`() = runTest {
        val repo = switchable(initialMockMode = true)

        repo.setMockMode(true)

        assertTrue(mock.calls.isEmpty())
        assertEquals(0, realCreated)
    }
}
