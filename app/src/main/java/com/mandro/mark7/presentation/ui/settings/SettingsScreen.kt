package com.mandro.mark7.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.domain.model.ConfigPushState
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.presentation.components.SectionCard
import com.mandro.mark7.presentation.components.StatRow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val g = ui.config.settings

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("의수 설정", style = MaterialTheme.typography.headlineSmall)

        SectionCard(title = "모터별 전류 (mA)") {
            HandStatus.MOTOR_NAMES.forEachIndexed { i, name ->
                StatRow(name, "쥐기 ${g.graspCurrent[i]} · 펴기 ${g.releaseCurrent[i]} · 최대 ${g.maxCurrent[i]}")
            }
            // TODO: 슬라이더 편집 → viewModel.updateSettings { it.copy(graspCurrent = ...) }
        }

        SectionCard(title = "위치 / 속도") {
            HandStatus.MOTOR_NAMES.forEachIndexed { i, name ->
                StatRow(name, "쥐기위치 ${g.graspPos[i]} · 펴기위치 ${g.releasePos[i]} · 속도 ${g.motorSpeed[i]}")
            }
        }

        SectionCard(title = "점진적(gradual) SL") {
            HandStatus.MOTOR_NAMES.forEachIndexed { i, name ->
                StatRow(name, "SL ${g.slSetting[i]} · SL(gradual) ${g.slGradual[i]}")
            }
        }

        SectionCard(title = "EMG") {
            StatRow("증폭 (ch0 / ch1)", "${g.emgAmp[0]} / ${g.emgAmp[1]}")
            StatRow("필터", g.emgFilter.toString())
        }

        when (val p = ui.push) {
            is ConfigPushState.Sending -> Text("전송 중…")
            is ConfigPushState.Acked -> Text("의수가 SETok 응답 — 적용 완료")
            is ConfigPushState.Error -> Text("오류: ${p.message}", color = MaterialTheme.colorScheme.error)
            ConfigPushState.Idle -> Unit
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::push, modifier = Modifier.weight(1f)) { Text("SET 전송") }
            OutlinedButton(onClick = viewModel::resetToDefault) { Text("기본값") }
        }

        // TODO: 패턴 8개 편집 UI (S1..S8: 어떤 모터를, 어떤 순서/딜레이로)
    }
}
