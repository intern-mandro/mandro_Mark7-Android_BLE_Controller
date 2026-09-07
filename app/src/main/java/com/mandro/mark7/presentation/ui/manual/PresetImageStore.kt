package com.mandro.mark7.presentation.ui.manual

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 프리셋 사진을 앱 내부 저장소로 복사해 두는 헬퍼.
 *
 * 갤러리에서 고른 `content://` URI 는 일시적이라 앱 재시작·원본 삭제 후 못 읽는다.
 * 골랐을 때 즉시 `filesDir/preset_images/<uuid>.jpg` 로 복사하고, 그 `file://` 경로를
 * [com.mandro.mark7.domain.model.ManualPreset.imageUri] 에 저장한다.
 */
object PresetImageStore {

    private const val DIR = "preset_images"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** [source] 이미지를 내부 저장소로 복사하고 `file://` 경로를 돌려준다. 실패 시 null. */
    fun persist(context: Context, source: Uri): String? = runCatching {
        val dest = File(dir(context), "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(dest).toString()
    }.getOrNull()

    /** 프리셋 삭제/사진 교체 시 예전 파일 정리. 내부 저장소 파일만 지운다. */
    fun deleteIfOwned(context: Context, imageUri: String?) {
        if (imageUri.isNullOrBlank()) return
        runCatching {
            val file = imageUri.toUri().path?.let(::File) ?: return
            if (file.parentFile == dir(context) && file.exists()) file.delete()
        }
    }

    private fun String.toUri(): Uri = Uri.parse(this)
}
