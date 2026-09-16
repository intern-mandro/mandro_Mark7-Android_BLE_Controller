package com.mandro.mark7.presentation.ui.action

import android.content.Context
import com.mandro.mark7.domain.model.action.Gesture
import com.mandro.mark7.domain.model.action.GestureCatalog

/**
 * 손 모양 사진을 Coil 이 읽을 수 있는 `file:///android_asset/...` URI 로 돌려준다.
 * 사진은 자유도별 폴더에 `<액션 ID 두 자리>_<id>.<확장자>`로 둔다 — [GestureCatalog.imageAssetPath].
 * 상태 다이어그램 노드 사진과 손 모양 그리드 피커([GesturePickerScreen])의 이미지 소스.
 */
object GestureAssets {

    /** 사진 파일이 없으면 null — 그때 노드는 "IDLE"(대기) 글자나 + 아이콘으로 그린다. */
    fun imageFor(context: Context, catalog: GestureCatalog, gesture: Gesture): String? {
        val path = catalog.imageAssetPath(gesture) ?: return null
        val exists = runCatching {
            context.assets.list(catalog.assetFolder)?.contains(path.substringAfterLast('/')) == true
        }.getOrDefault(false)
        return if (exists) "file:///android_asset/$path" else null
    }
}
