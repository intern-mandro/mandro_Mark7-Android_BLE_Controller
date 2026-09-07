package com.mandro.mark7.presentation.ui.action

import android.content.Context

/**
 * `assets/gesture_guides/<dir>/fNN.jpg` 목록을 Coil 이 읽을 수 있는
 * `file:///android_asset/...` URI 로 돌려준다. 상태 다이어그램 노드 사진과
 * 손 모양 그리드 피커([GesturePickerScreen])의 이미지 소스.
 */
object GestureAssets {

    fun framesForDir(context: Context, dir: String): List<String> =
        runCatching {
            context.assets.list("gesture_guides/$dir")
                ?.filter { it.endsWith(".jpg", ignoreCase = true) }
                ?.sorted()
                ?.map { "file:///android_asset/gesture_guides/$dir/$it" }
                ?: emptyList()
        }.getOrDefault(emptyList())

    /** 해당 카테고리의 대표(첫) 프레임. 노드·그리드 셀 썸네일용. */
    fun representativeForDir(context: Context, dir: String): String? =
        framesForDir(context, dir).firstOrNull()
}
