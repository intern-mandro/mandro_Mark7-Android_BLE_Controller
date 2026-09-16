package com.mandro.mark7.presentation.ui.settings

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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 설정 화면에 보이는 값(= 저장된 config)은 항상 "의수에 마지막으로 보낸 값"이어야 한다.
 * 토글을 다시 열거나 앱을 다시 켜면 이 값으로 돌아오므로, 전송에 실패한 값은 저장하면 안 된다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeHandRepository(private val pushSucceeds: Boolean) : HandRepository {
        override val config = MutableStateFlow(HandConfig.DEFAULT)
        val pushed = mutableListOf<HandConfig>()

        override suspend fun updateConfig(config: HandConfig) {
            this.config.value = config
        }

        override suspend fun pushConfig(config: HandConfig?): Result<Unit> {
            pushed += config ?: this.config.value
            return if (pushSucceeds) Result.success(Unit) else Result.failure(IllegalStateException("write failed"))
        }

        override val selectedDof: StateFlow<HandDof> = MutableStateFlow(HandDof.DEFAULT)
        override val connectedDof: StateFlow<HandDof?> = MutableStateFlow(null)
        override val activeDof: StateFlow<HandDof> = selectedDof
        override val bleState: Flow<BleState> = emptyFlow()
        override val status: Flow<HandStatus> = emptyFlow()
        override val configPushState: Flow<ConfigPushState> = emptyFlow()
        override val actionMapping: StateFlow<ActionMapping> = MutableStateFlow(ActionMapping())
        override val syncedActionMapping: StateFlow<ActionMapping> = actionMapping
        override val manualPresets: StateFlow<List<ManualPreset>> = MutableStateFlow(emptyList())
        override suspend fun startScan() = Unit
        override suspend fun stopScan() = Unit
        override suspend fun connect(device: BleDevice) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun sendCommand(command: MotorCommand) = Unit
        override suspend fun updateActionMapping(mapping: ActionMapping) = Unit
        override suspend fun saveManualPresets(presets: List<ManualPreset>) = Unit
        override suspend fun resetManualPresets() = Unit
    }

    /** 사용자가 EMG 증폭을 10 → 13 으로 바꾼 draft. */
    private val edited = HandConfig.DEFAULT.settings.copy(emgAmp = listOf(13, 13))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful send makes the edited values the last sent values`() = runTest(dispatcher) {
        val repo = FakeHandRepository(pushSucceeds = true)
        val viewModel = SettingsViewModel(repo)

        viewModel.applyAndPush(edited)
        testScheduler.advanceUntilIdle()

        assertEquals(edited, repo.pushed.single().settings)
        assertEquals(edited, repo.config.value.settings)
        assertEquals(edited, viewModel.uiState.value.config.settings)
    }

    @Test
    fun `failed send keeps the last sent values so reopening shows them again`() = runTest(dispatcher) {
        val repo = FakeHandRepository(pushSucceeds = false)
        val viewModel = SettingsViewModel(repo)

        viewModel.applyAndPush(edited)
        testScheduler.advanceUntilIdle()

        assertEquals("전송은 시도했다", edited, repo.pushed.single().settings)
        assertEquals("보내지 못한 값은 저장하지 않는다", HandConfig.DEFAULT, repo.config.value)
        assertEquals(HandConfig.DEFAULT, viewModel.uiState.value.config)
    }
}
