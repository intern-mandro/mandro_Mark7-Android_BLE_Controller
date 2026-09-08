package com.mandro.mark7.presentation.ui.dof

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mandro.mark7.R
import com.mandro.mark7.core.locale.AppLocale
import com.mandro.mark7.domain.model.HandDof
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme

@Composable
fun DofSetupScreen(
    onDone: () -> Unit,
    viewModel: DofSetupViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.done.collect { onDone() }
    }

    DofSetupContent(
        ui = ui,
        onSelectDof = viewModel::selectDof,
        onConfirm = viewModel::confirmSelection,
    )
}

@Composable
private fun DofSetupContent(
    ui: DofSetupUiState,
    onSelectDof: (HandDof) -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val currentTag by AppLocale.currentTag.collectAsStateWithLifecycle()
    var showMore by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredDofs = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        val allDofs = HandDof.entries.sortedByDescending { it.dof }
        if (q.isEmpty()) {
            allDofs
        } else {
            val qNorm = q.replace(" ", "")
            allDofs.filter { dof ->
                val title = context.getString(dof.titleRes).lowercase()
                val titleNorm = title.replace(" ", "")
                val badge = dof.badgeRes?.let { context.getString(it).lowercase() } ?: ""
                val desc = context.getString(dof.descRes).lowercase()
                dof.dof.toString() in q ||
                    "${dof.dof}dof" in qNorm ||
                    "${dof.dof}자유도" in qNorm ||
                    q in title ||
                    qNorm in titleNorm ||
                    q in badge ||
                    q in desc ||
                    q in dof.id.lowercase()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mark7Palette.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── 상단 고정 헤더: 화면 타이틀 + 언어 선택 토글, Current Robot Hand, more 버튼 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            // 상단 타이틀 + 우측 상단 언어 선택 (KO | EN) 세그먼트 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dof_setup_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                LanguageSegmentToggle(
                    currentTag = currentTag,
                    onSelect = { tag -> AppLocale.setTag(context, tag) },
                )
            }
            Spacer(Modifier.height(34.dp))

            // 현재 선택된 의수 섹션 라벨
            Text(
                text = stringResource(R.string.dof_current_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Mark7Palette.InkMuted,
            )
            Spacer(Modifier.height(10.dp))

            // 현재 선택된 의수 카드 (단일 네모 형태)
            CurrentDofCard(
                dof = ui.selectedDof,
                onClick = { showMore = !showMore },
            )

            // Current Robot Hand 바로 밑 중앙에 배경 없는 텍스트 버튼으로 More 배치
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (showMore) stringResource(R.string.dof_close_btn) else stringResource(R.string.dof_more_btn),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = Mark7Palette.InkMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showMore = !showMore }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // ── All Models 영역 (showMore 가 true 일 때 전개되며, 이 영역 내부에서만 스크롤) ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showMore,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Spacer(Modifier.height(14.dp))
                    // All Models 타이틀 + 오른쪽 위에 작게 배치된 검색창
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.dof_all_models_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Mark7Palette.Ink,
                        )

                        // 오른쪽 위에 작게 배치된 검색창
                        DofSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier.width(160.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // 격자 네모 (All Model 영역 내부에서만 깔끔하게 스크롤)
                    if (filteredDofs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.dof_setup_search_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Mark7Palette.InkMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp),
                        ) {
                            items(filteredDofs.chunked(2)) { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    rowItems.forEach { dof ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            DofGridCard(
                                                dof = dof,
                                                isSelected = dof == ui.selectedDof,
                                                onClick = { onSelectDof(dof) },
                                            )
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(Modifier.weight(1f).height(140.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 하단 진행 버튼 (고정) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Button(
                onClick = onConfirm,
                enabled = !ui.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mark7Palette.Accent,
                    disabledContainerColor = Mark7Palette.Line,
                ),
            ) {
                Text(
                    text = stringResource(R.string.dof_setup_continue, ui.selectedDof.dof),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * 현재 선택된 의수를 보여주는 깔끔한 단일 네모 카드.
 */
@Composable
private fun CurrentDofCard(
    dof: HandDof,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Mark7Palette.Surface,
        border = BorderStroke(1.8.dp, Mark7Palette.Accent),
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 좌측 사진 썸네일
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mark7Palette.SurfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = dof.imageAssetPath,
                    contentDescription = stringResource(dof.titleRes),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(14.dp))

            // 중앙 타이틀
            Text(
                text = stringResource(dof.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Mark7Palette.Ink,
                modifier = Modifier.weight(1f),
            )

            // 우측 현재 선택됨 체크 아이콘
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.dof_current_badge),
                tint = Mark7Palette.Accent,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 모든 의수 버전을 사진과 함께 격자(Grid)로 보여주는 네모 카드.
 */
@Composable
private fun DofGridCard(
    dof: HandDof,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Mark7Palette.Surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Mark7Palette.Accent else Mark7Palette.Line,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 상단 실물 사진 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(Mark7Palette.SurfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = dof.imageAssetPath,
                    contentDescription = stringResource(dof.titleRes),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // 선택된 상태일 때 우측 상단 체크 뱃지
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Mark7Palette.Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // 하단 타이틀 영역 (표준/확장 라벨 제거, 일관된 중앙 정렬)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(dof.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 깔끔하고 미니멀한 검색창 컴포넌트 */
@Composable
private fun DofSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Mark7Palette.Surface,
        border = BorderStroke(1.dp, Mark7Palette.Line),
        modifier = modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Mark7Palette.InkMuted,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dof_setup_search_hint),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Mark7Palette.InkMuted,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = Mark7Palette.Ink,
                    ),
                    cursorBrush = SolidColor(Mark7Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(18.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.settings_reset_cancel),
                        tint = Mark7Palette.InkMuted,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** ── 모던 캡슐형 언어 선택 토글 (KO | EN 미니 세그먼트) ── */
@Composable
private fun LanguageSegmentToggle(
    currentTag: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Mark7Palette.Surface,
        border = BorderStroke(1.dp, Mark7Palette.Line),
        modifier = modifier.height(28.dp),
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
        modifier = Modifier
            .width(32.dp)
            .height(24.dp),
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

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun DofSetupPreview() {
    Mark7Theme {
        DofSetupContent(
            ui = DofSetupUiState(selectedDof = HandDof.DOF_6),
            onSelectDof = {},
            onConfirm = {},
        )
    }
}
