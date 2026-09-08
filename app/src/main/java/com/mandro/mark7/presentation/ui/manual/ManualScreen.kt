package com.mandro.mark7.presentation.ui.manual

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.CmdDir
import com.mandro.mark7.domain.model.HandDof
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.domain.model.ManualPreset
import com.mandro.mark7.presentation.components.Mark7Slider
import com.mandro.mark7.presentation.components.ResetConfirmDialog
import com.mandro.mark7.presentation.components.SliderInterval
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
                    else -> ""
                }
                if (msg.isNotEmpty()) showFastToast(msg)
            },
            onExecutePreset = { preset ->
                val executed = viewModel.executePreset(preset)
                val name = getPresetDisplayName(preset, context)
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
                val name = targetPreset?.let { getPresetDisplayName(it, context) } ?: ""
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 상단 타이틀
        Text(
            text = stringResource(R.string.manual_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Mark7Palette.Ink,
        )

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
                        graspHi -> Mark7Palette.Grasp
                        !graspEnabled -> Mark7Palette.SurfaceAlt.copy(alpha = 0.45f)
                        else -> Mark7Palette.SurfaceAlt
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
                        graspHi -> Color.White
                        !graspEnabled -> Mark7Palette.InkMuted.copy(alpha = 0.4f)
                        else -> Mark7Palette.Ink
                    },
                    animationSpec = tween(80),
                    label = "graspText",
                )

                val releaseBgColor by animateColorAsState(
                    targetValue = when {
                        releaseHi -> Mark7Palette.Release
                        !releaseEnabled -> Mark7Palette.SurfaceAlt.copy(alpha = 0.45f)
                        else -> Mark7Palette.SurfaceAlt
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
                        releaseHi -> Color.White
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
                                    displayName = getPresetDisplayName(preset, context),
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
                        displayName = getPresetDisplayName(draggedPreset, context),
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
            displayName = getPresetDisplayName(preset, context),
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
        val name = getPresetDisplayName(preset, context)
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

}


/** 카드 헤더 우측의 작은 알약형 버튼 (모두 / 해제 / 추가 / 수정 …). 배경 틴트 + 넉넉한 패딩. */
@Composable
private fun HeaderPillButton(
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

/** 초컴팩트 카드 래퍼 (영역 터치 시 해당 위치로 즉시 스크롤 이동 지원) */
@Composable
private fun CompactCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Mark7Palette.Surface,
        border = BorderStroke(1.dp, Mark7Palette.Line.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title != null || trailing != null) {
                Row(
                    // trailing 버튼(알약형)이 조건부로 사라져도 헤더 높이가 줄지 않게 고정
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Mark7Palette.Ink,
                        )
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/**
 * 세부 조절 아코디언 카드. Settings 탭의 '전문가 세부 피팅'(AdvancedAccordionCard)과 동일한
 * 레이아웃 패턴 — 기본 접힘, 헤더 1클릭으로 펼침. 펼치면 슬라이더 아래에 [기본값][적용]
 * 버튼을 나란히 두며, 두 슬라이더는 로컬 draft 로만 편집되고 '적용'을 눌러야 실제 반영된다
 * (다음 쥐기/펴기 CMD 부터). 버튼 스타일은 Settings 탭 하단 바(StickyBottomBar)와 동일.
 */
@Composable
private fun FineTuningAccordionCard(
    ui: ManualUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onApply: (currentMa: Int, speedRaw: Int) -> Unit,
    onResetDefault: () -> Unit,
    defaultCurrent: Int,
    defaultSpeed: Int,
    modifier: Modifier = Modifier,
) {
    var draftCurrent by remember(ui.currentMa) { mutableIntStateOf(ui.currentMa) }
    var draftSpeed by remember(ui.speedRaw) { mutableIntStateOf(ui.speedRaw) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Mark7Palette.Surface,
        border = BorderStroke(1.dp, Mark7Palette.Line),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 헤더 (클릭 시 펼침/접힘 토글)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.manual_step3_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.settings_collapse else R.string.settings_expand,
                        ),
                        tint = Mark7Palette.InkMuted,
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Mark7Palette.Line)
                Spacer(Modifier.height(12.dp))

                // 쥐는 힘 (모터 전류 한계 mA)
                Mark7Slider(
                    leadingLabel = stringResource(R.string.manual_current_label),
                    leadingWidth = 54.dp,
                    value = draftCurrent.toFloat().coerceIn(600f, 1500f),
                    onValueChange = { draftCurrent = it.roundToInt() },
                    valueRange = 600f..1500f,
                    ticks = listOf(900f, 1200f),
                    minLabel = "600 mA",
                    maxLabel = "1500 mA",
                    formatValue = { "${it.roundToInt()}" },
                    intervals = listOf(
                        SliderInterval(
                            label = stringResource(R.string.settings_preset_light),
                            isActive = draftCurrent < 900,
                            onClick = { draftCurrent = 800 },
                        ),
                        SliderInterval(
                            label = stringResource(R.string.settings_preset_normal),
                            isActive = draftCurrent in 900..1200,
                            onClick = { draftCurrent = 1000 },
                        ),
                        SliderInterval(
                            label = stringResource(R.string.settings_preset_strong),
                            isActive = draftCurrent > 1200,
                            onClick = { draftCurrent = 1300 },
                        ),
                    ),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 동작 속도
                Mark7Slider(
                    leadingLabel = stringResource(R.string.manual_speed_label),
                    leadingWidth = 54.dp,
                    value = draftSpeed.toFloat().coerceIn(2000f, 51000f),
                    onValueChange = { draftSpeed = it.roundToInt() },
                    valueRange = 2000f..51000f,
                    ticks = listOf(18000f, 36000f),
                    minLabel = "2,000",
                    maxLabel = "51,000",
                    formatValue = { String.format("%,d", it.roundToInt()) },
                    intervals = listOf(
                        SliderInterval(
                            label = stringResource(R.string.manual_speed_slow),
                            isActive = draftSpeed < 18000,
                            onClick = { draftSpeed = 10000 },
                        ),
                        SliderInterval(
                            label = stringResource(R.string.settings_preset_normal),
                            isActive = draftSpeed in 18000..36000,
                            onClick = { draftSpeed = 20000 },
                        ),
                        SliderInterval(
                            label = stringResource(R.string.manual_speed_fast),
                            isActive = draftSpeed > 36000,
                            onClick = { draftSpeed = 40000 },
                        ),
                    ),
                )

                Spacer(Modifier.height(14.dp))

                // [기본값] [적용] — Settings 탭 하단 바(StickyBottomBar)와 동일 스타일
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.manual_btn_default),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Button(
                        onClick = { onApply(draftCurrent, draftSpeed) },
                        colors = ButtonDefaults.buttonColors(containerColor = Mark7Palette.Accent),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.manual_btn_apply),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        ResetConfirmDialog(
            onConfirm = {
                // 슬라이더 draft 와 실제 적용값을 모두 시스템 기본값으로 되돌린다.
                // (draft 만 만졌거나 이미 기본값이면 ui 값이 안 바뀌어 draft 가 안 따라오던 문제 수정)
                draftCurrent = defaultCurrent
                draftSpeed = defaultSpeed
                onResetDefault()
            },
            onDismiss = { showResetConfirm = false },
        )
    }
}

private data class GapResult(val gapIndex: Int, val rowIndex: Int)

/**
 * 포인터 좌표와 측정된 칩 경계로 타겟 갭 인덱스(0..presets.size)와 행 번호를 계산한다.
 * [presets] 는 전체 목록, [itemBounds] 는 컨테이너 로컬 좌표계의 칩 Rect.
 */
private fun computeGapAndRow(
    pointer: Offset,
    itemBounds: Map<String, Rect>,
    presets: List<ManualPreset>,
): GapResult {
    if (presets.isEmpty()) return GapResult(0, 0)

    val rows = presets.chunked(3)

    // 포인터 Y 가 속한 행 판단 (범위 밖이면 위→첫 행, 아래→마지막 행)
    var rowIndex = rows.indexOfFirst { row ->
        val tops = row.mapNotNull { itemBounds[it.id]?.top }
        val bottoms = row.mapNotNull { itemBounds[it.id]?.bottom }
        tops.isNotEmpty() && pointer.y >= tops.min() - 8f && pointer.y <= bottoms.max() + 8f
    }
    if (rowIndex < 0) {
        val firstTop = rows.first().mapNotNull { itemBounds[it.id]?.top }.minOrNull() ?: 0f
        rowIndex = if (pointer.y < firstTop) 0 else rows.lastIndex
    }

    val row = rows[rowIndex]
    val rowStartGlobal = rowIndex * 3

    // 해당 행 내에서 좌->우로 갭 판단 (칩 중심 X 기준)
    for ((colIndex, preset) in row.withIndex()) {
        val r = itemBounds[preset.id] ?: continue
        if (pointer.x < r.center.x) {
            return GapResult(rowStartGlobal + colIndex, rowIndex)
        }
    }
    return GapResult(rowStartGlobal + row.size, rowIndex)
}

/** 3열 그리드용 컴팩트 프리셋 칩 */
@Composable
private fun CompactPresetChip(
    preset: ManualPreset,
    displayName: String,
    isSelected: Boolean = false,
    isBeingDragged: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isRelease = preset.direction == CmdDir.RELEASE
    val accentColor = if (isRelease) Mark7Palette.Release else Mark7Palette.Grasp
    val softColor = if (isRelease) Mark7Palette.ReleaseSoft else Mark7Palette.GraspSoft

    val containerColor = when {
        isBeingDragged -> Mark7Palette.Surface
        isSelected -> softColor
        else -> Mark7Palette.Surface
    }
    val borderColor = when {
        isBeingDragged -> Color(0xFF1E88E5)
        isSelected -> accentColor
        else -> Mark7Palette.Line
    }

    Surface(
        modifier = modifier
            .shadow(
                elevation = if (isBeingDragged) 14.dp else if (isSelected) 1.dp else 0.dp,
                shape = RoundedCornerShape(8.dp),
            )
            .clip(RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(if (isBeingDragged || isSelected) 2.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // docs/img_5.png 형태: 사진이 셀을 크게 채우고(weight), 이름은 그 아래 한 줄.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Mark7Palette.SurfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                if (preset.imageUri != null) {
                    PresetPhoto(
                        imageUri = preset.imageUri,
                        biasX = preset.imageBiasX,
                        biasY = preset.imageBiasY,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(text = preset.emoji, fontSize = 24.sp)
                }
            }
            Text(
                text = displayName,
                fontSize = 12.sp,
                fontWeight = if (isSelected || isBeingDragged) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isBeingDragged) Color(0xFF1E88E5) else if (isSelected) accentColor else Mark7Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 사진 확대 배율 상한. */
/** 프리셋 사진 프레임의 가로:세로 비율 (칩의 사진 영역과 동일). */
private const val PRESET_PHOTO_ASPECT = 1.6f

/**
 * 프리셋 사진을 프레임에 꽉 채워 자르고([ContentScale.Crop]) [biasX]·[biasY] 로
 * 어느 부분이 보일지 정한다. 기본값(0f, 0f)이면 가운데 = 예전과 동일.
 */
@Composable
private fun PresetPhoto(
    imageUri: String,
    biasX: Float,
    biasY: Float,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = imageUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(biasX.coerceIn(-1f, 1f), biasY.coerceIn(-1f, 1f)),
        modifier = modifier,
    )
}

/**
 * 칩과 같은 크기·비율의 고정 프레임. 확대/축소 없이 드래그로 "어느 부분이 잘려 보일지"만 바꾼다.
 * 사진이 프레임보다 넘치는 축(세로로 길면 상하 / 가로로 길면 좌우)으로만 이동된다.
 */
@Composable
private fun PhotoAdjuster(
    imageUri: String,
    biasX: Float,
    biasY: Float,
    onChange: (biasX: Float, biasY: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cur = rememberUpdatedState(biasX to biasY) // stale capture 방지
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Mark7Palette.SurfaceAlt)
            .border(1.dp, Mark7Palette.Line, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val (x0, y0) = cur.value
                    val vw = size.width.toFloat().coerceAtLeast(1f)
                    val vh = size.height.toFloat().coerceAtLeast(1f)
                    onChange(
                        (x0 - drag.x / vw * 2f).coerceIn(-1f, 1f),
                        (y0 - drag.y / vh * 2f).coerceIn(-1f, 1f),
                    )
                }
            },
    ) {
        PresetPhoto(imageUri, biasX, biasY, Modifier.fillMaxSize())
    }
}

/** 프리셋 사진 선택 (갤러리에서 고른 뒤 내부 저장소로 복사). 사진이 없으면 이모지로 표시. */
@Composable
private fun PresetImagePicker(
    imageUri: String?,
    biasX: Float,
    biasY: Float,
    onImagePicked: (String?) -> Unit,
    onBiasChange: (biasX: Float, biasY: Float) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            onImagePicked(PresetImageStore.persist(context, uri))
            onBiasChange(0f, 0f) // 새 사진은 위치 초기화
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.manual_preset_image_label),
            style = MaterialTheme.typography.labelMedium,
            color = Mark7Palette.InkMuted,
        )
        if (imageUri != null) {
            Box {
                PhotoAdjuster(
                    imageUri = imageUri,
                    biasX = biasX,
                    biasY = biasY,
                    onChange = onBiasChange,
                    modifier = Modifier
                        .width(132.dp)
                        .aspectRatio(PRESET_PHOTO_ASPECT),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Mark7Palette.Danger)
                        .clickable { onImagePicked(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.manual_preset_image_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Mark7Palette.InkMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.manual_preset_image_reset),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Mark7Palette.InkMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onBiasChange(0f, 0f) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    Text(
                        text = stringResource(R.string.manual_preset_image_change),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Mark7Palette.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { launcher.launch("image/*") }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { launcher.launch("image/*") },
                shape = RoundedCornerShape(10.dp),
                color = Mark7Palette.SurfaceAlt,
                border = BorderStroke(1.dp, Mark7Palette.Line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.manual_preset_image_add),
                        fontSize = 12.sp,
                        color = Mark7Palette.InkMuted,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** 다이얼로그용 추천 손가락 제스처 이모지 목록 (손가락 펼침 조합 및 집기(pick)) */
private val RECOMMENDED_PRESET_EMOJIS = listOf(
    "✊", // 주먹
    "☝️", // 포인팅 (검지)
    "👆", // 손등 포인팅 위
    "👉", // 포인팅 오른쪽
    "👈", // 포인팅 왼쪽
    "🫵", // 포인팅 정면
    "👍", // 엄지 척 (4개 손가락 접음)
    "👎", // 엄지 아래
    "✌️", // 가위 / 브이 (검지, 중지)
    "🤘", // 락앤롤 (검지, 소지)
    "🤙", // 샤카 (엄지, 소지)
    "🤟", // 사랑해 (엄지, 검지, 소지)
    "🖖", // 발칸 (4개 손가락 펼침)
    "🖐", // 전체 펴기 (손가락 벌림)
    "✋", // 전체 펴기 (손가락 모음)
    "🤏", // 집기 (pick / pinch)
    "🖕", // 중지
)

/** 새 동작 만들기 모달 다이얼로그 */
@Composable
private fun CreatePresetDialog(
    dof: HandDof = HandDof.DEFAULT,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, imageUri: String?, imageBiasX: Float, imageBiasY: Float, fingers: List<Boolean>, dir: CmdDir) -> Unit,
) {
    // 새 동작은 이름·아이콘·동작 유형·손가락 모두 아무것도 선택되지 않은 상태로 시작한다.
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf<String?>(null) }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var imgBiasX by remember { mutableStateOf(0f) }
    var imgBiasY by remember { mutableStateOf(0f) }
    var direction by remember { mutableStateOf<CmdDir?>(null) }
    var fingers by remember { mutableStateOf(List(dof.dof) { false }) }
    val recommendedEmojis = RECOMMENDED_PRESET_EMOJIS
    val fingerResList: List<Int> = dof.motorShortRes

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mark7Palette.Surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.manual_preset_dialog_create_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.manual_preset_name_label)) },
                    placeholder = { Text(stringResource(R.string.manual_preset_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                PresetImagePicker(
                    imageUri = imageUri,
                    biasX = imgBiasX,
                    biasY = imgBiasY,
                    onImagePicked = { imageUri = it; if (it == null) { imgBiasX = 0f; imgBiasY = 0f } },
                    onBiasChange = { x, y -> imgBiasX = x; imgBiasY = y },
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.manual_preset_emoji_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mark7Palette.InkMuted,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(recommendedEmojis) { emoji ->
                            val selected = emoji == selectedEmoji
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedEmoji = emoji },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) Mark7Palette.Accent.copy(alpha = 0.15f) else Mark7Palette.SurfaceAlt,
                                border = BorderStroke(1.dp, if (selected) Mark7Palette.Accent else Color.Transparent),
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 20.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.manual_preset_dir_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mark7Palette.InkMuted,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { direction = CmdDir.GRASP },
                            shape = RoundedCornerShape(8.dp),
                            color = if (direction == CmdDir.GRASP) Mark7Palette.Grasp else Mark7Palette.SurfaceAlt,
                            border = if (direction == CmdDir.GRASP) BorderStroke(2.dp, Mark7Palette.GraspBorder) else BorderStroke(1.dp, Mark7Palette.Line),
                        ) {
                            Text(
                                text = "✊ " + stringResource(R.string.manual_grasp_title),
                                color = if (direction == CmdDir.GRASP) Color.White else Mark7Palette.Ink,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { direction = CmdDir.RELEASE },
                            shape = RoundedCornerShape(8.dp),
                            color = if (direction == CmdDir.RELEASE) Mark7Palette.Release else Mark7Palette.SurfaceAlt,
                            border = if (direction == CmdDir.RELEASE) BorderStroke(2.dp, Mark7Palette.ReleaseBorder) else BorderStroke(1.dp, Mark7Palette.Line),
                        ) {
                            Text(
                                text = "🖐 " + stringResource(R.string.manual_release_title),
                                color = if (direction == CmdDir.RELEASE) Color.White else Mark7Palette.Ink,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.manual_preset_fingers_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mark7Palette.InkMuted,
                    )
                    val dofCount = dof.dof
                    val ranges = when (dofCount) {
                        5 -> listOf(0..2, 3..4)
                        6 -> listOf(0..2, 3..5)
                        7 -> listOf(0..3, 4..6)
                        else -> (0 until dofCount).chunked(3).map { it.first()..it.last() }
                    }
                    ranges.forEach { range ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (i in range) {
                                val on = fingers.getOrElse(i) { false }
                                FingerMiniChip(
                                    label = "F${i + 1} " + stringResource(fingerResList[i]),
                                    selected = on,
                                    onClick = {
                                        val list = fingers.toMutableList()
                                        while (list.size <= i) list.add(false)
                                        list[i] = !list[i]
                                        fingers = list
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { direction?.let { onSave(name, selectedEmoji.orEmpty(), imageUri, imgBiasX, imgBiasY, fingers, it) } },
                enabled = name.isNotBlank() && fingers.any { it } && direction != null &&
                    (selectedEmoji != null || imageUri != null),
                colors = ButtonDefaults.buttonColors(containerColor = Mark7Palette.Accent),
            ) {
                Text(stringResource(R.string.manual_preset_btn_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.manual_cancel))
            }
        },
    )
}

/** 동작 수정 다이얼로그 (롱프레스 시). 삭제는 상단 "삭제" 버튼, 순서 변경은 그리드 드래그. */
@Composable
private fun EditPresetDialog(
    dof: HandDof = HandDof.DEFAULT,
    preset: ManualPreset,
    displayName: String,
    onDismiss: () -> Unit,
    onSave: (ManualPreset) -> Unit,
    onResetAllDefaults: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(displayName) }
    var selectedEmoji by remember { mutableStateOf(preset.emoji) }
    var imageUri by remember { mutableStateOf(preset.imageUri) }
    var imgBiasX by remember { mutableStateOf(preset.imageBiasX) }
    var imgBiasY by remember { mutableStateOf(preset.imageBiasY) }
    var direction by remember { mutableStateOf(preset.direction) }
    var fingers by remember { mutableStateOf(List(dof.dof) { i -> preset.fingers.getOrElse(i) { false } }) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val recommendedEmojis = RECOMMENDED_PRESET_EMOJIS
    val fingerResList: List<Int> = dof.motorShortRes

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Mark7Palette.Surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.manual_preset_btn_reset_all), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.manual_preset_reset_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetAllDefaults()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Mark7Palette.Accent),
                ) {
                    Text(stringResource(R.string.manual_confirm), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.manual_cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mark7Palette.Surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.manual_preset_dialog_edit_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 순서 변경은 그리드에서 드래그로만 처리한다 (기존 ◀▶ 이동 바 제거)

                // 이름 입력
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.manual_preset_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                PresetImagePicker(
                    imageUri = imageUri,
                    biasX = imgBiasX,
                    biasY = imgBiasY,
                    onImagePicked = { picked ->
                        if (picked != imageUri) PresetImageStore.deleteIfOwned(context, imageUri)
                        imageUri = picked
                        if (picked == null) { imgBiasX = 0f; imgBiasY = 0f }
                    },
                    onBiasChange = { x, y -> imgBiasX = x; imgBiasY = y },
                )

                // 이모지 선택
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.manual_preset_emoji_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mark7Palette.InkMuted,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(recommendedEmojis) { emoji ->
                            val selected = emoji == selectedEmoji
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedEmoji = emoji },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) Mark7Palette.Accent.copy(alpha = 0.15f) else Mark7Palette.SurfaceAlt,
                                border = BorderStroke(1.dp, if (selected) Mark7Palette.Accent else Color.Transparent),
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 20.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }

                // 동작 유형 선택 (쥐기 / 펴기)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.manual_preset_dir_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mark7Palette.InkMuted,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { direction = CmdDir.GRASP },
                            shape = RoundedCornerShape(8.dp),
                            color = if (direction == CmdDir.GRASP) Mark7Palette.Grasp else Mark7Palette.SurfaceAlt,
                            border = if (direction == CmdDir.GRASP) BorderStroke(2.dp, Mark7Palette.GraspBorder) else BorderStroke(1.dp, Mark7Palette.Line),
                        ) {
                            Text(
                                text = "✊ " + stringResource(R.string.manual_grasp_title),
                                color = if (direction == CmdDir.GRASP) Color.White else Mark7Palette.Ink,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { direction = CmdDir.RELEASE },
                            shape = RoundedCornerShape(8.dp),
                            color = if (direction == CmdDir.RELEASE) Mark7Palette.Release else Mark7Palette.SurfaceAlt,
                            border = if (direction == CmdDir.RELEASE) BorderStroke(2.dp, Mark7Palette.ReleaseBorder) else BorderStroke(1.dp, Mark7Palette.Line),
                        ) {
                            Text(
                                text = "🖐 " + stringResource(R.string.manual_release_title),
                                color = if (direction == CmdDir.RELEASE) Color.White else Mark7Palette.Ink,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                // 손가락 선택 (F1~F6)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.manual_preset_fingers_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mark7Palette.InkMuted,
                    )
                    val dofCount = dof.dof
                    val ranges = when (dofCount) {
                        5 -> listOf(0..2, 3..4)
                        6 -> listOf(0..2, 3..5)
                        7 -> listOf(0..3, 4..6)
                        else -> (0 until dofCount).chunked(3).map { it.first()..it.last() }
                    }
                    ranges.forEach { range ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (i in range) {
                                val on = fingers.getOrElse(i) { false }
                                FingerMiniChip(
                                    label = "F${i + 1} " + stringResource(fingerResList[i]),
                                    selected = on,
                                    onClick = {
                                        val list = fingers.toMutableList()
                                        while (list.size <= i) list.add(false)
                                        list[i] = !list[i]
                                        fingers = list
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = stringResource(R.string.manual_preset_btn_reset_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mark7Palette.InkMuted,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        preset.copy(
                            name = name.trim().ifEmpty { displayName },
                            emoji = selectedEmoji,
                            imageUri = imageUri,
                            imageBiasX = imgBiasX,
                            imageBiasY = imgBiasY,
                            fingers = fingers,
                            direction = direction,
                            isDefault = false,
                        ),
                    )
                    onDismiss()
                },
                enabled = name.isNotBlank() && fingers.any { it },
                colors = ButtonDefaults.buttonColors(containerColor = Mark7Palette.Accent),
            ) {
                Text(stringResource(R.string.manual_preset_btn_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.manual_cancel))
            }
        },
    )
}

/** 다이얼로그용 초소형 손가락 칩 */
@Composable
private fun FingerMiniChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) Mark7Palette.Accent else Mark7Palette.SurfaceAlt,
        border = BorderStroke(1.dp, if (selected) Mark7Palette.Accent else Mark7Palette.Line),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Mark7Palette.InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
        )
    }
}

private fun getPresetDisplayName(preset: ManualPreset, context: Context): String {
    if (!preset.isDefault) return preset.name
    return when (preset.id) {
        "fist" -> context.getString(R.string.manual_preset_fist)
        "point" -> context.getString(R.string.manual_preset_point)
        "pinch" -> context.getString(R.string.manual_preset_pinch)
        "peace" -> context.getString(R.string.manual_preset_peace)
        "tripod" -> context.getString(R.string.manual_preset_tripod)
        "open" -> context.getString(R.string.manual_preset_open)
        else -> preset.name
    }
}

@Composable
private fun FingerSelectChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 쥐기/펴기 버튼과 동일한 tween(150) 전환 — 액션 실행 시 손가락 칩과 방향 버튼이
    // 같은 속도로 점등·소등되어 시각적으로도 sync가 맞는다.
    val bgColor by animateColorAsState(
        targetValue = if (selected) Mark7Palette.Accent else Mark7Palette.SurfaceAlt,
        animationSpec = tween(150),
        label = "fingerBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Mark7Palette.AccentDim else Mark7Palette.Line,
        animationSpec = tween(150),
        label = "fingerBorder",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else Mark7Palette.InkMuted,
        animationSpec = tween(150),
        label = "fingerText",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

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
