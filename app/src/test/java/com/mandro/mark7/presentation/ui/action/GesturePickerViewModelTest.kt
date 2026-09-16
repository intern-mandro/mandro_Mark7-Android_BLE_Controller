package com.mandro.mark7.presentation.ui.action

import androidx.lifecycle.SavedStateHandle
import com.mandro.mark7.domain.model.hand.HandConfig
import com.mandro.mark7.domain.model.hand.ManualPreset
import com.mandro.mark7.domain.model.connection.BleDevice
import com.mandro.mark7.domain.model.connection.BleState
import com.mandro.mark7.domain.model.connection.ConfigPushState
import com.mandro.mark7.domain.model.hand.MotorCommand
import com.mandro.mark7.domain.model.action.ActionMapping
import com.mandro.mark7.domain.model.action.ActionSlotMode
import com.mandro.mark7.domain.model.hand.HandDof
import com.mandro.mark7.domain.model.hand.HandStatus
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pair 뒤 노드(S2 등)에서 손 선택 화면을 열었을 때도 묶음 전체를 앞 노드 기준으로 편집하는지 검증. */
@OptIn(ExperimentalCoroutinesApi::class)
class GesturePickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeHandRepository(initial: ActionMapping) : HandRepository {
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
        override suspend fun pushConfig(config: HandConfig?): Result<Unit> = Result.success(Unit)
        override val actionMapping = MutableStateFlow(initial)
        override val syncedActionMapping = MutableStateFlow(initial)
        override suspend fun updateActionMapping(mapping: ActionMapping) {
            actionMapping.value = mapping
        }
        override val manualPresets: StateFlow<List<ManualPreset>> = MutableStateFlow(emptyList())
        override suspend fun saveManualPresets(presets: List<ManualPreset>) {}
        override suspend fun resetManualPresets() {}
    }

    private val pairAtS1 = ActionMapping().assignGesture(primaryStateId = 1, gestureId = "cylinder_grip_open")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening from the back node starts from the current pair`() {
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "2")), FakeHandRepository(pairAtS1))

        assertEquals(1, viewModel.stateId)
        assertEquals("cylinder_grip_open", viewModel.selectedId.value)
    }

    @Test
    fun `picking another pair from the back node rewrites both nodes in order`() = runTest(testDispatcher) {
        val repo = FakeHandRepository(pairAtS1)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "2")), repo)

        val shouldClose = viewModel.select("tip_pinch_closed")
        assertFalse(shouldClose)
        advanceUntilIdle()

        assertEquals("tip_pinch_open", repo.actionMapping.value.effectiveGestureIdFor(1))
        assertEquals("tip_pinch_closed", repo.actionMapping.value.effectiveGestureIdFor(2))
    }

    @Test
    fun `upper pair picker rejects single actions`() = runTest(testDispatcher) {
        val repo = FakeHandRepository(pairAtS1)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "2")), repo)

        val shouldClose = viewModel.select("pointing")
        assertFalse(shouldClose)
        advanceUntilIdle()

        assertEquals(pairAtS1, repo.actionMapping.value)
        assertTrue(viewModel.options.all { viewModel.catalog.slotGesturesFor(it.id)?.companion != null })
    }

    @Test
    fun `lower single picker rejects paired gestures`() {
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "7")), FakeHandRepository(pairAtS1))
        assertFalse(viewModel.select("trigger_closed"))
        assertEquals(null, viewModel.selectedId.value)
        assertEquals((11..19).toList(), viewModel.options.map { viewModel.catalog.actionIdFor(it.id) })
    }

    @Test
    fun `tapping the already selected pair again closes without changing it`() = runTest(testDispatcher) {
        val repo = FakeHandRepository(pairAtS1)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "1")), repo)

        // Pair 는 뒤 칸을 눌러도 같은 선택이다.
        assertTrue(viewModel.select("cylinder_grip_closed"))
        advanceUntilIdle()

        assertEquals(pairAtS1, repo.actionMapping.value)
    }

    @Test
    fun `first tap on a new gesture selects it and second tap closes`() = runTest(testDispatcher) {
        val repo = FakeHandRepository(pairAtS1)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "5")), repo)

        assertFalse(viewModel.select("pointing"))
        advanceUntilIdle()
        assertEquals("pointing", viewModel.selectedId.value)
        assertEquals("pointing", repo.actionMapping.value.effectiveGestureIdFor(5))
        assertEquals(ActionSlotMode.DISABLED, repo.actionMapping.value.slotModeFor(6))

        assertTrue(viewModel.select("pointing"))
        assertTrue(viewModel.options.all { viewModel.catalog.slotGesturesFor(it.id)?.companion == null })
    }

    @Test
    fun `exiting without push or second tap reverts to initial mapping`() = runTest(testDispatcher) {
        val initial = pairAtS1.assignGesture(primaryStateId = 5, gestureId = "phone")
        val repo = FakeHandRepository(initial)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "5")), repo)

        // 다른 손 모양 탭 (임시 선택)
        assertFalse(viewModel.select("pointing"))
        advanceUntilIdle()
        assertEquals("pointing", repo.actionMapping.value.effectiveGestureIdFor(5))

        // Send 나 재클릭 없이 뒤로가기
        val committed = viewModel.onBack()
        assertFalse(committed)
        advanceUntilIdle()

        // 원래 손 모양(phone)으로 복원되었는지 검증
        assertEquals("phone", repo.actionMapping.value.effectiveGestureIdFor(5))
    }

    @Test
    fun `exiting after pushConfig retains new mapping`() = runTest(testDispatcher) {
        val initial = pairAtS1.assignGesture(primaryStateId = 5, gestureId = "phone")
        val repo = FakeHandRepository(initial)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "5")), repo)

        viewModel.select("pointing")
        advanceUntilIdle()

        // Send 버튼 클릭
        viewModel.pushConfig()
        advanceUntilIdle()

        // Send 이후 뒤로가기
        val committed = viewModel.onBack()
        assertTrue(committed)
        advanceUntilIdle()

        // 새 매핑(pointing)이 유지되는지 검증
        assertEquals("pointing", repo.actionMapping.value.effectiveGestureIdFor(5))
    }

    @Test
    fun `exiting after second tap retains new mapping`() = runTest(testDispatcher) {
        val initial = pairAtS1.assignGesture(primaryStateId = 5, gestureId = "phone")
        val repo = FakeHandRepository(initial)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "5")), repo)

        // 첫 번째 탭
        assertFalse(viewModel.select("pointing"))
        advanceUntilIdle()

        // 동일한 것 두 번째 탭 (확정)
        assertTrue(viewModel.select("pointing"))

        // 이후 onBack 호출
        val committed = viewModel.onBack()
        assertTrue(committed)
        advanceUntilIdle()

        // 새 매핑(pointing)이 유지되는지 검증
        assertEquals("pointing", repo.actionMapping.value.effectiveGestureIdFor(5))
    }

    @Test
    fun `isOriginalState returns true for initial pair lead and companion and false for others`() {
        val repo = FakeHandRepository(pairAtS1)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "1")), repo)

        assertTrue(viewModel.isOriginalState("cylinder_grip_open"))
        assertTrue(viewModel.isOriginalState("cylinder_grip_closed"))
        assertFalse(viewModel.isOriginalState("tip_pinch_open"))
        assertFalse(viewModel.isOriginalState("tip_pinch_closed"))
    }

    @Test
    fun `isOriginalState returns true for initial single gesture and false for others`() {
        val initial = pairAtS1.assignGesture(primaryStateId = 5, gestureId = "phone")
        val repo = FakeHandRepository(initial)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "5")), repo)

        assertTrue(viewModel.isOriginalState("phone"))
        assertFalse(viewModel.isOriginalState("pointing"))
    }

    @Test
    fun `syncedId tracks syncedActionMapping separately from selectedId`() = runTest(testDispatcher) {
        val initial = pairAtS1.assignGesture(primaryStateId = 5, gestureId = "phone")
        val repo = FakeHandRepository(initial)
        val viewModel = GesturePickerViewModel(SavedStateHandle(mapOf("state" to "5")), repo)

        assertEquals("phone", viewModel.selectedId.value)
        assertEquals("phone", viewModel.syncedId.value)

        // 다른 손 모양 선택 (임시 선택)
        viewModel.select("pointing")
        advanceUntilIdle()

        // selectedId 는 pointing 으로 바뀌지만, syncedId 는 여전히 의수에 저장된 phone 유지
        assertEquals("pointing", viewModel.selectedId.value)
        assertEquals("phone", viewModel.syncedId.value)
    }
}
