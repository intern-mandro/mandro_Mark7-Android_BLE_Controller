package com.mandro.mark7.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandro.mark7.presentation.theme.Mark7Palette

/** 가로 막대 게이지. [fraction] 은 0f..1f. [markerFraction] 을 주면 그 지점에 얇은 기준선. */
@Composable
fun BarMeter(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = Mark7Palette.SurfaceAlt,
    height: Dp = 10.dp,
    markerFraction: Float? = null,
    markerColor: Color = Mark7Palette.Danger,
) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(trackColor)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        markerFraction?.let { mf ->
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(mf.coerceIn(0f, 1f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(markerColor))
            }
        }
    }
}

/** 구간 막대: 트랙 위 [startFraction]~[endFraction] 구간만 채운다(가동 범위 등). */
@Composable
fun RangeBar(
    startFraction: Float,
    endFraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = Mark7Palette.SurfaceAlt,
    height: Dp = 10.dp,
) {
    val lo = minOf(startFraction, endFraction).coerceIn(0f, 1f)
    val hi = maxOf(startFraction, endFraction).coerceIn(0f, 1f)
    BoxWithConstraints(modifier.height(height).clip(RoundedCornerShape(50)).background(trackColor)) {
        val w = maxWidth
        Box(
            Modifier
                .padding(start = w * lo)
                .width((w * (hi - lo)).coerceAtLeast(3.dp))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

/** 라벨 + 구간 막대 + 값 한 줄. */
@Composable
fun RangeMeterRow(
    label: String,
    valueText: String,
    startFraction: Float,
    endFraction: Float,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Mark7Palette.InkMuted,
            modifier = Modifier.width(28.dp),
        )
        RangeBar(startFraction, endFraction, color, Modifier.weight(1f))
        Text(
            valueText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(84.dp),
        )
    }
}

/** 라벨 + 막대 + 값 한 줄. 모니터링·설정의 모터별 수치용. */
@Composable
fun MeterRow(
    label: String,
    valueText: String,
    fraction: Float,
    color: Color,
    markerFraction: Float? = null,
    labelWidth: Dp = 28.dp,
    valueWidth: Dp = 72.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Mark7Palette.InkMuted,
            modifier = Modifier.width(labelWidth),
        )
        BarMeter(fraction, color, Modifier.weight(1f), markerFraction = markerFraction)
        Text(
            valueText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(valueWidth),
        )
    }
}

/**
 * 여러 계열의 미니 꺾은선(스파크라인). [series] 의 각 원소 = 한 선의 y 값들(시간순, 왼→오).
 * [yMin]/[yMax] 를 주면 고정 축, 없으면 데이터에 맞춰 자동. [guides] 는 점선 가로 기준선.
 */
@Composable
fun MiniLineChart(
    series: List<FloatArray>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    yMin: Float? = null,
    yMax: Float? = null,
    guides: List<Float> = emptyList(),
) {
    Canvas(modifier) {
        val flat = series.flatMap { it.asList() }
        if (flat.isEmpty()) return@Canvas
        val lo = yMin ?: flat.min()
        val hi = yMax ?: flat.max()
        val span = (hi - lo).takeIf { it > 1e-3f } ?: 1f
        fun projectY(v: Float): Float = size.height * (1f - ((v - lo) / span)).coerceIn(0f, 1f)

        guides.forEach { g ->
            val gy = projectY(g)
            drawLine(
                color = Mark7Palette.Line,
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        series.forEachIndexed { idx, line ->
            if (line.isEmpty()) return@forEachIndexed
            val color = colors.getOrElse(idx) { colors.firstOrNull() ?: Mark7Palette.Accent }

            if (line.size == 1) {
                // 단일 표본일 때도 위치를 인지할 수 있도록 점을 그린다
                val cx = size.width / 2f
                val cy = projectY(line[0])
                drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(cx, cy))
                return@forEachIndexed
            }

            val stepX = size.width / (line.size - 1)
            val path = Path()
            line.forEachIndexed { i, v ->
                val x = stepX * i
                val y = projectY(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = color,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )

            // 최신 값 위치에 둥근 도트를 표시해 현재 위치를 직관적으로 강조
            val lastX = size.width
            val lastY = projectY(line.last())
            drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))
        }
    }
}
