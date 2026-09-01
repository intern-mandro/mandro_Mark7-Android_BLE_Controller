package com.mandro.mark7.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.ConfigPushState
import com.mandro.mark7.domain.model.GlobalSettings
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val config: HandConfig = HandConfig.DEFAULT,
    val push: ConfigPushState = ConfigPushState.Idle,
)

/**
 * SET 프레임(전역 설정 + 패턴 8개) 편집·전송. 지금은 값 확인 + 전송/초기화만.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch { repo.config.collect { c -> _uiState.update { it.copy(config = c) } } }
        viewModelScope.launch {
            repo.configPushState.collect { p -> _uiState.update { it.copy(push = p) } }
        }
    }

    fun updateSettings(transform: (GlobalSettings) -> GlobalSettings) = viewModelScope.launch {
        val c = _uiState.value.config
        repo.updateConfig(c.copy(settings = transform(c.settings)))
    }

    fun resetToDefault() = viewModelScope.launch { repo.updateConfig(HandConfig.DEFAULT) }

    fun push() = viewModelScope.launch { repo.pushConfig() }
}
