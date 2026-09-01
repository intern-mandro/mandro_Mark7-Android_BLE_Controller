package com.mandro.mark7.presentation.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.presentation.components.SectionCard
import com.mandro.mark7.presentation.components.StatRow

@Composable
fun ScanScreen(
    onConnected: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(ui.connected) { if (ui.connected) onConnected() }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("의수 연결", style = MaterialTheme.typography.headlineSmall)

        SectionCard(
            title = "발견된 기기",
            trailing = { Button(onClick = viewModel::rescan) { Text("다시 스캔") } },
        ) {
            val state = ui.bleState
            when {
                state is BleState.Error -> StatRow("오류", state.message)
                ui.devices.isEmpty() -> StatRow("상태", if (state is BleState.Scanning) "스캔 중…" else "없음")
                else -> ui.devices.forEach { device ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.connect(device) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${device.address}   RSSI ${device.rssi}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        // TODO: BLE 권한 요청 플로우 (Android 12+ BLUETOOTH_SCAN/CONNECT) 붙이기
        // TODO: 페어링/재연결 UX — 마지막 연결 기기 기억 (DataStore)
    }
}
