package com.mandro.mark7.presentation.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val repo: HandRepository,
) : ViewModel() {
    suspend fun isConnected(): Boolean = repo.bleState.first() is BleState.Connected
}

@Composable
fun SplashScreen(
    onReady: (connected: Boolean) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { onReady(viewModel.isConnected()) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Mark7 BLE", style = MaterialTheme.typography.headlineMedium)
    }
}
