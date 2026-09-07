package com.mandro.mark7

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    repo: HandRepository,
) : ViewModel() {
    val bleState: StateFlow<BleState> = repo.bleState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleState.Idle)
}
