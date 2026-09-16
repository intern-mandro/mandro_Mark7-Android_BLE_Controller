package com.mandro.mark7.presentation.ui.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.hand.HandStatus
import com.mandro.mark7.domain.model.hand.HandDof
import com.mandro.mark7.presentation.components.SectionCard
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme
import kotlin.math.cos
import kotlin.math.sin


@Composable
fun MonitorScreen(
    onDisconnected: () -> Unit,
    viewModel: MonitorViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(ui.connected) { if (!ui.connected) onDisconnected() }
    MonitorContent(
        ui = ui,
        emgBuffers = viewModel.emgBuffers,
        onDisconnect = viewModel::disconnect,
    )
}

@Composable
private fun MonitorContent(
    ui: MonitorUiState,
    emgBuffers: Array<FloatArray>? = null,
    onDisconnect: () -> Unit,
) {
    val status = ui.status

    // STATUS 미수신이면 레이아웃(테이블 "-" / EMG "신호 없음")은 그대로 두고
    // 그 위에 어두운 스크림 + 중앙 경고 창을 덮어 사용자가 즉시 인지하게 한다.
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 상단 타이틀 영역
        Text(
            text = stringResource(R.string.monitor_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Mark7Palette.Ink,
        )

        // 1. 모터 상태 테이블 (온도 및 위치/회전량 수치)
        SectionCard(
            title = "${stringResource(R.string.monitor_card_motor_status)} (${ui.dof.dof} DOF)",
            trailing = { VoltageReading(voltageText = status?.voltageText) },
        ) {
            MotorStatusTable(
                dof = ui.dof,
                temps = status?.motorTemp,
                turns = status?.motorTurn,
            )
        }

        // 2. EMG 센서 실시간 파형 모니터 (C:\Intern\mandro-final_Armband_Android\mandro-dynamic-gesture WaveformScreen 참고)
        SectionCard(
            title = stringResource(R.string.monitor_card_emg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1420)) // MandroPalette.DarkBg
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // CH 1 (빨간색 - MandroPalette.WaveCH0: #E44444)
                EmgChannelRow(
                    channelName = "CH 1",
                    value = status?.emg?.getOrNull(0),
                    buffer = status?.let { emgBuffers?.getOrNull(0) },
                    writePtr = ui.writePtr,
                    color = Color(0xFFE44444),
                )

                HorizontalDivider(
                    color = Color(0xFF1F2636),
                    thickness = 0.8.dp,
                )

                // CH 2 (파란색 - MandroPalette.WaveCH5: #446CE4)
                EmgChannelRow(
                    channelName = "CH 2",
                    value = status?.emg?.getOrNull(1),
                    buffer = status?.let { emgBuffers?.getOrNull(1) },
                    writePtr = ui.writePtr,
                    color = Color(0xFF446CE4),
                )
            }
        }
    }

        if (status == null) WaitingStatusOverlay()
    }
}

/** 데이터가 아직 없을 때 계측 자리를 채우는 플레이스홀더. */
private const val PLACEHOLDER_DASH = "-"

/** 모터 상태 카드 제목 줄 오른쪽의 STATUS 전압(예: "12.7 V"). STATUS 미수신이면 "-". */
@Composable
private fun VoltageReading(voltageText: String?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = voltageText ?: PLACEHOLDER_DASH,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Mark7Palette.Ink,
        )
        Text(
            text = " V",
            style = MaterialTheme.typography.labelMedium,
            color = Mark7Palette.InkMuted,
        )
    }
}

/**
 * STATUS 미수신 상태를 알리는 중앙 경고 창 + 어두운 스크림. 연결은 됐지만 프로토콜이 안 맞아
 * STATUS 가 해석되지 않을 때도 여기로 들어와 이 안내가 뜬다.
 * 스크림이 아래 컨텐츠 터치를 삼켜 "지금은 상호작용 불가"를 분명히 한다.
 */
@Composable
private fun BoxScope.WaitingStatusOverlay() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) { detectTapGestures { /* 아래로 통과 차단 */ } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Mark7Palette.Surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(horizontal = 24.dp),
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 대기는 오류가 아니라 "찾는 중" — 스피너를 표식으로 쓴다.
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = Mark7Palette.Accent,
                )
                Text(
                    text = stringResource(R.string.monitor_waiting_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.monitor_waiting_status_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mark7Palette.InkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 모터의 온도와 소비 전류를 정갈하게 정렬한 3열 계측 테이블.
 *  [temps]/[currents] 가 null 이면(STATUS 미수신) 값 자리를 "-" 로 표시한다. */
@Composable
private fun MotorStatusTable(
    dof: HandDof = HandDof.DEFAULT,
    temps: IntArray?,
    turns: IntArray?,
) {
    val motorResList = dof.motorNameRes
    Column(Modifier.fillMaxWidth()) {
        // 테이블 컬럼 헤더 (모터, 온도, 위치/회전량 각 구역 중앙 정렬)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.monitor_header_motor),
                style = MaterialTheme.typography.labelSmall,
                color = Mark7Palette.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // 모터와 피처(온도/전류) 사이 연한 수직 구분선
            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .height(12.dp)
                    .background(Mark7Palette.Line.copy(alpha = 0.5f)),
            )
            Text(
                text = stringResource(R.string.monitor_temp_label),
                style = MaterialTheme.typography.labelSmall,
                color = Mark7Palette.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.monitor_turn_label),
                style = MaterialTheme.typography.labelSmall,
                color = Mark7Palette.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(
            color = Mark7Palette.Line.copy(alpha = 0.6f),
            thickness = 0.8.dp,
        )

        // 모터 행 (자유도 개수에 맞게)
        for (i in 0 until dof.dof) {
            val temp = temps?.getOrNull(i)
            val turn = turns?.getOrNull(i)
            val motorName = stringResource(motorResList.getOrElse(i) { R.string.motor_f1 })

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 모터 식별자 (이름) - 중앙 정렬
                Text(
                    text = motorName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Mark7Palette.Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // 모터와 피처(온도/전류) 사이 연한 수직 구분선
                Box(
                    modifier = Modifier
                        .width(0.8.dp)
                        .height(18.dp)
                        .background(Mark7Palette.Line.copy(alpha = 0.5f)),
                )

                // 온도 (숫자 + 단위) - 중앙 정렬. 값 없으면 "-"
                MetricCell(value = temp?.let { "$it" }, unit = "°C")

                // 위치/회전량 (uint8, 단위 없음) - 중앙 정렬. 값 없으면 "-"
                MetricCell(value = turn?.let { "$it" }, unit = "")
            }

            if (i < dof.dof - 1) {
                HorizontalDivider(
                    color = Mark7Palette.SurfaceAlt,
                    thickness = 0.8.dp,
                )
            }
        }
    }
}

/** 계측값 1칸(숫자 + 단위)을 가로 중앙에 배치. [value] 가 null 이면 단위 없이 "-" 만 표시. */
@Composable
private fun RowScope.MetricCell(value: String?, unit: String) {
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = value ?: PLACEHOLDER_DASH,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (value != null) Mark7Palette.Ink else Mark7Palette.InkMuted,
        )
        if (value != null && unit.isNotEmpty()) {
            Spacer(Modifier.size(2.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = Mark7Palette.InkMuted,
                modifier = Modifier.padding(bottom = 1.5.dp),
            )
        }
    }
}

private const val CURSOR_GAP = 4
private const val NO_SIGNAL_THRESHOLD = 0f
private const val MIN_DISPLAY_RANGE = 60f

/**
 * EMG 채널별 파형 트랙
 * mandro-dynamic-gesture의 WaveformScreen(ChannelRow 및 drawWaveform) 스타일 적용:
 * - 어두운 배경(DarkSurf: #1A2131) 및 중앙 수평 기준선(DarkBorder: #2E384D)
 * - 2~98 백분위수 기반 자동 스케일링 (스파이크 및 미세 노이즈 왜곡 방지)
 * - 오실로스코프 스위프 커서 수직선 및 링버퍼 분할 드로우 (과거 구간 / 최신 구간)
 * - CH 1 빨강(#E44444), CH 2 파랑(#446CE4) 파형 렌더링
 */
@Composable
private fun EmgChannelRow(
    channelName: String,
    value: Int?,
    buffer: FloatArray?,
    writePtr: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val noSignalText = stringResource(R.string.emg_no_signal)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 좌측 채널 라벨 및 현재 계측 수치 (중앙 기준선 및 가로 중앙 정렬)
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channelName,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                text = value?.toString() ?: PLACEHOLDER_DASH,
                style = TextStyle(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFB0B8C4),
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 15.dp),
            )
        }

        // 파형 Canvas (오실로스코프 스위프 커서 — mandro-dynamic-gesture drawWaveform)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF1A2131), RoundedCornerShape(6.dp)),
        ) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // ── 중앙 수평 기준선 (DarkBorder) ──────────────────────
            drawLine(
                color = Color(0xFF2E384D),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 0.8.dp.toPx(),
            )

            if (buffer == null || buffer.isEmpty()) {
                val measured = textMeasurer.measure(
                    text = noSignalText,
                    style = TextStyle(fontSize = 9.sp, color = Color(0xFF707A8F)),
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = w / 2f - measured.size.width / 2f,
                        y = midY - measured.size.height / 2f,
                    ),
                )
                return@Canvas
            }

            val n = buffer.size
            fun bx(idx: Int) = (idx.toFloat() / (n - 1).coerceAtLeast(1)) * w

            // ── 스케일 계산 (2~98 백분위수 기반 — 스파이크 한두 개에 안 흔들림) ──
            val sorted = buffer.copyOf().also { it.sort() }
            val p2 = sorted[(n * 0.02f).toInt().coerceIn(0, n - 1)]
            val p98 = sorted[(n * 0.98f).toInt().coerceIn(0, n - 1)]

            val hasSignal = (sorted[n - 1] - sorted[0]) > NO_SIGNAL_THRESHOLD
            val displayRange = maxOf(p98 - p2, MIN_DISPLAY_RANGE)
            val midOffset = (p98 + p2) / 2f
            val scale = (h * 0.45f) / (displayRange / 2f)

            fun by(v: Float) = (midY - (v - midOffset) * scale).coerceIn(2f, h - 2f)

            val lineColor = if (hasSignal) color else color.copy(alpha = 0.25f)

            // ── 세그먼트 드로우 헬퍼 ──────────────────────────────
            fun drawSegment(from: Int, to: Int) {
                if (to - from < 2) return
                val path = Path()
                path.moveTo(bx(from), by(buffer[from]))
                for (i in from + 1 until to) {
                    path.lineTo(bx(i), by(buffer[i]))
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            // ── 링버퍼 분할 드로우 (과거 구간 / 최신 구간) ─────────
            // Current (왼쪽, 최신): 0 ~ writePtr
            // Past    (오른쪽, 과거): writePtr+GAP ~ n-1
            val safePtr = writePtr.coerceIn(0, n)
            val pastStart = (safePtr + CURSOR_GAP).coerceAtMost(n)

            drawSegment(0, safePtr)
            drawSegment(pastStart, n)

            // ── 커서 수직선 (스위프 커서) ───────────────────────────
            val cursorX = bx(safePtr)
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(cursorX, 0f),
                end = Offset(cursorX, h),
                strokeWidth = 1.2.dp.toPx(),
            )

            // ── 신호 없음 안내 문구 ───────────────────────────────
            if (!hasSignal) {
                val measured = textMeasurer.measure(
                    text = noSignalText,
                    style = TextStyle(fontSize = 9.sp, color = Color(0xFF707A8F)),
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = w / 2f - measured.size.width / 2f,
                        y = midY - measured.size.height / 2f,
                    ),
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MonitorPreview() {
    val hist = List(60) { k ->
        val ch1 = (sin(k * 0.25) * 60 + 128 + (k % 7) * 6).toInt().coerceIn(0, 255)
        val ch2 = (cos(k * 0.2) * 70 + 120 + (k % 5) * 8).toInt().coerceIn(0, 255)
        HandStatus(
            dof = 6,
            motorTemp = intArrayOf(38, 41, 46, 43, 62, 39),
            motorTurn = intArrayOf(18, 92, 140, 138, 130, 40),
            emg = intArrayOf(ch1, ch2),
            voltage = 200,
            checksumOk = true,
        )
    }
    val dummyBuffers = Array(2) { ch ->
        FloatArray(120) { i ->
            if (ch == 0) (sin(i * 0.18) * 140 + 512).toFloat()
            else (cos(i * 0.14) * 160 + 480).toFloat()
        }
    }
    Mark7Theme {
        MonitorContent(
            ui = MonitorUiState(connected = true, status = hist.last(), writePtr = 55),
            emgBuffers = dummyBuffers,
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800, name = "STATUS 미수신 (플레이스홀더)")
@Composable
private fun MonitorWaitingPreview() {
    Mark7Theme {
        MonitorContent(
            ui = MonitorUiState(connected = true, status = null),
            emgBuffers = Array(2) { FloatArray(0) },
            onDisconnect = {},
        )
    }
}
