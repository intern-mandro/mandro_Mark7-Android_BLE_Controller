@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mandro.mark7.presentation.ui.action

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mandro.mark7.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.Gesture
import com.mandro.mark7.domain.model.ModeFlowGraph
import com.mandro.mark7.domain.model.ModeInput
import com.mandro.mark7.domain.model.ModeState
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme

/**
 * 모드 탭 — `docs/mode_flow.png` 의 상태 전이를 "두 갈래 흐름"으로 단순화해 보여준다.
 *
 * 왼쪽에 시작(대기), 오른쪽으로 두 줄의 흐름: 위는 굽힘(F)으로 진입, 아래는 폄(E)으로
 * 진입. 흐름 안에서는 F 로 다음 칸, 어디서든 E 로 한 칸 복귀. 노드를 눌러 손 모양을
 * 지정하고 길게 눌러 해제한다. 상태 수·배선은 펌웨어 `transitionTable` 고정.
 */
@Composable
fun ModeFlowScreen(
    onPickGesture: (Int) -> Unit,
    viewModel: ActionViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SET 전송 결과는 화면 상단(앱바 바로 아래)에 배너로 띄운다 — 안드로이드 기본 토스트는
    // 하단 SET 버튼/네비바와 겹쳐 안 보였음.
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.pushResult.collect { ok ->
            banner = context.getString(if (ok) R.string.mode_send_done else R.string.mode_send_failed)
        }
    }
    LaunchedEffect(banner) {
        if (banner != null) {
            kotlinx.coroutines.delay(2200)
            banner = null
        }
    }

    var showClearAll by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        ModeFlowContent(
            mapping = ui.mapping,
            pushing = ui.pushing,
            onPickGesture = onPickGesture,
            onClearAll = { showClearAll = true },
            onPush = viewModel::pushConfig,
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = banner != null,
            enter = androidx.compose.animation.fadeIn() +
                androidx.compose.animation.slideInVertically { -it },
            exit = androidx.compose.animation.fadeOut() +
                androidx.compose.animation.slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 16.dp, end = 16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Mark7Palette.Ink,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = banner.orEmpty(),
                    color = Mark7Palette.Surface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            containerColor = Mark7Palette.Surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.mode_clear_all_title),
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                )
            },
            text = { Text(stringResource(R.string.mode_clear_all_msg), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showClearAll = false
                    viewModel.clearAllGestures()
                }) {
                    Text(stringResource(R.string.mode_clear_confirm), fontWeight = FontWeight.Bold, color = Mark7Palette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun ModeFlowContent(
    mapping: ActionMapping,
    pushing: Boolean,
    onPickGesture: (Int) -> Unit,
    onClearAll: () -> Unit,
    onPush: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ─── 모드 1 (상단) ───
        ModeHeader(
            title = stringResource(R.string.mode_1_title),
        )
        FlowDiagram(
            graph = ModeFlowGraph.MODE_1,
            mapping = mapping,
            onPickGesture = onPickGesture,
        )

        ModeSwitchIndicator()

        // ─── 모드 2 (하단, 같은 형태) ───
        ModeHeader(
            title = stringResource(R.string.mode_2_title),
        )
        FlowDiagram(
            graph = ModeFlowGraph.MODE_2,
            mapping = mapping,
            onPickGesture = onPickGesture,
        )

        Spacer(Modifier.height(4.dp))

        // [All Clear] [의수에 적용] — 다른 탭의 [기본값][적용] 버튼 줄과 동일 스타일
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onClearAll,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mark7Palette.Danger),
            ) {
                Text(
                    text = stringResource(R.string.mode_clear_all),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Mark7Palette.Danger,
                )
            }
            Button(
                onClick = onPush,
                enabled = !pushing,
                colors = ButtonDefaults.buttonColors(containerColor = Mark7Palette.Accent),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(if (pushing) R.string.common_sending else R.string.mode_apply_set),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ModeHeader(
    title: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Mark7Palette.Ink,
        )
    }
}

/** 두 모드 사이를 Close 입력으로 전환할 수 있음을 나타내는 시각적 표시기. */
@Composable
private fun ModeSwitchIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Mark7Palette.Line,
            thickness = 1.dp,
        )

        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "↕",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Mark7Palette.Accent,
            )
            Text(
                text = stringResource(R.string.mode_switch_by_close),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Mark7Palette.InkMuted,
            )
        }

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Mark7Palette.Line,
            thickness = 1.dp,
        )
    }
}

/** 대기에서 [entry] 입력으로 진입한 뒤 F 로 계속 이어지는 상태 id 목록. */
private fun trackFrom(graph: ModeFlowGraph, entry: ModeInput): List<Int> {
    val first = graph.transitions
        .firstOrNull { it.from == ModeFlowGraph.IDLE_STATE_ID && it.input == entry }?.to
        ?: return emptyList()
    val ids = mutableListOf(first)
    var cur = first
    while (true) {
        val next = graph.transitions
            .firstOrNull { it.from == cur && it.input == ModeInput.FLEXION }?.to ?: break
        if (next in ids || next == ModeFlowGraph.IDLE_STATE_ID) break
        ids += next
        cur = next
    }
    return ids
}

@Composable
private fun FlowDiagram(
    graph: ModeFlowGraph,
    mapping: ActionMapping,
    onPickGesture: (Int) -> Unit,
) {
    val idle = graph.state(ModeFlowGraph.IDLE_STATE_ID) ?: return
    val fTrack = trackFrom(graph, ModeInput.FLEXION)
    val eTrack = trackFrom(graph, ModeInput.EXTENSION)
    if (fTrack.size < 2 || eTrack.size < 2) return

    val sTop1 = graph.state(fTrack[0]) ?: return
    val sTop2 = graph.state(fTrack[1]) ?: return
    val sBottom1 = graph.state(eTrack[0]) ?: return
    val sBottom2 = graph.state(eTrack[1]) ?: return

    val density = LocalDensity.current

    // 다이어그램 내부만 글자 크기 설정(fontScale) 영향을 받지 않게 고정 — 어떤 기기·설정에서도
    // 라벨/캡션이 레이아웃을 깨지 않도록.
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 1f),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp) // 태블릿·가로모드에서 화살표가 과하게 늘어나지 않도록
                .clip(RoundedCornerShape(14.dp))
                .background(Mark7Palette.Surface)
                .border(
                    width = 1.dp,
                    color = Mark7Palette.Line,
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val totalW = maxWidth
                // 좁은 폰(~340dp 미만)에서만 노드·간격을 비례 축소해 잘림 방지. 그 이상은 1.0 (기존 그대로).
                val nodeScale = (totalW / 360.dp).coerceIn(0.78f, 1f)

                val nodeRadius = 36.dp * nodeScale
                val colWidth = 86.dp * nodeScale
                val x0 = 42.dp * nodeScale
                val x2 = totalW - x0
                // S1과 S2 사이의 간격을 안정적으로 확보하여 완만한 곡선 및 충분한 여백 제공
                val x1 = x0 + (x2 - x0) * 0.48f

                val yTop = 54.dp * nodeScale
                val yBtm = 180.dp * nodeScale
                val yMid = (yTop + yBtm) / 2f

                val trackGapDp = 7.dp

              Box(Modifier.fillMaxWidth().height(236.dp * nodeScale)) {
            // ─── 1. 화살표 및 연결선 캔버스 ───
            Canvas(Modifier.fillMaxSize()) {
                val x0Px = x0.toPx()
                val x1Px = x1.toPx()
                val x2Px = x2.toPx()
                val rPx = nodeRadius.toPx()

                val yTopPx = yTop.toPx()
                val yBtmPx = yBtm.toPx()
                val yMidPx = yMid.toPx()

                val sw = 2.dp.toPx()
                val headW = 7.dp.toPx().coerceAtMost((x1Px - x0Px) * 0.25f)
                val headH = 7.5.dp.toPx()
                // 화살표(화살촉 포함)와 노드 원 사이에 눈에 보이는 여백. 체인·브랜치 모든 끝에 동일 적용.
                val tipInset = 5.dp.toPx()
                val trackGap = trackGapDp.toPx()

                val x0Right = x0Px + rPx
                val x1Left = x1Px - rPx
                val x1Right = x1Px + rPx
                val x2Left = x2Px - rPx

                fun circleRightX(cx: Float, cy: Float, r: Float, y: Float): Float {
                    val dy = (y - cy).coerceIn(-r * 0.99f, r * 0.99f)
                    return cx + kotlin.math.sqrt(r * r - dy * dy)
                }

                fun circleLeftX(cx: Float, cy: Float, r: Float, y: Float): Float {
                    val dy = (y - cy).coerceIn(-r * 0.99f, r * 0.99f)
                    return cx - kotlin.math.sqrt(r * r - dy * dy)
                }

                // ─── 수평 화살표: 상단 (S2 ⇄ S3) ───
                val yTopFwd = yTopPx - trackGap // 위: 오른쪽 진행 (Flexion)
                val s2R_fwd = circleRightX(x1Px, yTopPx, rPx, yTopFwd) + tipInset
                val s3L_fwd = circleLeftX(x2Px, yTopPx, rPx, yTopFwd) - tipInset
                drawLine(
                    color = FlexionColor,
                    start = Offset(s2R_fwd, yTopFwd),
                    end = Offset(s3L_fwd - headW * 0.5f, yTopFwd),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
                drawArrowHead(tip = Offset(s3L_fwd, yTopFwd), headW = headW, headH = headH, color = FlexionColor, pointsRight = true)

                val yTopBwd = yTopPx + trackGap // 아래: 왼쪽 복귀 (Extension)
                val s2R_bwd = circleRightX(x1Px, yTopPx, rPx, yTopBwd) + tipInset
                val s3L_bwd = circleLeftX(x2Px, yTopPx, rPx, yTopBwd) - tipInset
                drawLine(
                    color = ExtensionColor,
                    start = Offset(s2R_bwd + headW * 0.5f, yTopBwd),
                    end = Offset(s3L_bwd, yTopBwd),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
                drawArrowHead(tip = Offset(s2R_bwd, yTopBwd), headW = headW, headH = headH, color = ExtensionColor, pointsRight = false)

                // ─── 수평 화살표: 하단 (S4 ⇄ S5: 상단과 동일하게 위: 진행 Flexion, 아래: 복귀 Extension) ───
                val yBtmFwd = yBtmPx - trackGap // 위: 오른쪽 진행 (Flexion)
                val s4R_fwd = circleRightX(x1Px, yBtmPx, rPx, yBtmFwd) + tipInset
                val s5L_fwd = circleLeftX(x2Px, yBtmPx, rPx, yBtmFwd) - tipInset
                drawLine(
                    color = FlexionColor,
                    start = Offset(s4R_fwd, yBtmFwd),
                    end = Offset(s5L_fwd - headW * 0.5f, yBtmFwd),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
                drawArrowHead(tip = Offset(s5L_fwd, yBtmFwd), headW = headW, headH = headH, color = FlexionColor, pointsRight = true)

                val yBtmBwd = yBtmPx + trackGap // 아래: 왼쪽 복귀 (Extension)
                val s4R_bwd = circleRightX(x1Px, yBtmPx, rPx, yBtmBwd) + tipInset
                val s5L_bwd = circleLeftX(x2Px, yBtmPx, rPx, yBtmBwd) - tipInset
                drawLine(
                    color = ExtensionColor,
                    start = Offset(s4R_bwd + headW * 0.5f, yBtmBwd),
                    end = Offset(s5L_bwd, yBtmBwd),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
                drawArrowHead(tip = Offset(s4R_bwd, yBtmBwd), headW = headW, headH = headH, color = ExtensionColor, pointsRight = false)

                // ─── 브랜치: IDLE ⇄ 첫 노드. 체인처럼 '직선' 진행/복귀 화살표를 중심선(두 원 중심을 잇는 선)
                //     에서 법선방향으로 ±trackGap 평행이동 → 두 선 사이 거리가 항상 2·trackGap 로 일정(체인과 동일).
                //     끝점은 원 둘레에서 tipInset 만큼 바깥 → 화살촉이 노드에 가려지지 않음.
                fun drawBranchPair(nodeCy: Float, fwdColor: Color, bwdColor: Color) {
                    val idleC = Offset(x0Px, yMidPx)
                    val nodeC = Offset(x1Px, nodeCy)
                    val dv = Offset(nodeC.x - idleC.x, nodeC.y - idleC.y)
                    val dl = dv.getDistance().coerceAtLeast(1e-3f)
                    val d = Offset(dv.x / dl, dv.y / dl)   // 단위 방향 IDLE→node
                    val perp = Offset(-d.y, d.x)
                    val reach = kotlin.math.sqrt((rPx * rPx - trackGap * trackGap).coerceAtLeast(1f)) + tipInset

                    fun seg(sign: Float): Pair<Offset, Offset> {
                        val ox = perp.x * trackGap * sign
                        val oy = perp.y * trackGap * sign
                        return Offset(idleC.x + ox + d.x * reach, idleC.y + oy + d.y * reach) to
                            Offset(nodeC.x + ox - d.x * reach, nodeC.y + oy - d.y * reach)
                    }

                    val (fA, fB) = seg(-1f) // 진행: IDLE → node (행 중심에 가까운 쪽 = 위)
                    drawLine(fwdColor, fA, Offset(fB.x - d.x * headW * 0.6f, fB.y - d.y * headW * 0.6f), sw, StrokeCap.Round)
                    drawArrowHeadDir(fB, d, headW, fwdColor)

                    val (bA, bB) = seg(1f) // 복귀: node → IDLE
                    drawLine(bwdColor, bB, Offset(bA.x + d.x * headW * 0.6f, bA.y + d.y * headW * 0.6f), sw, StrokeCap.Round)
                    drawArrowHeadDir(bA, Offset(-d.x, -d.y), headW, bwdColor)
                }

                drawBranchPair(yTopPx, FlexionColor, ExtensionColor)   // IDLE ⇄ S2
                drawBranchPair(yBtmPx, ExtensionColor, ExtensionColor) // IDLE ⇄ S4
            }

            // ─── 2. 텍스트 라벨: 각 라벨을 자기 화살표선 기준으로 개별 배치 ───
            val arrowStartX = x1 + nodeRadius
            val arrowEndX = x2 - nodeRadius
            val arrowSpan = (arrowEndX - arrowStartX).coerceAtLeast(0.dp)

            // 폰트 패딩 제거 → 텍스트 박스가 글자에 딱 맞아 여백 계산이 예측대로.
            val labelStyle = TextStyle(
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
            val labelClear = 5.dp   // 화살표선 ↔ 라벨 여백 (위·아래 동일)
            val labelH = 11.dp      // 8.5sp 라벨 대략 높이

            // 체인 라벨: 화살표선 = rowY ∓ trackGapDp (수평). arrowSpan 안에서 중앙 정렬 → 화살표 정중앙.
            @Composable
            fun ChainLabel(text: String, color: Color, rowY: Dp, above: Boolean) {
                val y = if (above) rowY - trackGapDp - labelClear - labelH else rowY + trackGapDp + labelClear
                Text(
                    text = text,
                    style = labelStyle,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(arrowSpan).offset(x = arrowStartX, y = y),
                )
            }
            ChainLabel("Flexion", FlexionColor, yTop, above = true)
            ChainLabel("Extension", ExtensionColor, yTop, above = false)
            ChainLabel("Flexion", FlexionColor, yBtm, above = true)
            ChainLabel("Extension", ExtensionColor, yBtm, above = false)

            // 브랜치 라벨: 해당 화살표(sign: 진행 -1 / 복귀 +1)의 '중점'에서 법선방향 바깥으로 띄우고,
            // 화살표 각도만큼 회전. 텍스트는 layout 트릭으로 0크기화해 중심을 정확히 그 점에 맞춘다.
            @Composable
            fun BranchLabel(text: String, color: Color, nodeRowY: Dp, sign: Float) {
                val dxv = (x1 - x0).value
                val dyv = (nodeRowY - yMid).value
                val dl = kotlin.math.hypot(dxv, dyv).coerceAtLeast(1e-3f)
                val ux = dxv / dl
                val uy = dyv / dl
                val nx = -uy // 법선
                val ny = ux
                val angle = Math.toDegrees(kotlin.math.atan2(dyv.toDouble(), dxv.toDouble())).toFloat()

                // 화살표 중점 = 중심선 중점 + 법선 * trackGap * sign
                val midX = (x0 + x1) / 2f + (nx * trackGapDp.value * sign).dp
                val midY = (yMid + nodeRowY) / 2f + (ny * trackGapDp.value * sign).dp
                // 라벨 중심 = 화살표 중점에서 바깥(법선*sign)으로 (여백 + 글자높이/2)
                val outDp = (labelClear + labelH * 0.5f).value
                val lcx = midX + (nx * outDp * sign).dp
                val lcy = midY + (ny * outDp * sign).dp

                Text(
                    text = text,
                    style = labelStyle,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints)
                            layout(0, 0) { p.place(-p.width / 2, -p.height / 2) }
                        }
                        .offset { IntOffset(lcx.roundToPx(), lcy.roundToPx()) }
                        .graphicsLayer { rotationZ = angle },
                )
            }
            // IDLE ⇄ S2: 진행(위) = Flexion, 복귀(아래) = Extension
            BranchLabel("Flexion", FlexionColor, yTop, sign = -1f)
            BranchLabel("Extension", ExtensionColor, yTop, sign = 1f)
            // IDLE ⇄ S4: 진행·복귀 둘 다 Extension
            BranchLabel("Extension", ExtensionColor, yBtm, sign = -1f)
            BranchLabel("Extension", ExtensionColor, yBtm, sign = 1f)

            // ─── 3. 원형 노드 (정확한 중심 좌표에 offset 배치) ───
            //     화면 폭에 따라 nodeScale 이 달라지므로, 실제 그려지는 원 지름(bubbleSize)·열 폭을
            //     Canvas 가 가정하는 nodeRadius 와 항상 똑같이 스케일해야 화살표-원 간격이 기기마다 동일하다.
            val halfCol = colWidth / 2f
            val bubbleSize = nodeRadius * 2f

            // S1 (IDLE)
            Box(
                Modifier.offset(x = x0 - halfCol, y = yMid - nodeRadius),
            ) {
                NodeColumn(idle, mapping, onPickGesture, bubbleSize, colWidth)
            }

            // S2 (상단 중앙)
            Box(
                Modifier.offset(x = x1 - halfCol, y = yTop - nodeRadius),
            ) {
                NodeColumn(sTop1, mapping, onPickGesture, bubbleSize, colWidth)
            }

            // S3 (상단 우측)
            Box(
                Modifier.offset(x = x2 - halfCol, y = yTop - nodeRadius),
            ) {
                NodeColumn(sTop2, mapping, onPickGesture, bubbleSize, colWidth)
            }

            // S4 (하단 중앙)
            Box(
                Modifier.offset(x = x1 - halfCol, y = yBtm - nodeRadius),
            ) {
                NodeColumn(sBottom1, mapping, onPickGesture, bubbleSize, colWidth)
            }

            // S5 (하단 우측)
            Box(
                Modifier.offset(x = x2 - halfCol, y = yBtm - nodeRadius),
            ) {
                NodeColumn(sBottom2, mapping, onPickGesture, bubbleSize, colWidth)
            }
              } // 내부 콘텐츠 Box
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    tip: Offset,
    headW: Float,
    headH: Float,
    color: Color,
    pointsRight: Boolean,
) {
    val path = Path().apply {
        if (pointsRight) {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - headW, tip.y - headH / 2f)
            lineTo(tip.x - headW, tip.y + headH / 2f)
            close()
        } else {
            moveTo(tip.x, tip.y)
            lineTo(tip.x + headW, tip.y - headH / 2f)
            lineTo(tip.x + headW, tip.y + headH / 2f)
            close()
        }
    }
    drawPath(path, color = color)
}

/** [dir] 방향(단위벡터 아니어도 됨)으로 향하는 삼각 화살촉. 대각선 화살표용. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHeadDir(
    tip: Offset,
    dir: Offset,
    size: Float,
    color: Color,
) {
    val dl = dir.getDistance()
    if (dl < 1e-3f) return
    val ux = dir.x / dl
    val uy = dir.y / dl
    val bx = tip.x - ux * size
    val by = tip.y - uy * size
    val half = size * 0.55f
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(bx + -uy * half, by + ux * half)
        lineTo(bx - -uy * half, by - ux * half)
        close()
    }
    drawPath(path, color = color)
}

/** 다이어그램 기준 노드 지름 (nodeScale = 1 일 때). Canvas 의 nodeRadis*2 와 반드시 일치해야 한다. */
private val NODE_SIZE = 72.dp

@Composable
private fun NodeColumn(
    state: ModeState,
    mapping: ActionMapping,
    onPickGesture: (Int) -> Unit,
    bubbleSize: Dp = NODE_SIZE,
    columnWidth: Dp = 86.dp,
) {
    val gesture = gestureFor(state, mapping)
    val assigned = state.fixed || mapping.gestureIdFor(state.id) != null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.width(columnWidth),
    ) {
        // 노드를 누르면(짧게/길게 상관없이) 무조건 액션 선택 화면으로. 예외 없음.
        NodeBubble(
            state = state,
            gesture = gesture,
            size = bubbleSize,
            onClick = if (state.fixed) null else ({ onPickGesture(state.id) }),
        )
        Text(
            text = if (state.fixed) stringResource(R.string.gesture_idle) else gestureLabel(gesture),
            fontSize = 9.5.sp,
            fontWeight = if (assigned) FontWeight.SemiBold else FontWeight.Normal,
            color = if (assigned) Mark7Palette.Ink else Mark7Palette.InkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NodeBubble(
    state: ModeState,
    gesture: Gesture?,
    onClick: (() -> Unit)?,
    size: Dp = NODE_SIZE,
) {
    val context = LocalContext.current
    val shape = CircleShape
    val photo = gesture
        ?.takeUnless { state.fixed }
        ?.let { GestureAssets.representativeForDir(context, it.assetDir) }

    val clickMod = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    val ring = if (state.fixed || photo != null) Mark7Palette.Accent else Mark7Palette.Line

    Box(
        Modifier.size(size).clip(shape)
            .background(if (state.fixed) Mark7Palette.AccentSoft else Mark7Palette.SurfaceAlt)
            .border(2.dp, ring, shape)
            .then(clickMod),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.fixed -> Text(
                "IDLE",
                fontSize = (size.value * 0.21f).sp,
                fontWeight = FontWeight.Bold,
                color = Mark7Palette.AccentDim,
            )

            photo != null -> AsyncImage(
                model = photo,
                contentDescription = gestureLabel(gesture),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )

            else -> Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Mark7Palette.InkMuted,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ModeFlowPreview() {
    Mark7Theme {
        ModeFlowContent(
            mapping = ActionMapping(gestureIdByState = mapOf(2 to "flexion", 4 to "close", 6 to "flexion", 8 to "close")),
            pushing = false,
            onPickGesture = {},
            onClearAll = {},
            onPush = {},
        )
    }
}
