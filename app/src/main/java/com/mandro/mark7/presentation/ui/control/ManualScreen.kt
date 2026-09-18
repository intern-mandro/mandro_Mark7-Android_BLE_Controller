package com.mandro.mark7.presentation.ui.control

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.hand.ManualPreset
import com.mandro.mark7.domain.model.hand.CmdDir
import com.mandro.mark7.presentation.components.ResetConfirmDialog
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
fun ManualScreen(
    viewModel: ManualViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()

    // 탭 재탭/재진입 시 항상 처음 상태(선택 없음)로. 화면 로컬 remember 는 key() 재구성으로
    // 이미 초기화되지만, 선택 상태는 ViewModel 에 있어 별도로 리셋해 줘야 한다.
    LaunchedEffect(Unit) { viewModel.resetTransientState() }

    var fastToastText by remember { mutableStateOf<String?>(null) }
    var toastJob by remember { mutableStateOf<Job?>(null) }

    fun showFastToast(msg: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        fastToastText = msg
        toastJob?.cancel()
        toastJob = coroutineScope.launch {
            delay(1400)
            fastToastText = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        ManualContent(
            ui = ui,
            presets = presets,
            onToggleFinger = viewModel::toggleFinger,
            onSetAll = viewModel::setAllFingers,
            onSetSpeed = viewModel::setSpeed,
            onSetCurrent = viewModel::setCurrent,
            onResetPowerSettings = viewModel::resetPowerSettings,
            onSend = { dir ->
                viewModel.send(dir)
                val summary = ui.select.mapIndexedNotNull { i, on -> if (on) "F${i + 1}" else null }.joinToString(" ")
                val msg = when (dir) {
                    CmdDir.GRASP -> context.getString(R.string.manual_feedback_grasp, summary)
                    CmdDir.RELEASE -> context.getString(R.string.manual_feedback_release, summary)
                    CmdDir.RESET_COUNTER -> context.getString(R.string.manual_feedback_reset_counter)
                    CmdDir.RESET_POWER -> context.getString(R.string.manual_feedback_reset_power)
                    else -> ""
                }
                if (msg.isNotEmpty()) showFastToast(msg)
            },
            onExecutePreset = { preset ->
                val executed = viewModel.executePreset(preset)
                val name = getPresetDisplayName(preset, ui.dof)
                if (executed) {
                    val dirLabel = context.getString(
                        if (preset.direction == CmdDir.RELEASE) R.string.manual_release_title
                        else R.string.manual_grasp_title,
                    )
                    showFastToast(context.getString(R.string.manual_preset_feedback, preset.emoji, name, dirLabel))
                } else {
                    showFastToast(context.getString(R.string.manual_preset_deselected, name))
                }
            },
            onCreatePreset = { name, emoji, imageUri, imgBiasX, imgBiasY, fingers, dir ->
                viewModel.createPreset(name, emoji, imageUri, imgBiasX, imgBiasY, fingers, dir)
            },
            onUpdatePreset = viewModel::updatePreset,
            onDeletePreset = { id ->
                val targetPreset = presets.firstOrNull { it.id == id }
                val name = targetPreset?.let { getPresetDisplayName(it, ui.dof) } ?: ""
                PresetImageStore.deleteIfOwned(context, targetPreset?.imageUri)
                viewModel.deletePreset(id)
                if (name.isNotEmpty()) {
                    showFastToast(context.getString(R.string.manual_preset_deleted, name))
                }
            },
            onMovePresetToIndex = viewModel::movePresetToIndex,
            onResetPresets = viewModel::resetPresetsToDefault,
        )

        // 0ms 지연 즉시 반응 인앱 토스트 (하단 플로팅 캡슐 HUD)
        AnimatedVisibility(
            visible = fastToastText != null,
            enter = fadeIn(tween(80)) + slideInVertically(tween(100)) { it / 2 },
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            fastToastText?.let { text ->
                val isRelease = text.contains("펴기") || text.contains("Release") || text.contains("🖐")
                val isGrasp = text.contains("쥐기") || text.contains("Grasp") || text.contains("✊")
                val accentColor = when {
                    isGrasp -> Mark7Palette.Grasp
                    isRelease -> Mark7Palette.Release
                    else -> Color(0x55FFFFFF)
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xF011161B),
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.5.dp, accentColor),
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManualContent(
    ui: ManualUiState,
    presets: List<ManualPreset>,
    onToggleFinger: (Int) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onSetSpeed: (Int) -> Unit,
    onSetCurrent: (Int) -> Unit,
    onResetPowerSettings: () -> Unit,
    onSend: (CmdDir) -> Unit,
    onExecutePreset: (ManualPreset) -> Unit,
    onCreatePreset: (name: String, emoji: String, imageUri: String?, imageBiasX: Float, imageBiasY: Float, fingers: List<Boolean>, dir: CmdDir) -> Unit,
    onUpdatePreset: (ManualPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onMovePresetToIndex: (Int, Int) -> Unit,
    onResetPresets: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val fingerResList = ui.dof.motorShortRes
    val count = ui.selectedCount
    val isExecuting = ui.lastCommandedDir != null

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<ManualPreset?>(null) }
    // 상단 "삭제" 버튼 → 확인 다이얼로그 대상
    var pendingDeletePreset by remember { mutableStateOf<ManualPreset?>(null) }
    // 카운터/전원 초기화 → 전송 전 확인 다이얼로그 대상
    var pendingReset by remember { mutableStateOf<CmdDir?>(null) }

    val currentOnExecutePreset by rememberUpdatedState(onExecutePreset)
    val currentIsExecuting by rememberUpdatedState(isExecuting)

    // 쥐기/펴기 버튼·손가락 선택의 일시적 점등은 ViewModel의 ui.lastCommandedDir / ui.select
    // 단일 상태로 관리한다. 별도 로컬 펄스 상태를 두지 않아 둘의 점등·소등 시점이 항상 일치한다.

    // 드래그 재정렬 상태.
    //  - localOrder: 드래그 중에는 이 로컬 리스트만 재정렬(→ animateItem 으로 칩들이 실제로 이동),
    //    손을 뗄 때 1회만 영구 저장. presets 가 갱신되면 리셋.
    //  - dragStartIndex: 드래그 시작 시점의 원본 인덱스(저장 커밋용).
    var localOrder by remember(presets) { mutableStateOf(presets) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    val isDragging = draggingIndex >= 0
    var pointerPos by remember { mutableStateOf(Offset.Zero) }
    var targetGap by remember { mutableIntStateOf(0) }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // 칩 Rect를 캐시하지 않고 살아있는 LayoutCoordinates만 보관한다. 컨테이너/칩의
    // onGloballyPositioned 호출 순서와 무관하게 필요 시점(드래그 시작·이동)에 즉석 계산 →
    // 예전 캐시 방식이 컨테이너 좌표가 늦게 세팅되면 itemBounds가 영영 비어 드래그가
    // 아예 시작되지 않던 문제 해결.
    val itemCoords = remember { mutableStateMapOf<String, LayoutCoordinates>() }

    /** 지정 프리셋 칩의 현재 경계(컨테이너 로컬 좌표). 아직 배치 전이면 null. */
    fun boundsOf(id: String): Rect? {
        val parent = containerCoordinates ?: return null
        val child = itemCoords[id] ?: return null
        if (!parent.isAttached || !child.isAttached) return null
        val topLeft = parent.localPositionOf(child, Offset.Zero)
        return Rect(topLeft, child.size.toSize())
    }

    /** 포인터 좌표 아래에 있는 칩 id (없으면 null). */
    fun presetIdAt(pos: Offset): String? =
        itemCoords.keys.firstOrNull { id -> boundsOf(id)?.contains(pos) == true }

    /** 좌표에 정확히 안 걸려도 가장 가까운 칩 id (드래그 시작 폴백). */
    fun nearestPresetId(pos: Offset): String? =
        itemCoords.keys
            .mapNotNull { id -> boundsOf(id)?.let { id to (it.center - pos).getDistance() } }
            .minByOrNull { it.second }
            ?.first

    val scrollState = rememberScrollState()
    var scrollContainerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var presetsTopInColumn by remember { mutableIntStateOf(0) }
    var fineTuningTopInColumn by remember { mutableIntStateOf(0) }
    var fineTuningExpanded by rememberSaveable { mutableStateOf(false) }

    // 토글을 펼치면 카드 상단으로 스크롤 (Settings 탭 '전문가 세부 피팅'과 동일 동작)
    LaunchedEffect(fineTuningExpanded) {
        if (fineTuningExpanded) {
            yield()
            if (fineTuningTopInColumn > 0) {
                scrollState.scrollTo(fineTuningTopInColumn.coerceAtMost(scrollState.maxValue))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { scrollContainerCoordinates = it }
            .verticalScroll(scrollState, enabled = !isDragging)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ─── 1. 손가락 직접 선택 & 쥐기/펴기 통합 카드 ───
        CompactCard(
            title = stringResource(R.string.manual_step1_title),
            onClick = {
                coroutineScope.launch { scrollState.scrollTo(0) }
            },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderPillButton(
                        stringResource(R.string.manual_all),
                        { if (!isExecuting) onSetAll(true) },
                    )
                    HeaderPillButton(
                        stringResource(R.string.manual_clear),
                        { if (!isExecuting) onSetAll(false) },
                        color = Mark7Palette.InkMuted,
                        strong = false,
                    )
                }
            },
        ) {
            val dofCount = ui.dof.dof
            val ranges = when (dofCount) {
                5 -> listOf(0..2, 3..4)
                6 -> listOf(0..2, 3..5)
                7 -> listOf(0..3, 4..6)
                else -> (0 until dofCount).chunked(3).map { it.first()..it.last() }
            }
            ranges.forEach { range ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (i in range) {
                        val on = ui.select.getOrElse(i) { false }
                        FingerSelectChip(
                            name = "F${i + 1} " + stringResource(fingerResList[i]),
                            selected = on,
                            onClick = { if (!isExecuting) onToggleFinger(i) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // 쥐기 / 펴기 버튼
            //  - 프리셋을 탭하면 그 방향(armedDir)만 활성되고 반대 버튼은 즉시 비활성 → 그 버튼을 눌러야 전송
            //  - 프리셋 없이 손가락만 고르면 두 버튼 다 활성
            //  - 쥐기/펴기 중 하나를 누른 순간, 선택받지 못한 반대편 버튼은 즉시 비활성화된다.
            //  - 전송 중(isExecuting)에는 중복 터치 방지
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val armed = ui.lastCommandedDir ?: ui.armedDir
                val hasFingers = count > 0

                // 선택된 방향(armed / 전송 중인 방향)만 활성화. 반대 방향은 즉시 비활성화.
                // 전송 중에는 중복 전송 방지를 위해 두 버튼 모두 enabled=false (눌린 쪽은 색상 하이라이트 유지).
                val graspEnabled = hasFingers && (armed == null || armed == CmdDir.GRASP) && !isExecuting
                val releaseEnabled = hasFingers && (armed == null || armed == CmdDir.RELEASE) && !isExecuting

                // 강조(초록/파랑)는 오직 실제로 눌러서 전송한 순간에만 잠깐.
                val graspHi = ui.lastCommandedDir == CmdDir.GRASP
                val releaseHi = ui.lastCommandedDir == CmdDir.RELEASE

                val graspBgColor by animateColorAsState(
                    targetValue = when {
                        graspHi -> Mark7Palette.GraspSoft
                        !graspEnabled -> Mark7Palette.SurfaceAlt.copy(alpha = 0.45f)
                        else -> Mark7Palette.Surface
                    },
                    animationSpec = tween(80),
                    label = "graspBg",
                )
                val graspBorderColor by animateColorAsState(
                    targetValue = when {
                        graspHi -> Mark7Palette.GraspBorder
                        !graspEnabled -> Mark7Palette.Line.copy(alpha = 0.35f)
                        else -> Mark7Palette.Line
                    },
                    animationSpec = tween(80),
                    label = "graspBorder",
                )
                val graspTextColor by animateColorAsState(
                    targetValue = when {
                        graspHi -> Mark7Palette.Grasp
                        !graspEnabled -> Mark7Palette.InkMuted.copy(alpha = 0.4f)
                        else -> Mark7Palette.Ink
                    },
                    animationSpec = tween(80),
                    label = "graspText",
                )

                val releaseBgColor by animateColorAsState(
                    targetValue = when {
                        releaseHi -> Mark7Palette.ReleaseSoft
                        !releaseEnabled -> Mark7Palette.SurfaceAlt.copy(alpha = 0.45f)
                        else -> Mark7Palette.Surface
                    },
                    animationSpec = tween(80),
                    label = "releaseBg",
                )
                val releaseBorderColor by animateColorAsState(
                    targetValue = when {
                        releaseHi -> Mark7Palette.ReleaseBorder
                        !releaseEnabled -> Mark7Palette.Line.copy(alpha = 0.35f)
                        else -> Mark7Palette.Line
                    },
                    animationSpec = tween(80),
                    label = "releaseBorder",
                )
                val releaseTextColor by animateColorAsState(
                    targetValue = when {
                        releaseHi -> Mark7Palette.ReleaseTextStrong
                        !releaseEnabled -> Mark7Palette.InkMuted.copy(alpha = 0.4f)
                        else -> Mark7Palette.Ink
                    },
                    animationSpec = tween(80),
                    label = "releaseText",
                )

                // 쥐기 (GRASP) 버튼
                Surface(
                    onClick = { onSend(CmdDir.GRASP) },
                    enabled = graspEnabled,
                    shape = RoundedCornerShape(8.dp),
                    color = graspBgColor,
                    border = BorderStroke(if (graspHi) 2.dp else 1.dp, graspBorderColor),
                    shadowElevation = if (graspHi) 3.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.manual_grasp_btn),
                            color = graspTextColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // 펴기 (RELEASE) 버튼
                Surface(
                    onClick = { onSend(CmdDir.RELEASE) },
                    enabled = releaseEnabled,
                    shape = RoundedCornerShape(8.dp),
                    color = releaseBgColor,
                    border = BorderStroke(if (releaseHi) 2.dp else 1.dp, releaseBorderColor),
                    shadowElevation = if (releaseHi) 3.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.manual_release_btn),
                            color = releaseTextColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // ─── 1. 동작 프리셋 (3열 그리드, 롱프레스 후 드래그로 순서 변경) ───
        CompactCard(
            modifier = Modifier.onGloballyPositioned { coords ->
                val container = scrollContainerCoordinates
                if (container != null && container.isAttached && coords.isAttached) {
                    val pos = container.localPositionOf(coords, Offset.Zero)
                    presetsTopInColumn = (scrollState.value + pos.y).roundToInt()
                }
            },
            title = stringResource(R.string.manual_presets_title),
            onClick = {
                coroutineScope.launch {
                    val target = if (presetsTopInColumn > 0) presetsTopInColumn else 250
                    scrollState.scrollTo(target)
                }
            },
            trailing = {
                val selectedPreset = ui.lastExecutedPresetId?.let { sid -> presets.firstOrNull { it.id == sid } }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedPreset != null) {
                        // 프리셋 선택 중 — "동작 추가" 대신 수정 / 삭제 버튼
                        HeaderPillButton(
                            stringResource(R.string.manual_preset_btn_edit),
                            { editingPreset = selectedPreset },
                        )
                        HeaderPillButton(
                            stringResource(R.string.manual_preset_btn_delete),
                            { pendingDeletePreset = selectedPreset },
                            color = Mark7Palette.Danger,
                        )
                    } else {
                        // 삭제된 기본 동작이 있을 때만 노출되는 복원 버튼 (기존 동작은 유지)
                        val hasMissingDefaults = ManualPreset.DEFAULT_PRESETS.any { d -> presets.none { it.id == d.id } }
                        if (hasMissingDefaults) {
                            HeaderPillButton(
                                stringResource(R.string.manual_preset_restore_missing),
                                { onResetPresets() },
                                color = Mark7Palette.InkMuted,
                                strong = false,
                            )
                        }
                        HeaderPillButton(
                            "+ " + stringResource(R.string.manual_preset_add),
                            { showCreateDialog = true },
                        )
                    }
                }
            },
        ) {
            val rowCount = ((localOrder.size + 2) / 3).coerceAtLeast(1)
            val gridHeight = (rowCount.coerceAtMost(4) * 98).dp   // 행당 92dp + 간격 6dp

            Box(modifier = Modifier.fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight)
                        .onGloballyPositioned { containerCoordinates = it }
                        // 드래그 재정렬: 표준 detectDragGesturesAfterLongPress. 그리드 자체에 붙여
                        // 상위 스크롤이 제스처를 가로채지 못하게 한다. 드래그 중에는 localOrder 만
                        // 재정렬(animateItem 으로 칩들이 실제로 움직임), 손을 뗄 때 1회만 저장.
                        .pointerInput(presets) {
                            var accumulated = Offset.Zero
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    accumulated = Offset.Zero
                                    val id = presetIdAt(offset) ?: nearestPresetId(offset)
                                    val idx = id?.let { pid -> localOrder.indexOfFirst { it.id == pid } } ?: -1
                                    if (idx >= 0) {
                                        dragStartIndex = idx
                                        draggingIndex = idx
                                        pointerPos = offset
                                        targetGap = idx
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggingIndex >= 0) {
                                        accumulated += dragAmount
                                        pointerPos = change.position
                                        val boundsSnapshot = localOrder
                                            .mapNotNull { p -> boundsOf(p.id)?.let { p.id to it } }
                                            .toMap()
                                        val res = computeGapAndRow(
                                            pointer = change.position,
                                            itemBounds = boundsSnapshot,
                                            presets = localOrder,
                                        )
                                        targetGap = res.gapIndex
                                        val dest = (if (targetGap > draggingIndex) targetGap - 1 else targetGap)
                                            .coerceIn(0, localOrder.lastIndex)
                                        if (dest != draggingIndex) {
                                            // 로컬 리스트만 재정렬 → 다른 칩들이 animateItem 으로 밀려남
                                            localOrder = localOrder.toMutableList().apply {
                                                add(dest, removeAt(draggingIndex))
                                            }
                                            draggingIndex = dest
                                            targetGap = dest
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val moved = accumulated.getDistance() > 12f
                                    when {
                                        !moved && dragStartIndex in localOrder.indices ->
                                            editingPreset = localOrder[dragStartIndex]
                                        draggingIndex >= 0 && draggingIndex != dragStartIndex -> {
                                            onMovePresetToIndex(dragStartIndex, draggingIndex)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                    draggingIndex = -1
                                    dragStartIndex = -1
                                },
                                onDragCancel = {
                                    localOrder = presets   // 되돌리기
                                    draggingIndex = -1
                                    dragStartIndex = -1
                                },
                            )
                        }
                        // 탭: 프리셋 준비 / 해제
                        .pointerInput(presets) {
                            detectTapGestures(
                                onTap = { pos ->
                                    if (currentIsExecuting) return@detectTapGestures
                                    val id = presetIdAt(pos) ?: nearestPresetId(pos) ?: return@detectTapGestures
                                    val preset = localOrder.firstOrNull { it.id == id } ?: return@detectTapGestures
                                    currentOnExecutePreset(preset)
                                },
                            )
                        },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    userScrollEnabled = !isDragging,
                ) {
                    itemsIndexed(localOrder, key = { _, p -> p.id }) { index, preset ->
                        Box(
                            modifier = Modifier
                                .animateItem()   // 순서가 바뀌면 칩이 새 위치로 부드럽게 이동
                                .height(92.dp)
                                .onGloballyPositioned { coords -> itemCoords[preset.id] = coords },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isDragging && draggingIndex == index) {
                                // 드래그 중인 칩의 자리 — 연한 플레이스홀더 (플로팅 칩이 손가락을 따라감)
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Mark7Palette.SurfaceAlt.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, Mark7Palette.Line.copy(alpha = 0.6f)),
                                ) {}
                            } else {
                                CompactPresetChip(
                                    preset = preset,
                                    dof = ui.dof,
                                    displayName = getPresetDisplayName(preset, ui.dof),
                                    isSelected = ui.lastExecutedPresetId == preset.id,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

                // 드래그 중 손가락을 따라다니는 플로팅 칩
                if (isDragging && draggingIndex in localOrder.indices) {
                    val draggedPreset = localOrder[draggingIndex]
                    val bounds = boundsOf(draggedPreset.id)
                    val chipWidthDp = with(LocalDensity.current) { (bounds?.width ?: 280f).toDp() }
                    val chipHeightDp = with(LocalDensity.current) { (bounds?.height ?: 96f).toDp() }
                    val halfW = (bounds?.width ?: 280f) / 2f
                    val halfH = (bounds?.height ?: 96f) / 2f

                    CompactPresetChip(
                        preset = draggedPreset,
                        dof = ui.dof,
                        displayName = getPresetDisplayName(draggedPreset, ui.dof),
                        isSelected = true,
                        isBeingDragged = true,
                        modifier = Modifier
                            .size(chipWidthDp, chipHeightDp)
                            .graphicsLayer {
                                translationX = pointerPos.x - halfW
                                translationY = pointerPos.y - halfH
                                scaleX = 1.08f
                                scaleY = 1.08f
                                shadowElevation = 16f
                            },
                    )
                }
            }
        }

        // ─── 3. 세부 조절 (Settings 탭 '전문가 세부 피팅'과 동일한 토글 아코디언) ───
        FineTuningAccordionCard(
            ui = ui,
            expanded = fineTuningExpanded,
            onToggle = { fineTuningExpanded = !fineTuningExpanded },
            onApply = { currentMa, speedRaw ->
                onSetCurrent(currentMa)
                onSetSpeed(speedRaw)
                fineTuningExpanded = false
            },
            onResetDefault = onResetPowerSettings,
            defaultCurrent = ManualViewModel.DEFAULT_CURRENT_MA,
            defaultSpeed = ManualViewModel.DEFAULT_SPEED_RAW,
            modifier = Modifier.onGloballyPositioned { coords ->
                val container = scrollContainerCoordinates
                if (container != null && container.isAttached && coords.isAttached) {
                    val pos = container.localPositionOf(coords, Offset.Zero)
                    fineTuningTopInColumn = (scrollState.value + pos.y).roundToInt()
                }
            },
        )

        // 유지보수 — 카운터/전원 초기화 (버튼 형태)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { pendingReset = CmdDir.RESET_COUNTER },
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Mark7Palette.Danger),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Mark7Palette.Danger,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.manual_reset_counter),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            OutlinedButton(
                onClick = { pendingReset = CmdDir.RESET_POWER },
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Mark7Palette.Danger),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Mark7Palette.Danger,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.manual_reset_power),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

    }

    // ── 새 동작 만들기 다이얼로그 ──
    if (showCreateDialog) {
        CreatePresetDialog(
            dof = ui.dof,
            onDismiss = { showCreateDialog = false },
            onSave = { name, emoji, imageUri, imgBiasX, imgBiasY, fingers, dir ->
                onCreatePreset(name, emoji, imageUri, imgBiasX, imgBiasY, fingers, dir)
                showCreateDialog = false
            },
        )
    }

    // ── 동작 수정 / 삭제 다이얼로그 (롱프레스 시) ──
    editingPreset?.let { preset ->
        EditPresetDialog(
            dof = ui.dof,
            preset = preset,
            displayName = getPresetDisplayName(preset, ui.dof),
            onDismiss = { editingPreset = null },
            onSave = { updated ->
                onUpdatePreset(updated)
                editingPreset = null
            },
            onResetAllDefaults = {
                onResetPresets()
                editingPreset = null
            },
        )
    }

    // ── 동작 삭제 확인 다이얼로그 (상단 "삭제" 버튼) ──
    pendingDeletePreset?.let { preset ->
        val name = getPresetDisplayName(preset, ui.dof)
        AlertDialog(
            onDismissRequest = { pendingDeletePreset = null },
            containerColor = Mark7Palette.Surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.manual_preset_btn_delete), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.manual_preset_delete_confirm, name), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(preset.id)
                        pendingDeletePreset = null
                    },
                ) {
                    Text(stringResource(R.string.manual_preset_btn_delete), fontWeight = FontWeight.Bold, color = Mark7Palette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePreset = null }) {
                    Text(stringResource(R.string.manual_cancel))
                }
            },
        )
    }

    // ── 카운터/전원 초기화 전송 확인 다이얼로그 ──
    pendingReset?.let { dir ->
        ResetConfirmDialog(
            onConfirm = { onSend(dir) },
            onDismiss = { pendingReset = null },
            message = stringResource(
                if (dir == CmdDir.RESET_COUNTER) R.string.manual_reset_counter_confirm_msg
                else R.string.manual_reset_power_confirm_msg,
            ),
        )
    }

}


/** 카드 헤더 우측의 작은 알약형 버튼 (모두 / 해제 / 추가 / 수정 …). 배경 틴트 + 넉넉한 패딩. */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ManualPreview() {
    Mark7Theme {
        ManualContent(
            ui = ManualUiState(select = listOf(true, true, true, false, false, false)),
            presets = ManualPreset.DEFAULT_PRESETS,
            onToggleFinger = {},
            onSetAll = {},
            onSetSpeed = {},
            onSetCurrent = {},
            onResetPowerSettings = {},
            onSend = {},
            onExecutePreset = {},
            onCreatePreset = { _, _, _, _, _, _, _ -> },
            onUpdatePreset = {},
            onDeletePreset = {},
            onMovePresetToIndex = { _, _ -> },
            onResetPresets = {},
        )
    }
}
