package com.mandro.mark7.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mandro.mark7.R
import com.mandro.mark7.presentation.theme.Mark7Palette
import kotlin.math.roundToInt

/**
 * '기본값' 등 되돌릴 수 없는 초기화 액션 앞에 띄우는 공통 확인 다이얼로그.
 * 표시 여부는 호출부에서 관리하고, 이 컴포저블이 보이면 이미 "확인" 대기 상태다.
 * [onConfirm] 은 확인 시 1회 호출된다(다이얼로그는 그 전에 닫힌다).
 */
@Composable
fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String = stringResource(R.string.common_reset_msg),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mark7Palette.Surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.common_reset_title),
                fontWeight = FontWeight.Bold,
                color = Mark7Palette.Ink,
            )
        },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(
                    text = stringResource(R.string.common_reset_confirm),
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Danger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** 화면 섹션을 감싸는 카드. 제목 + 우측 보조 슬롯 + 내용. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Mark7Palette.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                trailing?.invoke()
            }
            content()
        }
    }
}

/** 라벨 + 값 한 줄. 모니터링/상태 표시에 사용. */
@Composable
fun StatRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Mark7Palette.InkMuted)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * 슬라이더 하단에 표시되는 구간 프리셋 정보
 */
data class SliderInterval(
    val label: String,
    val isActive: Boolean,
    val onClick: () -> Unit,
    val weight: Float = 1f,
)

/**
 * Mark7 전용 커스텀 슬라이더.
 * - 상단: (선택) 타이틀, 최소/최대 기준값 표시 (도달 시 파란색 볼드 강조), 썸(Thumb) 추종 동적 수치 표시
 * - 좌측: (선택) leadingLabel (예: "CH 1", "CH 2") 트랙 중심 수평 정렬
 * - 트랙: 구간 구분선(ticks), 점(dots) 없는 매끄러운 6dp 바
 * - 하단: (선택) 구간 라벨(Soft, Standard, Strong 등)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Mark7Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    leadingLabel: String? = null,
    leadingWidth: Dp = 36.dp,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    ticks: List<Float> = emptyList(),
    minLabel: String? = null,
    maxLabel: String? = null,
    formatValue: ((Float) -> String)? = null,
    intervals: List<SliderInterval>? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Mark7Palette.InkMuted,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            if (leadingLabel != null) {
                Box(
                    modifier = Modifier
                        .width(leadingWidth)
                        .padding(top = if (minLabel != null) 16.dp else 0.dp)
                        .height(20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = leadingLabel,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Mark7Palette.Ink,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (minLabel != null && maxLabel != null && formatValue != null) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp),
                    ) {
                        val rangeSpan = valueRange.endInclusive - valueRange.start
                        val activeFraction = if (rangeSpan > 0f) {
                            ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
                        } else 0f

                        val density = LocalDensity.current
                        val thumbRadius = 7.dp
                        val trackSpan = maxWidth - 14.dp
                        val thumbCenter = thumbRadius + trackSpan * activeFraction

                        val isAtMin = value <= valueRange.start
                        val isAtMax = value >= valueRange.endInclusive

                        val distFromMinDp = (thumbCenter - thumbRadius).coerceAtLeast(0.dp)
                        val distFromMaxDp = (maxWidth - thumbRadius - thumbCenter).coerceAtLeast(0.dp)

                        val mergeRangeDp = 20.dp
                        val minProgress = (distFromMinDp / mergeRangeDp).coerceIn(0f, 1f)
                        val maxProgress = (distFromMaxDp / mergeRangeDp).coerceIn(0f, 1f)

                        val minColor = androidx.compose.ui.graphics.lerp(
                            Mark7Palette.Accent,
                            Mark7Palette.InkMuted,
                            if (isAtMin) 0f else minProgress,
                        )
                        val maxColor = androidx.compose.ui.graphics.lerp(
                            Mark7Palette.Accent,
                            Mark7Palette.InkMuted,
                            if (isAtMax) 0f else maxProgress,
                        )

                        Text(
                            text = minLabel,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (isAtMin || minProgress < 0.2f) FontWeight.Bold else FontWeight.Normal,
                            color = minColor,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 7.dp),
                        )

                        Text(
                            text = maxLabel,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (isAtMax || maxProgress < 0.2f) FontWeight.Bold else FontWeight.Normal,
                            color = maxColor,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 7.dp),
                        )

                        val floatingAlpha = when {
                            isAtMin || isAtMax -> 0f
                            else -> minOf(minProgress, maxProgress)
                        }

                        if (floatingAlpha > 0.01f) {
                            Text(
                                text = formatValue(value),
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Mark7Palette.Accent.copy(alpha = floatingAlpha),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(
                                            constraints.copy(minWidth = 0)
                                        )
                                        val minXPx = with(density) { 7.dp.toPx() }
                                        val maxXPx = (constraints.maxWidth - placeable.width - with(density) { 7.dp.toPx() }).coerceAtLeast(minXPx)
                                        val centerPx = with(density) { thumbCenter.toPx() }
                                        val halfW = placeable.width / 2f
                                        val clampedX = (centerPx - halfW).coerceIn(minXPx, maxXPx).roundToInt()
                                        layout(constraints.maxWidth, placeable.height) {
                                            placeable.place(clampedX, 0)
                                        }
                                    },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Slider(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        valueRange = valueRange,
                        onValueChangeFinished = onValueChangeFinished,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Mark7Palette.Accent,
                            inactiveTrackColor = Color(0xFFE2E8F0),
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .shadow(1.5.dp, CircleShape)
                                    .background(Mark7Palette.Accent, CircleShape),
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier
                                    .height(6.dp)
                                    .drawWithContent {
                                        drawContent()
                                        if (ticks.isNotEmpty()) {
                                            val rangeSpan = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                            if (rangeSpan > 0f) {
                                                val thumbRadius = 7.dp.toPx()
                                                val availableWidth = size.width - 2 * thumbRadius
                                                val activeFraction = ((sliderState.value - sliderState.valueRange.start) / rangeSpan).coerceIn(0f, 1f)
                                                val activeX = thumbRadius + activeFraction * availableWidth

                                                for (tick in ticks) {
                                                    val tickFraction = ((tick - sliderState.valueRange.start) / rangeSpan).coerceIn(0f, 1f)
                                                    val tickX = thumbRadius + tickFraction * availableWidth
                                                    if (kotlin.math.abs(tickX - activeX) > 8.dp.toPx()) {
                                                        val isPassed = tickX <= activeX
                                                        val tickColor = if (isPassed) Color.White.copy(alpha = 0.85f) else Color(0xFF94A3B8)
                                                        drawLine(
                                                            color = tickColor,
                                                            start = Offset(tickX, 0f),
                                                            end = Offset(tickX, size.height),
                                                            strokeWidth = 1.8.dp.toPx(),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Mark7Palette.Accent,
                                    inactiveTrackColor = Color(0xFFE2E8F0),
                                ),
                                drawStopIndicator = null,
                                thumbTrackGapSize = 0.dp,
                            )
                        }
                    )
                }

                if (!intervals.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 7.dp)
                            .height(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (interval in intervals) {
                            Box(
                                modifier = Modifier
                                    .weight(interval.weight)
                                    .clickable(onClick = interval.onClick),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = interval.label,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = if (interval.isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (interval.isActive) Mark7Palette.Accent else Mark7Palette.InkMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 카드 헤더 우측의 작은 알약형 버튼 (프리셋 / 일괄 설정 / 추가 등). 배경 틴트 + 넉넉한 패딩. */
@Composable
fun HeaderPillButton(
    text: String,
    onClick: () -> Unit,
    color: Color = Mark7Palette.Accent,
    strong: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

