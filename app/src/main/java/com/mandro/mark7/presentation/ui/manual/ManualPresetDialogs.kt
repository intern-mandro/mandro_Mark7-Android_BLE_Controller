package com.mandro.mark7.presentation.ui.manual

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.hand.ManualPreset
import com.mandro.mark7.domain.model.hand.CmdDir
import com.mandro.mark7.domain.model.hand.HandDof
import com.mandro.mark7.presentation.theme.Mark7Palette

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
    // 🖖(발칸) · 🖐(손가락 벌린 손)은 손가락 외전이 필요해 하드웨어로 불가 — 제외
    "✋", // 전체 펴기 (손가락 모음)
    "🤏", // 집기 (pick / pinch)
    "🖕", // 중지
)

/** 새 동작 만들기 모달 다이얼로그 */
@Composable
internal fun CreatePresetDialog(
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
                            color = if (direction == CmdDir.GRASP) Mark7Palette.GraspSoft else Mark7Palette.Surface,
                            border = if (direction == CmdDir.GRASP) BorderStroke(2.dp, Mark7Palette.GraspBorder) else BorderStroke(1.dp, Mark7Palette.Line),
                        ) {
                            Text(
                                text = "✊ " + stringResource(R.string.manual_grasp_title),
                                color = if (direction == CmdDir.GRASP) Mark7Palette.Grasp else Mark7Palette.Ink,
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
                            color = if (direction == CmdDir.RELEASE) Mark7Palette.ReleaseSoft else Mark7Palette.Surface,
                            border = if (direction == CmdDir.RELEASE) BorderStroke(2.dp, Mark7Palette.ReleaseBorder) else BorderStroke(1.dp, Mark7Palette.Line),
                        ) {
                            Text(
                                text = "🖐 " + stringResource(R.string.manual_release_title),
                                color = if (direction == CmdDir.RELEASE) Mark7Palette.Release else Mark7Palette.Ink,
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
internal fun EditPresetDialog(
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
                            color = if (direction == CmdDir.GRASP) Mark7Palette.GraspSoft else Mark7Palette.Surface,
                            border = if (direction == CmdDir.GRASP) BorderStroke(2.dp, Mark7Palette.GraspBorder) else BorderStroke(1.dp, Mark7Palette.Line),
                        ) {
                            Text(
                                text = "✊ " + stringResource(R.string.manual_grasp_title),
                                color = if (direction == CmdDir.GRASP) Mark7Palette.Grasp else Mark7Palette.Ink,
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
                            color = if (direction == CmdDir.RELEASE) Mark7Palette.ReleaseSoft else Mark7Palette.Surface,
                            border = if (direction == CmdDir.RELEASE) BorderStroke(2.dp, Mark7Palette.ReleaseBorder) else BorderStroke(1.dp, Mark7Palette.Line),
                        ) {
                            Text(
                                text = "🖐 " + stringResource(R.string.manual_release_title),
                                color = if (direction == CmdDir.RELEASE) Mark7Palette.Release else Mark7Palette.Ink,
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
