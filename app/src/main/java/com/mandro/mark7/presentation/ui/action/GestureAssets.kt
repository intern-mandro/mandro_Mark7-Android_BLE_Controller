package com.mandro.mark7.presentation.ui.action

import android.content.Context
import com.mandro.mark7.domain.model.HandAction

/**
 * `assets/gesture_guides/<action>/fNN.jpg` 목록을 Coil 이 읽을 수 있는
 * `file:///android_asset/...` URI 로 돌려준다. 액션·패턴을 사진으로 고르는
 * UI 의 이미지 소스.
 */
object GestureAssets {

    fun framesFor(context: Context, action: HandAction): List<String> =
        runCatching {
            context.assets.list("gesture_guides/${action.assetDir}")
                ?.filter { it.endsWith(".jpg", ignoreCase = true) }
                ?.sorted()
                ?.map { "file:///android_asset/gesture_guides/${action.assetDir}/$it" }
                ?: emptyList()
        }.getOrDefault(emptyList())

    fun thumbnailFor(context: Context, action: HandAction): String? =
        framesFor(context, action).firstOrNull()
}
