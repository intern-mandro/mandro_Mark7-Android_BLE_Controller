package com.mandro.mark7.presentation.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.domain.model.CmdDir
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.presentation.components.SectionCard

@Composable
fun ManualScreen(
    viewModel: ManualViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("수동 제어", style = MaterialTheme.typography.headlineSmall)

        SectionCard(title = "손가락 선택") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HandStatus.MOTOR_NAMES.forEachIndexed { i, name ->
                    FilterChip(
                        selected = ui.select[i],
                        onClick = { viewModel.toggleFinger(i) },
                        label = { Text(name.substringBefore(' ')) },
                    )
                }
            }
            // TODO: 손가락별 위치 슬라이더 (0~360, 펌웨어 환산 pos = byte*2 - 21)
            // TODO: 속도/전류 슬라이더 — 현재 speed=${ui.speedRaw}, current=${ui.currentMa}
        }

        SectionCard(title = "명령") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.send(CmdDir.GRASP) }) { Text("GRASP") }
                Button(onClick = { viewModel.send(CmdDir.RELEASE) }) { Text("RELEASE") }
                OutlinedButton(onClick = { viewModel.send(CmdDir.STOP) }) { Text("STOP") }
            }
            OutlinedButton(onClick = viewModel::resetPower) { Text("RESET POWER") }
        }
    }
}
