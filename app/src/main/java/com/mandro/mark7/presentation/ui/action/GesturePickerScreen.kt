package com.mandro.mark7.presentation.ui.action

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.action.ActionMapping
import com.mandro.mark7.domain.model.action.ActionSlotPairs
import com.mandro.mark7.domain.model.action.Gesture
import com.mandro.mark7.presentation.components.ResetConfirmDialog
import com.mandro.mark7.domain.model.action.GestureCatalog
import com.mandro.mark7.domain.model.action.GestureCatalogs
import com.mandro.mark7.domain.model.hand.HandDof
import com.mandro.mark7.domain.repository.HandRepository
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GesturePickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: HandRepository,
) : ViewModel() {

    /** 어떤 앞 상태(S1/S3/S5/S7)에 손 모양을 지정하는 중인지. */
    val stateId: Int = savedStateHandle.get<String>("state")
        ?.toIntOrNull()
        ?.let { ActionSlotPairs.pairForState(it)?.primaryStateId }
        ?: 1

    /** 지금 의수 자유도의 손 모양 목록. 후보·액션 ID·Pair 규칙이 모두 여기서 온다. */
    val catalog: GestureCatalog = repo.actionMapping.value.catalog

    val options: List<Gesture> = catalog.selectableForState(stateId)

    // 진입 시점의 매핑 보관 (Send 안 누르고 뒤로가기 시 롤백용)
    private val initialMapping: ActionMapping = repo.actionMapping.value

    val initialSelectedId: String? = initialMapping.effectiveGestureIdFor(stateId)

    /**
     * 지정한 [gestureId]가 진입 시점의 원래 선택 상태와 동일한지 확인한다.
     * 이미 원래 선택되어 있던 항목을 다시 탭해 나갈 때 토스트 메시지를 생략하기 위해 사용된다.
     */
    fun isOriginalState(gestureId: String): Boolean {
        val leadId = catalog.slotGesturesForState(stateId, gestureId)?.lead?.id ?: return false
        return leadId == initialSelectedId
    }

    private val _selectedId = MutableStateFlow(repo.actionMapping.value.effectiveGestureIdFor(stateId))
    val selectedId = _selectedId.asStateFlow()

    private val _syncedId = MutableStateFlow(repo.syncedActionMapping.value.effectiveGestureIdFor(stateId))
    val syncedId = _syncedId.asStateFlow()

    private val _pushing = MutableStateFlow(false)
    val pushing = _pushing.asStateFlow()

    private val _pushResult = Channel<Boolean>(Channel.BUFFERED)
    val pushResult = _pushResult.receiveAsFlow()

    // Send 전송 시 또는 동일 항목 재클릭으로 모드 플로우 복귀 시 true
    private var isCommitted = false

    init {
        viewModelScope.launch {
            repo.actionMapping.collect { m ->
                _selectedId.value = m.effectiveGestureIdFor(stateId)
            }
        }
        viewModelScope.launch {
            repo.syncedActionMapping.collect { m ->
                _syncedId.value = m.effectiveGestureIdFor(stateId)
            }
        }
    }

    /**
     * 셀 탭: 새 손 모양이면 매핑을 저장하고 false 반환.
     * 이미 선택되어 있는 손 모양을 한 번 더 누르면 변경사항을 확정(commit)하고 true 를 반환하여 모드 플로우 화면으로 복귀한다.
     */
    fun select(gestureId: String): Boolean {
        val leadId = catalog.slotGesturesForState(stateId, gestureId)?.lead?.id ?: return false
        if (leadId == _selectedId.value) {
            isCommitted = true
            return true
        }
        _selectedId.value = leadId
        val m = repo.actionMapping.value
        viewModelScope.launch {
            repo.updateActionMapping(
                m.assignGesture(primaryStateId = stateId, gestureId = leadId),
            )
        }
        return false
    }

    /**
     * 뒤로가기(상단 버튼 또는 시스템 백) 처리:
     * Send 를 누르지 않았거나 재클릭으로 확정되지 않았다면, 진입 시점의 원래 매핑으로 복원한다.
     * @return 확정 상태(isCommitted) 여부. false 면 롤백되었음을 의미하므로 토스트를 띄우지 않는다.
     */
    fun onBack(): Boolean {
        if (!isCommitted) {
            viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
                repo.updateActionMapping(initialMapping)
            }
            return false
        }
        return true
    }

    /** 'Reset to Default' 버튼: 모든 상태의 손 모양 지정을 기본값으로 복원한다. */
    fun resetToDefault() = viewModelScope.launch {
        val m = repo.actionMapping.value
        repo.updateActionMapping(m.copy(gestureIdByState = m.catalog.defaultStateGestures))
    }

    /** 'Send' 버튼: 현재 설정을 MSET 프레임으로 의수에 전송하고 변경사항을 확정한다. */
    fun pushConfig() = viewModelScope.launch {
        isCommitted = true
        _pushing.value = true
        val result = repo.pushConfig()
        _pushing.value = false
        _pushResult.send(result.isSuccess)
    }
}

/**
 * 한 상태에 연결할 손 모양을 사진 그리드에서 고른다.
 * 하단에 모드 플로우 탭과 동일한 [All Clear] [Send] 버튼이 표시된다.
 */
@Composable
fun GesturePickerScreen(
    onDone: (notice: String?) -> Unit,
    viewModel: GesturePickerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val syncedId by viewModel.syncedId.collectAsStateWithLifecycle()
    val pushing by viewModel.pushing.collectAsStateWithLifecycle()
    val catalog = viewModel.catalog
    val options = viewModel.options.map { it to GestureAssets.imageFor(context, catalog, it) }
    // Pair 를 고르면 두 칸을 함께 하이라이트한다.
    val selectedIds = catalog.slotGesturesFor(selectedId)
        ?.let { setOfNotNull(it.lead.id, it.companion?.id) }
        .orEmpty()
    // 직전에 명령을 보냈던 값(의수에 현재 반영된 설정값)
    val syncedIds = catalog.slotGesturesFor(syncedId)
        ?.let { setOfNotNull(it.lead.id, it.companion?.id) }
        .orEmpty()

    LaunchedEffect(Unit) {
        viewModel.pushResult.collect { ok ->
            val msg = context.getString(if (ok) R.string.mode_send_done else R.string.mode_send_failed)
            onDone(msg)
        }
    }

    var showClearAll by remember { mutableStateOf(false) }
    var lastSelectedNotice by remember { mutableStateOf<String?>(null) }

    val handleBack: () -> Unit = {
        val committed = viewModel.onBack()
        onDone(if (committed) lastSelectedNotice else null)
    }

    // 시스템 뒤로가기
    BackHandler(onBack = handleBack)

    Box(Modifier.fillMaxSize()) {
        GesturePickerContent(
            options = options,
            selectedIds = selectedIds,
            syncedIds = syncedIds,
            pushing = pushing,
            onChoose = { id ->
                val gesture = catalog.byId(id)
                val name = gesture?.let { context.getString(it.displayNameRes) } ?: id
                val lastChar = name.trim().lastOrNull()
                val josa = if (lastChar != null && lastChar in '가'..'힣') {
                    if ((lastChar.code - 0xAC00) % 28 > 0) "이" else "가"
                } else {
                    "가"
                }
                val msg = if (context.resources.configuration.locales[0].language == "ko") {
                    "$name $josa 선택됐습니다."
                } else {
                    "$name selected."
                }
                lastSelectedNotice = msg

                if (viewModel.select(id)) {
                    val isOriginal = viewModel.isOriginalState(id)
                    onDone(if (isOriginal) null else msg)
                }
            },
            onClearAll = { showClearAll = true },
            onPush = viewModel::pushConfig,
            onBack = handleBack,
        )
    }

    if (showClearAll) {
        ResetConfirmDialog(
            title = stringResource(R.string.mode_clear_all_title),
            message = stringResource(R.string.mode_clear_all_msg),
            onConfirm = { viewModel.resetToDefault() },
            onDismiss = { showClearAll = false },
        )
    }
}

@Composable
private fun GesturePickerContent(
    options: List<Pair<Gesture, String?>>,
    selectedIds: Set<String>,
    syncedIds: Set<String> = emptySet(),
    pushing: Boolean,
    onChoose: (String) -> Unit,
    onClearAll: () -> Unit,
    onPush: () -> Unit,
    onBack: () -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(450)
        armed = true
    }

    // 배경(카드가 아닌 빈 영역)을 눌러도 나갈 수 있도록 화면 전체에 리플 없는 클릭 레이어.
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (armed) onBack() },
            ),
    ) {
        Column(
            Modifier.fillMaxSize(),
        ) {
            // Flat Hand(기본 대기)를 뺀 손 모양을 2열 수직 스크롤 그리드로 표시
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .padding(top = 6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options, key = { (gesture, _) -> gesture.id }) { (gesture, photo) ->
                    GestureCell(
                        gesture = gesture,
                        photo = photo,
                        selected = gesture.id in selectedIds,
                        synced = gesture.id in syncedIds,
                        onClick = { if (armed) onChoose(gesture.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(156.dp),
                    )
                }
            }

            // ── 하단 액션 바: [All Clear] [Send] (ModeFlowScreen 과 완전히 동일한 패딩과 위치) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mode_clear_all),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                // 전송 중에도 비활성화하지 않는다 — 언제든 다시 보낼 수 있다.
                Button(
                    onClick = onPush,
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
}

@Composable
private fun GestureCell(
    gesture: Gesture,
    photo: String?,
    selected: Boolean,
    synced: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gestureName = stringResource(gesture.displayNameRes)

    // 선택된 항목: 파란색 선 (Accent)
    // 직전에 명령을 보낸 값(의수 현재 설정): 회색에 가까운 연한 하늘색 테두리
    // 그 외 일반 항목: 기본 회색 선 (Line)
    val (borderWidth, borderColor) = when {
        selected -> 2.dp to Mark7Palette.Accent
        synced -> 1.dp to Color(0xFF9BBED6)
        else -> 1.dp to Mark7Palette.Line
    }

    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Mark7Palette.Surface)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = gestureName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(stringResource(R.string.picker_photo), style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                gestureName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Mark7Palette.AccentDim else Mark7Palette.Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
            )
        }

        // 이미 설정되어 있는 값(의수 현재 설정): 더욱 연하고 은은한 하늘색 오버레이
        if (synced) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF38BDF8).copy(alpha = 0.03f))
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun GesturePickerPreview() {
    Mark7Theme {
        GesturePickerContent(
            options = GestureCatalogs.forDof(HandDof.DEFAULT).selectableForState(1).map { it to null },
            selectedIds = setOf("cylinder_grip_open", "cylinder_grip_closed"),
            syncedIds = setOf("tip_pinch_open", "tip_pinch_closed"),
            pushing = false,
            onChoose = {},
            onClearAll = {},
            onPush = {},
            onBack = {},
        )
    }
}
