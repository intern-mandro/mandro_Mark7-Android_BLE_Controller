package com.mandro.mark7.presentation.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.repository.HandRepository
import com.mandro.mark7.presentation.theme.Mark7Theme
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
    SplashContent()
}

@Composable
private fun SplashContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun SplashPreview() {
    Mark7Theme { SplashContent() }
}
