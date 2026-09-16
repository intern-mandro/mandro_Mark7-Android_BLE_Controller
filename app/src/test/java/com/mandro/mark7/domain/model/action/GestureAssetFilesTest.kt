package com.mandro.mark7.domain.model.action

import com.mandro.mark7.domain.model.hand.HandDof
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 자유도별 사진 폴더(`assets/mset_hand_shape/<N>dof`)가 손 모양 목록과 맞는지 검증한다.
 * 목록 순서를 바꾸고 사진 파일명(`<액션 ID>_<id>.<확장자>`)을 안 바꾸면 여기서 실패한다.
 */
class GestureAssetFilesTest {

    private val assetsRoot: File = listOf(File("src/main/assets"), File("app/src/main/assets"))
        .firstOrNull { it.isDirectory }
        ?: error("assets 폴더를 찾을 수 없다 (작업 폴더: ${File("").absolutePath})")

    @Test
    fun `each dof folder holds exactly one photo per action id from 00 flat hand`() {
        HandDof.entries.forEach { dof ->
            val catalog = GestureCatalogs.forDof(dof)
            val expected = catalog.gestures.mapNotNull { catalog.imageAssetPath(it) }.toSet()
            val extension = catalog.imageExtension
            assertTrue("$dof", "${catalog.assetFolder}/00_flat_hand.$extension" in expected)
            val actual = File(assetsRoot, catalog.assetFolder).listFiles().orEmpty()
                .map { "${catalog.assetFolder}/${it.name}" }
                .toSet()
            assertEquals("$dof missing ${expected - actual}; unexpected ${actual - expected}", expected, actual)
        }
    }
}
