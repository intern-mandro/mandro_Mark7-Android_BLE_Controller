package com.mandro.mark7.presentation.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.data.local.HandConfigStore
import com.mandro.mark7.data.local.UserStore
import com.mandro.mark7.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserSetupUiState(
    val users: List<UserProfile> = emptyList(),
    val activeUserId: String? = null,
    val busy: Boolean = false,
    /** null 이면 다이얼로그 닫힘. */
    val createDialogName: String? = null,
    val deleteTarget: UserProfile? = null,
) {
    val activeUser: UserProfile? get() = users.firstOrNull { it.id == activeUserId }
}

@HiltViewModel
class UserSetupViewModel @Inject constructor(
    private val userStore: UserStore,
    private val handConfigStore: HandConfigStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSetupUiState())
    val uiState = _uiState.asStateFlow()

    /** 사용자 선택/생성 완료 → 다음 화면(연결)으로. 일회성 이벤트. */
    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(userStore.users, userStore.activeUserId) { users, active -> users to active }
                .collect { (users, active) ->
                    _uiState.update { it.copy(users = users, activeUserId = active) }
                }
        }
    }

    /** 목록에서 사용자를 골랐을 때 (또는 하단 '계속하기'). */
    fun selectUser(id: String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            userStore.setActiveUser(id)
            _done.send(Unit)
        }
    }

    fun continueWithActive() {
        _uiState.value.activeUserId?.let { selectUser(it) }
    }

    // ── 새 사용자 만들기 다이얼로그 ──
    fun openCreateDialog() = _uiState.update { it.copy(createDialogName = "") }
    fun dismissCreateDialog() = _uiState.update { it.copy(createDialogName = null) }
    fun onCreateNameChange(v: String) = _uiState.update { it.copy(createDialogName = v) }

    fun confirmCreate() {
        val name = _uiState.value.createDialogName?.trim().orEmpty()
        if (name.isEmpty() || _uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, createDialogName = null) }
            userStore.createUser(name) // 생성 즉시 활성 사용자로 지정됨
            _done.send(Unit)
        }
    }

    // ── 사용자 삭제 ──
    fun requestDelete(user: UserProfile) = _uiState.update { it.copy(deleteTarget = user) }
    fun cancelDelete() = _uiState.update { it.copy(deleteTarget = null) }

    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleteTarget = null) }
            userStore.deleteUser(target.id)
            handConfigStore.deleteUserData(target.id)
        }
    }
}
