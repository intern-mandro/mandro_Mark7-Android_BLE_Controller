package com.mandro.mark7.presentation.ui.action

import com.mandro.mark7.domain.model.action.ActionMapping
import com.mandro.mark7.domain.model.connection.BleDevice
import com.mandro.mark7.domain.model.connection.BleState
import com.mandro.mark7.domain.model.connection.ConfigPushState
import com.mandro.mark7.domain.model.hand.HandConfig
import com.mandro.mark7.domain.model.hand.HandDof
import com.mandro.mark7.domain.model.hand.HandStatus
import com.mandro.mark7.domain.model.hand.ManualPreset
import com.mandro.mark7.domain.model.hand.MotorCommand
import com.mandro.mark7.domain.repository.HandRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeHandRepository(initial: ActionMapping) : HandRepository {
        var pushCount = 0
        var sentActionIds: List<Int>? = null
        override val selectedDof: StateFlow<HandDof> = MutableStateFlow(HandDof.DEFAULT)
        override val connectedDof: StateFlow<HandDof?> = MutableStateFlow(null)
        override val activeDof: StateFlow<HandDof> = selectedDof
        override val bleState: Flow<BleState> = emptyFlow()
        override val status: Flow<HandStatus> = emptyFlow()
        override val configPushState: Flow<ConfigPushState> = emptyFlow()
        override suspend fun startScan() {}
        override suspend fun stopScan() {}
        override suspend fun connect(device: BleDevice) {}
        override suspend fun disconnect() {}
        override suspend fun sendCommand(command: MotorCommand) {}
        override val config: StateFlow<HandConfig> = MutableStateFlow(HandConfig())
        override suspend fun updateConfig(config: HandConfig) {}

        override val actionMapping = MutableStateFlow(initial)
        override val syncedActionMapping = MutableStateFlow(initial)

        override suspend fun pushConfig(config: HandConfig?): Result<Unit> {
            pushCount++
            sentActionIds = actionMapping.value.toActionIds().toList()
            syncedActionMapping.value = actionMapping.value
            return Result.success(Unit)
        }

        override suspend fun updateActionMapping(mapping: ActionMapping) {
            actionMapping.value = mapping
        }
        override val manualPresets: StateFlow<List<ManualPreset>> = MutableStateFlow(emptyList())
        override suspend fun saveManualPresets(presets: List<ManualPreset>) {}
        override suspend fun resetManualPresets() {}
    }

    private val defaultMapping = ActionMapping()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unsent changes are false initially when mapping matches synced state`() = runTest(testDispatcher) {
        val repo = FakeHandRepository(defaultMapping)
        val viewModel = ActionViewModel(repo)
        advanceUntilIdle()

        // 초기에는 전송할 변경사항이 없으므로 hasUnsentChanges == false
        assertFalse(viewModel.uiState.value.hasUnsentChanges)
    }

    @Test
    fun `unsent changes become true when mapping changes without push`() = runTest(testDispatcher) {
        val repo = FakeHandRepository(defaultMapping)
        val viewModel = ActionViewModel(repo)
        advanceUntilIdle()

        // 손 모양 변경 (예: S5에 phone 지정)
        repo.updateActionMapping(defaultMapping.assignGesture(primaryStateId = 5, gestureId = "phone"))
        advanceUntilIdle()

        // 마지막 전송 상태와 달라졌으므로 hasUnsentChanges == true
        assertTrue(viewModel.uiState.value.hasUnsentChanges)
    }

    @Test
    fun `unsent changes become false after successful pushConfig`() = runTest(testDispatcher) {
        val repo = FakeHandRepository(defaultMapping)
        val viewModel = ActionViewModel(repo)
        advanceUntilIdle()

        repo.updateActionMapping(defaultMapping.assignGesture(primaryStateId = 5, gestureId = "phone"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasUnsentChanges)

        // Send 버튼 클릭 (pushConfig)
        viewModel.pushConfig()
        advanceUntilIdle()

        // 전송 완료 후 다시 hasUnsentChanges == false
        assertFalse(viewModel.uiState.value.hasUnsentChanges)
    }

    @Test
    fun `resetToDefault sends restored action ids immediately`() = runTest(testDispatcher) {
        val customMapping = defaultMapping.assignGesture(primaryStateId = 5, gestureId = "pointing")
        val repo = FakeHandRepository(customMapping)
        val viewModel = ActionViewModel(repo)
        advanceUntilIdle()

        // 초기 커스텀 매핑이 이미 동기화되어 있으므로 false
        assertFalse(viewModel.uiState.value.hasUnsentChanges)

        // Reset to Default 실행
        viewModel.resetToDefault()
        advanceUntilIdle()

        val expected = customMapping.copy(gestureIdByState = customMapping.catalog.defaultStateGestures)
        assertEquals(1, repo.pushCount)
        assertEquals(expected.toActionIds().toList(), repo.sentActionIds)
        assertFalse(viewModel.uiState.value.hasUnsentChanges)
    }
}
