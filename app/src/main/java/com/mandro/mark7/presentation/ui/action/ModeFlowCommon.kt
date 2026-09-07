package com.mandro.mark7.presentation.ui.action

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.Gesture
import com.mandro.mark7.domain.model.GestureCatalog
import com.mandro.mark7.domain.model.ModeState
import com.mandro.mark7.presentation.theme.Mark7Palette

/** 굽힘(F) — 시인성 높은 선명한 블루. */
internal val FlexionColor = Color(0xFF1565C0)

/** 폄(E) — 복귀 방향, 명확히 잘 보이는 다크 슬레이트. */
internal val ExtensionColor = Color(0xFF475569)

/** 상태에 연결된 손 모양(고정 S1 은 IDLE). 지정 안 됐으면 null. */
internal fun gestureFor(state: ModeState, mapping: ActionMapping): Gesture? {
    val id = if (state.fixed) GestureCatalog.IDLE.id else mapping.gestureIdFor(state.id)
    return GestureCatalog.byId(id)
}

/** 손 모양 이름(로케일 반영). null 이면 "미지정". */
@Composable
internal fun gestureLabel(gesture: Gesture?): String =
    gesture?.let { stringResource(it.displayNameRes) } ?: stringResource(R.string.common_unset)

/**
 * 맨 위 모드 표시줄. [currentMode] (1/2, 아직 모르면 null) 세그먼트가 강조되고,
 * 다른 쪽을 누르면 확인 다이얼로그 뒤 [onConfirmMode] 를 호출한다. `close` 입력으로
 * 펌웨어가 자동 전환한 경우에도 STATUS 로 들어온 [currentMode] 가 그대로 반영된다.
 */
@Composable
internal fun ModeBar(
    currentMode: Int?,
    onConfirmMode: (Int) -> Unit,
    errorMessage: String?,
    onDismissError: () -> Unit,
) {
    var pending by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Mark7Palette.Line, RoundedCornerShape(10.dp)),
        ) {
            ModeSegment(
                label = stringResource(R.string.mode_seg_1),
                active = currentMode == 1,
                onClick = { onDismissError(); pending = 1 },
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(1.dp).height(44.dp).background(Mark7Palette.Line))
            ModeSegment(
                label = stringResource(R.string.mode_seg_2),
                active = currentMode == 2,
                onClick = { onDismissError(); pending = 2 },
                modifier = Modifier.weight(1f),
            )
        }
        when {
            errorMessage != null -> Text(errorMessage, style = MaterialTheme.typography.labelSmall, color = Mark7Palette.Danger)
            currentMode == null -> Text(stringResource(R.string.mode_checking), style = MaterialTheme.typography.labelSmall, color = Mark7Palette.InkMuted)
        }
    }

    pending?.let { target ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.mode_switch_title, target)) },
            text = { Text(stringResource(R.string.mode_switch_body, target)) },
            confirmButton = {
                TextButton(onClick = { pending = null; onConfirmMode(target) }) {
                    Text(stringResource(R.string.mode_switch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.mode_switch_cancel)) }
            },
        )
    }
}

@Composable
private fun ModeSegment(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(if (active) Mark7Palette.Accent else Mark7Palette.Surface)
            .clickable(enabled = !active, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else Mark7Palette.InkMuted,
        )
    }
}
