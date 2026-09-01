package com.mandro.mark7.presentation.ui.action

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mandro.mark7.domain.model.HandAction
import com.mandro.mark7.domain.repository.HandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatternPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: HandRepository,
) : ViewModel() {

    val action: HandAction =
        runCatching { HandAction.valueOf(savedStateHandle.get<String>("action") ?: "") }
            .getOrDefault(HandAction.FLEXION)

    private val _selected = MutableStateFlow(repo.actionMapping.value.patternFor(action))
    val selected = _selected.asStateFlow()

    fun choose(patternIndex: Int) {
        _selected.value = patternIndex
        val m = repo.actionMapping.value
        viewModelScope.launch {
            repo.updateActionMapping(
                m.copy(patternIndexByAction = m.patternIndexByAction + (action to patternIndex)),
            )
        }
    }
}

/**
 * 한 액션에 연결할 패턴을 사진 그리드에서 고른다. 지금은 해당 액션의 가이드
 * 프레임을 후보 카드로 나열 — 실제로는 "패턴 8개의 대표 손 모양 사진"을 보여주게
 * 바꾼다 (assets 구성은 TODO).
 */
@Composable
fun PatternPickerScreen(
    onDone: () -> Unit,
    viewModel: PatternPickerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val frames = GestureAssets.framesFor(context, viewModel.action)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(viewModel.action.displayName, style = MaterialTheme.typography.headlineSmall)
        Text("손 모양을 하나 고르면 저장됩니다.", style = MaterialTheme.typography.bodyMedium)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(frames.size) { i ->
                val border = if (selected == i) 3.dp else 0.dp
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEDEFF0))
                        .clickable {
                            viewModel.choose(i)
                            onDone()
                        },
                    contentAlignment = Alignment.BottomStart,
                ) {
                    AsyncImage(
                        model = frames[i],
                        contentDescription = "패턴 ${i + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        "  ${i + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
}
