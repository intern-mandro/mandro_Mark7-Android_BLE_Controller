package com.mandro.mark7.presentation.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.R
import com.mandro.mark7.core.locale.AppLocale
import com.mandro.mark7.domain.model.GlobalSettings
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.presentation.components.HeaderPillButton
import com.mandro.mark7.presentation.components.Mark7Slider
import com.mandro.mark7.presentation.components.ResetConfirmDialog
import com.mandro.mark7.presentation.components.SliderInterval
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SET 전송 결과를 화면 상단 배너로 띄운다 — Mode 탭과 동일 패턴(일회성 이벤트 + 상단 배너 + 자동 소멸).
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

    Box(Modifier.fillMaxSize()) {
        SettingsScreenContent(
            ui = ui,
            actions = viewModel,
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
}

@Composable
fun SettingsScreenContent(
    ui: SettingsUiState,
    actions: SettingsEditActions,
) {
    val context = LocalContext.current
    val currentTag by AppLocale.currentTag.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // 한 번에 하나만 펼쳐진다. 펼쳐도 스크롤을 옮기지 않고 그 자리에서 펼친다.
    var expandedSection by rememberSaveable { mutableStateOf<String?>(null) }

    // 슬라이더 편집은 이 draft 에만 반영된다. 'SET 전송' 을 눌러야 실제 config 에 커밋 + 의수 전송.
    // 토글을 열거나 닫거나 다른 토글로 바꾸면 draft 는 마지막으로 커밋된 값으로 되돌아간다.
    var draft by remember(ui.config.settings) { mutableStateOf(ui.config.settings) }
    LaunchedEffect(expandedSection) { draft = ui.config.settings }

    fun toggleSection(id: String) {
        expandedSection = if (expandedSection == id) null else id
    }

    fun changeLang(tag: String) {
        if (tag == currentTag) return
        AppLocale.setTag(context, tag)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mark7Palette.Bg),
    ) {
        // ── 1. 상단 타이틀 + 원터치 언어 세그먼트 토글 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Mark7Palette.Ink,
            )
            LanguageSegmentToggle(
                currentTag = currentTag,
                onSelect = { changeLang(it) },
            )
        }

        // ── 2. 스크롤 가능한 본문 영역 ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val defaults = GlobalSettings.DEFAULT

            // ── 근전도(EMG) 센서 증폭 게인 (CH1·CH2) ──
            AccordionCard(
                title = stringResource(R.string.settings_emg_sens_title),
                expanded = expandedSection == SEC_EMG,
                onToggle = { toggleSection(SEC_EMG) },
            ) {
                EmgBody(g = draft, onChange = { draft = it })
                AccordionActionRow(
                    pushing = ui.pushing,
                    onSend = { actions.applyAndPush(draft) },
                    onDefault = { draft = draft.copy(emgAmp = defaults.emgAmp) },
                )
            }

            // ── 최대 전류량 제한 (자유도별) ──
            AccordionCard(
                title = stringResource(R.string.settings_max_current_title),
                expanded = expandedSection == SEC_MAX_CURRENT,
                onToggle = { toggleSection(SEC_MAX_CURRENT) },
            ) {
                MaxCurrentPresetRow(
                    values = draft.maxCurrent,
                    onSetAll = { v -> draft = draft.copy(maxCurrent = List(6) { v }) },
                )
                PerDofSliders(
                    values = draft.maxCurrent,
                    valueRange = 600f..1500f,
                    ticks = listOf(900f, 1200f),
                    minLabel = "600 mA",
                    maxLabel = "1500 mA",
                    onCommit = { i, v -> draft = draft.copy(maxCurrent = draft.maxCurrent.withAt(i, v)) },
                )
                AccordionActionRow(
                    pushing = ui.pushing,
                    onSend = { actions.applyAndPush(draft) },
                    onDefault = { draft = draft.copy(maxCurrent = defaults.maxCurrent) },
                )
            }

            // ── 모터 속도 (자유도별) ──
            AccordionCard(
                title = stringResource(R.string.settings_speed),
                expanded = expandedSection == SEC_SPEED,
                onToggle = { toggleSection(SEC_SPEED) },
            ) {
                PerDofSliders(
                    values = draft.motorSpeed,
                    valueRange = 0f..255f,
                    ticks = listOf(85f, 170f),
                    minLabel = "0",
                    maxLabel = "255",
                    onCommit = { i, v -> draft = draft.copy(motorSpeed = draft.motorSpeed.withAt(i, v)) },
                )
                AccordionActionRow(
                    pushing = ui.pushing,
                    onSend = { actions.applyAndPush(draft) },
                    onDefault = { draft = draft.copy(motorSpeed = defaults.motorSpeed) },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun List<Int>.withAt(i: Int, v: Int): List<Int> =
    toMutableList().also { if (i in indices) it[i] = v }

/** ── 모던 캡슐형 언어 선택 토글 (간격이 좁고 콤팩트한 KO | EN 미니 세그먼트) ── */
@Composable
private fun LanguageSegmentToggle(
    currentTag: String,
    onSelect: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Mark7Palette.SurfaceAlt,
        border = BorderStroke(1.dp, Mark7Palette.Line),
        modifier = Modifier.height(26.dp),
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            val isKo = currentTag == "ko"
            LanguageCapsulePill(
                label = "KO",
                selected = isKo,
                onClick = { onSelect("ko") },
            )
            LanguageCapsulePill(
                label = "EN",
                selected = !isKo,
                onClick = { onSelect("en") },
            )
        }
    }
}

@Composable
private fun LanguageCapsulePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Mark7Palette.Accent else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier
            .width(28.dp)
            .height(22.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Mark7Palette.InkMuted,
            )
        }
    }
}


// 특징별 토글 섹션 식별자.
private const val SEC_EMG = "emg"
private const val SEC_MAX_CURRENT = "maxCurrent"
private const val SEC_SPEED = "speed"

/**
 * 특징 하나를 감싸는 접이식 토글 카드. 헤더(제목 + chevron)를 누르면 펼쳐지고, 펼쳤을 때만
 * [body] 를 보여준다. 시각 스타일은 기존 '전문가 세부 피팅' 아코디언과 동일.
 */
@Composable
private fun AccordionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Mark7Palette.Surface,
        border = BorderStroke(1.dp, Mark7Palette.Line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) stringResource(R.string.settings_collapse) else stringResource(R.string.settings_expand),
                        tint = Mark7Palette.InkMuted,
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Mark7Palette.Line)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
            }
        }
    }
}

/** 자유도(F1~F6)별 단일값 슬라이더 6개. */
@Composable
private fun PerDofSliders(
    values: List<Int>,
    valueRange: ClosedFloatingPointRange<Float>,
    ticks: List<Float>,
    minLabel: String,
    maxLabel: String,
    onCommit: (i: Int, v: Int) -> Unit,
) {
    val motorResList = HandStatus.MOTOR_NAME_RES
    for (i in 0..5) {
        val currentVal = values.getOrElse(i) { valueRange.start.roundToInt() }
        var liveVal by remember(currentVal) { mutableIntStateOf(currentVal) }
        Mark7Slider(
            leadingLabel = stringResource(motorResList[i]),
            leadingWidth = 66.dp,
            value = liveVal.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { liveVal = it.roundToInt() },
            onValueChangeFinished = { onCommit(i, liveVal) },
            valueRange = valueRange,
            ticks = ticks,
            minLabel = minLabel,
            maxLabel = maxLabel,
            formatValue = { "${it.roundToInt()}" },
        )
    }
}

/** 최대 전류량 일괄 프리셋(부드럽게/표준/강하게) 한 줄. draft 만 바꾼다 (SET 전송 시 커밋). */
@Composable
private fun MaxCurrentPresetRow(
    values: List<Int>,
    onSetAll: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HeaderPillButton(
            text = stringResource(R.string.settings_preset_light),
            onClick = { onSetAll(800) },
            color = if (values.all { it == 800 }) Mark7Palette.Accent else Mark7Palette.InkMuted,
            strong = values.all { it == 800 },
        )
        HeaderPillButton(
            text = stringResource(R.string.settings_preset_normal),
            onClick = { onSetAll(1000) },
            color = if (values.all { it == 1000 }) Mark7Palette.Accent else Mark7Palette.InkMuted,
            strong = values.all { it == 1000 },
        )
        HeaderPillButton(
            text = stringResource(R.string.settings_preset_strong),
            onClick = { onSetAll(1300) },
            color = if (values.all { it == 1300 }) Mark7Palette.Accent else Mark7Palette.InkMuted,
            strong = values.all { it == 1300 },
        )
    }
}

/** EMG 센서 증폭 게인 (CH1·CH2). draft([g]) 만 바꾸며 [onChange] 로 상위에 전달한다. */
@Composable
private fun EmgBody(
    g: GlobalSettings,
    onChange: (GlobalSettings) -> Unit,
) {
    val amp0 = g.emgAmp.getOrElse(0) { 20 }
    val amp1 = g.emgAmp.getOrElse(1) { 20 }
    var live0 by remember(amp0) { mutableIntStateOf(amp0) }
    var live1 by remember(amp1) { mutableIntStateOf(amp1) }
    val emgTicks = remember { listOf(7f, 14f) }

    // 채널 1 (CH 1)
    Mark7Slider(
        leadingLabel = stringResource(R.string.monitor_emg_ch0),
        leadingWidth = 52.dp,
        value = live0.toFloat().coerceIn(0f, 20f),
        onValueChange = { live0 = it.roundToInt() },
        onValueChangeFinished = { onChange(g.copy(emgAmp = g.emgAmp.withAt(0, live0))) },
        valueRange = 0f..20f,
        ticks = emgTicks,
        minLabel = "Lv. 0",
        maxLabel = "Lv. 20",
        formatValue = { "${it.roundToInt()}" },
    )

    // 채널 2 (CH 2)
    Mark7Slider(
        leadingLabel = stringResource(R.string.monitor_emg_ch1),
        leadingWidth = 52.dp,
        value = live1.toFloat().coerceIn(0f, 20f),
        onValueChange = { live1 = it.roundToInt() },
        onValueChangeFinished = { onChange(g.copy(emgAmp = g.emgAmp.withAt(1, live1))) },
        valueRange = 0f..20f,
        ticks = emgTicks,
        minLabel = "Lv. 0",
        maxLabel = "Lv. 20",
        formatValue = { "${it.roundToInt()}" },
        intervals = listOf(
            SliderInterval(
                label = stringResource(R.string.settings_preset_sens_low),
                isActive = amp0 <= 7 && amp1 <= 7,
                onClick = { onChange(g.copy(emgAmp = List(2) { 6 })) },
                weight = 7f,
            ),
            SliderInterval(
                label = stringResource(R.string.settings_preset_sens_mid),
                isActive = amp0 in 8..14 && amp1 in 8..14,
                onClick = { onChange(g.copy(emgAmp = List(2) { 11 })) },
                weight = 7f,
            ),
            SliderInterval(
                label = stringResource(R.string.settings_preset_sens_high),
                isActive = amp0 > 14 && amp1 > 14,
                onClick = { onChange(g.copy(emgAmp = List(2) { 16 })) },
                weight = 6f,
            ),
        ),
    )
}

/**
 * 각 토글(특징) 본문 맨 아래에 들어가는 [디폴트][SET 전송] 버튼 줄.
 * - 디폴트: 그 특징의 파라미터를 기본값으로 되돌린다. 누르면 먼저 확인 다이얼로그를 띄운다.
 * - SET 전송: 전체 설정을 SET 프레임으로 의수에 보낸다(SET 은 통짜라 특징별 분리 전송은 불가).
 *   전송 중엔 버튼 비활성 + 문구 전환, 결과는 화면 상단 배너(Mode 탭과 동일)로 표시된다.
 */
@Composable
private fun AccordionActionRow(
    pushing: Boolean,
    onSend: () -> Unit,
    onDefault: () -> Unit,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    HorizontalDivider(color = Mark7Palette.Line)

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
                text = stringResource(R.string.settings_reset),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Button(
            onClick = onSend,
            enabled = !pushing,
            colors = ButtonDefaults.buttonColors(containerColor = Mark7Palette.Accent),
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(if (pushing) R.string.common_sending else R.string.settings_send_set),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }

    if (showResetConfirm) {
        ResetConfirmDialog(
            onConfirm = onDefault,
            onDismiss = { showResetConfirm = false },
        )
    }
}



/** [SettingsViewModel] 에서 화면이 쓰는 편집 액션 인터페이스. */
interface SettingsEditActions {
    /** draft 설정을 로컬에 커밋하고 곧바로 SET 프레임으로 의수에 전송한다. (토글별 'SET 전송') */
    fun applyAndPush(settings: GlobalSettings)
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun SettingsPreview() {
    val noop = object : SettingsEditActions {
        override fun applyAndPush(settings: GlobalSettings) {}
    }
    Mark7Theme {
        SettingsScreenContent(
            ui = SettingsUiState(),
            actions = noop,
        )
    }
}
