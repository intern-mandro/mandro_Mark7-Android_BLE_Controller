package com.mandro.mark7.presentation.ui.action

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mandro.mark7.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mandro.mark7.domain.model.Gesture
import com.mandro.mark7.domain.model.GestureCatalog
import com.mandro.mark7.domain.repository.HandRepository
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GesturePickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: HandRepository,
) : ViewModel() {

    /** 어떤 상태(S2..S8)에 손 모양을 지정하는 중인지. */
    val stateId: Int = savedStateHandle.get<String>("state")?.toIntOrNull() ?: 2

    /** 이 화면에 들어올 때 저장돼 있던 값. 나갈 때 [selectedId] 와 다르면 "저장할까요?" 를 묻는다. */
    val originalGestureId: String? = repo.actionMapping.value.gestureIdFor(stateId)

    private val _selectedId = MutableStateFlow(originalGestureId)
    val selectedId = _selectedId.asStateFlow()

    /** 셀 탭 = '선택'만(하이라이트). 실제 저장은 [confirm] 에서 — 실수 탭으로 바뀌는 것 방지. */
    fun choose(gestureId: String) {
        _selectedId.value = gestureId
    }

    /** '지정' 버튼: 현재 선택된 손 모양을 이 상태에 저장한다. */
    fun confirm() {
        val id = _selectedId.value ?: return
        val m = repo.actionMapping.value
        viewModelScope.launch {
            repo.updateActionMapping(
                m.copy(gestureIdByState = m.gestureIdByState + (stateId to id)),
            )
        }
    }

    /** 이 상태의 손 모양 지정을 지운다(빈 상태로). */
    fun clear() {
        _selectedId.value = null
        val m = repo.actionMapping.value
        viewModelScope.launch {
            repo.updateActionMapping(m.copy(gestureIdByState = m.gestureIdByState - stateId))
        }
    }
}

/**
 * 한 상태에 연결할 손 모양을 사진 그리드에서 고른다 (reference.png 스타일).
 * 후보는 [GestureCatalog.ALL] (2열 그리드, 스크롤 없이 한 화면). grip 종류를 늘리려면
 * 카탈로그에 항목을 추가한다 — 행 수는 weight 로 자동 분배된다.
 */
@Composable
fun GesturePickerScreen(
    onDone: () -> Unit,
    viewModel: GesturePickerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val options = GestureCatalog.ALL.map { it to GestureAssets.representativeForDir(context, it.assetDir) }

    // 선택했는데 '지정'을 안 눌렀으면(=변경사항 있음) 나갈 때 저장 여부를 묻는다.
    val dirty = selectedId != viewModel.originalGestureId
    var showSavePrompt by remember { mutableStateOf(false) }
    fun requestExit() {
        if (dirty) showSavePrompt = true else onDone()
    }

    // 시스템 뒤로가기 + 상단바 ← 화살표(아래 dispatcher 경유) 모두 여기서 가로챈다.
    BackHandler { requestExit() }

    GesturePickerContent(
        options = options,
        selectedId = selectedId,
        onChoose = viewModel::choose,                     // 선택(하이라이트)만
        onConfirm = { viewModel.confirm(); onDone() },    // 지정 + 나가기
        onClear = { viewModel.clear(); onDone() },
        onBack = { requestExit() },                       // 배경 탭 → 저장 여부 확인
    )

    if (showSavePrompt) {
        AlertDialog(
            onDismissRequest = { showSavePrompt = false },
            containerColor = Mark7Palette.Surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.picker_save_prompt_title),
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                )
            },
            text = { Text(stringResource(R.string.picker_save_prompt_msg), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showSavePrompt = false
                    viewModel.confirm()
                    onDone()
                }) {
                    Text(stringResource(R.string.picker_save), fontWeight = FontWeight.Bold, color = Mark7Palette.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSavePrompt = false
                    onDone()
                }) {
                    Text(stringResource(R.string.picker_discard), color = Mark7Palette.InkMuted)
                }
            },
        )
    }
}

@Composable
private fun GesturePickerContent(
    options: List<Pair<Gesture, String?>>,
    selectedId: String?,
    onChoose: (String) -> Unit,
    onConfirm: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    // 이 화면이 뜬 직후 짧은 시간 동안은 입력을 무시한다 — 이전 화면(노드) 탭이
    // 전환 직후 이 화면으로 새어들어와 셀이 눌리는 걸 막는다.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(450)
        armed = true
    }

    // 제목(← S8 손 모양)은 Scaffold 상단바(PickerTopBar)가 그린다 — 여기서는 그리드만.
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
        Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
            .padding(top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 스크롤 없이 한 화면에 모두: 2열 × N행을 weight 로 나눠 채운다.
        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.chunked(2).forEach { rowItems ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { (gesture, photo) ->
                        GestureCell(
                            gesture = gesture,
                            photo = photo,
                            selected = gesture.id == selectedId,
                            onClick = { if (armed) onChoose(gesture.id) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (rowItems.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ── 하단 액션 바: [지우기] [이 손 모양으로 지정] ──
        // 셀 탭은 선택만 하고, 여기 '지정'을 눌러야 실제로 저장된다.
        var confirmClear by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedId != null) {
                TextButton(onClick = { if (armed) confirmClear = true }) {
                    Text(stringResource(R.string.mode_clear_confirm), color = Mark7Palette.Danger, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = { if (armed) onConfirm() },
                enabled = selectedId != null,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mark7Palette.Accent,
                    disabledContainerColor = Mark7Palette.Line,
                ),
            ) {
                Text(
                    stringResource(R.string.picker_confirm),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                containerColor = Mark7Palette.Surface,
                tonalElevation = 0.dp,
                title = {
                    Text(
                        text = stringResource(R.string.mode_clear_title),
                        fontWeight = FontWeight.Bold,
                        color = Mark7Palette.Danger,
                    )
                },
                text = { Text(stringResource(R.string.mode_clear_msg), style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    TextButton(onClick = { confirmClear = false; onClear() }) {
                        Text(stringResource(R.string.mode_clear_confirm), fontWeight = FontWeight.Bold, color = Mark7Palette.Danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) }
                },
            )
        }
    }
    }
}

@Composable
private fun GestureCell(
    gesture: Gesture,
    photo: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gestureName = stringResource(gesture.displayNameRes)
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Mark7Palette.Surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Mark7Palette.Accent else Mark7Palette.Line,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Mark7Palette.SurfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = gestureName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(stringResource(R.string.picker_photo), style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            gestureName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Mark7Palette.AccentDim else Mark7Palette.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun GesturePickerPreview() {
    Mark7Theme {
        GesturePickerContent(
            options = GestureCatalog.ALL.map { it to null },
            selectedId = "close",
            onChoose = {},
            onConfirm = {},
            onClear = {},
            onBack = {},
        )
    }
}
