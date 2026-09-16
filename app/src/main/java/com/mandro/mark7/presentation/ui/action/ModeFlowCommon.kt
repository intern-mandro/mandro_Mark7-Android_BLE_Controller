package com.mandro.mark7.presentation.ui.action

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.action.ActionMapping
import com.mandro.mark7.domain.model.action.Gesture
import com.mandro.mark7.domain.model.action.ModeState

/** 굽힘(F) — 시인성 높은 선명한 블루. */
internal val FlexionColor = Color(0xFF1565C0)

/** 폄(E) — 복귀 방향, 명확히 잘 보이는 다크 슬레이트. */
internal val ExtensionColor = Color(0xFF475569)

/** 상태에 연결된 손 모양(고정 S0 은 대기 손 모양). 지정 안 됐으면 null. 매핑의 자유도 목록 기준. */
internal fun gestureFor(state: ModeState, mapping: ActionMapping): Gesture? {
    if (state.fixed) return mapping.catalog.idle
    return mapping.catalog.byId(mapping.effectiveGestureIdFor(state.id))
}

/** 손 모양 이름(로케일 반영). null 이면 "미지정". */
@Composable
internal fun gestureLabel(gesture: Gesture?): String =
    gesture?.let { stringResource(it.displayNameRes) } ?: stringResource(R.string.common_unset)
