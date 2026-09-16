package com.mandro.mark7.presentation.ui.manual

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.hand.ManualPreset
import com.mandro.mark7.domain.model.hand.CmdPresetCatalogs
import com.mandro.mark7.domain.model.hand.CmdDir
import com.mandro.mark7.domain.model.hand.HandDof
import com.mandro.mark7.presentation.components.Mark7Slider
import com.mandro.mark7.presentation.components.ResetConfirmDialog
import com.mandro.mark7.presentation.components.SliderInterval
import com.mandro.mark7.presentation.theme.Mark7Palette
import kotlinx.coroutines.launch

@Composable
internal fun HeaderPillButton(
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
internal fun CompactCard(
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
internal fun FineTuningAccordionCard(
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

internal data class GapResult(val gapIndex: Int, val rowIndex: Int)

/**
 * 포인터 좌표와 측정된 칩 경계로 타겟 갭 인덱스(0..presets.size)와 행 번호를 계산한다.
 * [presets] 는 전체 목록, [itemBounds] 는 컨테이너 로컬 좌표계의 칩 Rect.
 */
internal fun computeGapAndRow(
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
internal fun CompactPresetChip(
    preset: ManualPreset,
    dof: HandDof,
    displayName: String,
    isSelected: Boolean = false,
    isBeingDragged: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageUri = remember(context, dof, preset.id, preset.isDefault, preset.imageUri) {
        PresetImageStore.imageFor(context, preset, dof)
    }
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
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUri != null) {
                    PresetPhoto(
                        imageUri = imageUri,
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
 * 프리셋 사진을 프레임 전체에 맞추어([ContentScale.Fit]) 전체가 온전히 보이도록 표시한다.
 */
@Composable
internal fun PresetPhoto(
    imageUri: String,
    biasX: Float = 0f,
    biasY: Float = 0f,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = imageUri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
        modifier = modifier,
    )
}

/**
 * 칩과 같은 크기·비율의 프레임에서 사진 전체를 미리보기한다. 배경은 흰색.
 */
@Composable
internal fun PhotoAdjuster(
    imageUri: String,
    biasX: Float,
    biasY: Float,
    onChange: (biasX: Float, biasY: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Mark7Palette.Line, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        PresetPhoto(imageUri, biasX, biasY, Modifier.fillMaxSize())
    }
}

/** 프리셋 사진 선택 (갤러리에서 고른 뒤 내부 저장소로 복사). 사진이 없으면 이모지로 표시. */
@Composable
internal fun PresetImagePicker(
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
@Composable
internal fun FingerMiniChip(
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

internal fun getPresetDisplayName(preset: ManualPreset, dof: HandDof): String {
    if (!preset.isDefault) return preset.name
    return CmdPresetCatalogs.forDof(dof).firstOrNull { it.id == preset.id }?.name ?: preset.name
}

@Composable
internal fun FingerSelectChip(
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
        targetValue = if (selected) Mark7Palette.Accent else Mark7Palette.Line,
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
