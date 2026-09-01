package com.mandro.mark7.presentation.ui.action

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mandro.mark7.domain.model.HandAction
import com.mandro.mark7.presentation.components.SectionCard
import com.mandro.mark7.presentation.theme.Mark7Palette

/**
 * 액션 → 패턴 매핑 화면. 각 액션 카드에 현재 연결된 패턴의 대표 사진을 보여주고,
 * 탭하면 사진 그리드에서 다른 패턴을 고른다 (PatternPickerScreen).
 */
@Composable
fun ActionScreen(
    onPickPattern: (HandAction) -> Unit,
    viewModel: ActionViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("액션 설정", style = MaterialTheme.typography.headlineSmall)
        Text(
            "암밴드가 인식하는 4가지 동작에 손 모양을 연결하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = Mark7Palette.InkMuted,
        )

        HandAction.entries.forEach { action ->
            val patternIndex = ui.mapping.patternFor(action)
            SectionCard(
                title = action.displayName,
                trailing = {
                    Text(
                        patternIndex?.let { "패턴 ${it + 1}" } ?: "미지정",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mark7Palette.InkMuted,
                    )
                },
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { onPickPattern(action) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val thumb = GestureAssets.thumbnailFor(context, action)
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                            .background(Mark7Palette.SurfaceAlt),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (thumb != null) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = action.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text("사진 없음", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text("사진으로 손 모양 고르기 →", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionCard(title = "점진적 잡기") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "굽히기를 유지하는 동안 S1→S8 단계로 조금씩 더 쥡니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = ui.mapping.gradualGraspEnabled,
                    onCheckedChange = viewModel::setGradual,
                )
            }
        }

        Button(
            onClick = viewModel::pushConfig,
            enabled = !ui.pushing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ui.pushing) "전송 중…" else "의수에 적용 (SET 전송)")
        }

        // TODO: 패턴별 미리보기 애니메이션 (프레임 시퀀스 재생)
        // TODO: transitionTable 시각화 — 어떤 액션이 어느 상태로 가는지 다이어그램
    }
}
