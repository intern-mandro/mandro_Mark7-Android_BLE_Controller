package com.mandro.mark7.presentation.ui.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.presentation.components.SectionCard
import com.mandro.mark7.presentation.components.StatRow

@Composable
fun MonitorScreen(
    onDisconnected: () -> Unit,
    viewModel: MonitorViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(ui.connected) { if (!ui.connected) onDisconnected() }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("모니터링", style = MaterialTheme.typography.headlineSmall)

        val status = ui.status
        SectionCard(
            title = "모터 상태",
            trailing = { OutlinedButton(onClick = viewModel::disconnect) { Text("연결 해제") } },
        ) {
            if (status == null) {
                StatRow("상태", "STATUS 프레임 수신 대기 중…")
            } else {
                HandStatus.MOTOR_NAMES.forEachIndexed { i, name ->
                    StatRow(
                        label = name,
                        value = "온도 ${status.motorTemp[i]}°C · 전류 ${status.motorCurrentAvg[i]}mA · 위치 ${status.motorTurn[i]}",
                        emphasize = status.motorTemp[i] >= 60,
                    )
                }
            }
        }

        if (status != null) {
            SectionCard(title = "EMG") {
                StatRow("채널 0", status.emg[0].toString())
                StatRow("채널 1", status.emg[1].toString())
                StatRow("체크섬", if (status.checksumOk) "정상" else "불일치(표시용)")
            }
        }

        // TODO: 온도/전류 시계열 그래프 (Mark7Palette.motorColors)
        // TODO: 과열/과전류 임계값 경보 + 자동 STOP 프레임 옵션
    }
}
