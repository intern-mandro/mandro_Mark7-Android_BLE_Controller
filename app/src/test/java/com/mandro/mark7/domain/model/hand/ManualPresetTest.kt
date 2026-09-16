package com.mandro.mark7.domain.model.hand
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualPresetTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun defaultPresets_areThreeWithCorrectConfiguration() {
        val defaults = ManualPreset.DEFAULT_PRESETS
        assertEquals(3, defaults.size)
        assertTrue(defaults.all { it.isDefault && it.fingers.size == 6 })

        // 전체 쥐기
        val fist = defaults.first { it.id == "fist" }
        assertEquals("✊", fist.emoji)
        assertEquals(CmdDir.GRASP, fist.direction)
        assertTrue(fist.fingers.all { it })

        // 전체 펴기
        val open = defaults.first { it.id == "open" }
        assertEquals("✋", open.emoji)
        assertEquals(CmdDir.RELEASE, open.direction)
        assertTrue(open.fingers.all { it })

        // 포인팅 — 검지(F2, index 1)만 빠지고 나머지 쥐기
        val point = defaults.first { it.id == "point" }
        assertEquals("☝️", point.emoji)
        assertEquals(CmdDir.GRASP, point.direction)
        assertEquals(listOf(true, false, true, true, true, true), point.fingers)
    }

    @Test
    fun defaultPresets_areGroupedByDofAndUseMatchingFingerCounts() {
        for (dof in HandDof.entries) {
            val defaults = CmdPresetCatalogs.forDof(dof)
            assertEquals(3, defaults.size)
            assertTrue(defaults.all { it.isDefault && it.fingers.size == dof.dof })
            assertEquals(listOf("open", "fist", "point"), defaults.map { it.id })
            assertEquals(listOf("00_flat_hand.jpg", "02_fist.jpg", "03_pointing.jpg"), defaults.map { it.imageFileName })
            assertTrue(defaults.first { it.id == "fist" }.fingers.all { it })
            assertEquals(List(dof.dof) { it != 1 }, defaults.first { it.id == "point" }.fingers)
        }
    }

    @Test
    fun savedSixMotorPresetsAreAdaptedWithoutChangingCustomChoices() {
        val oldDefault = ManualPreset.DEFAULT_PRESETS.first { it.id == "fist" }
        val custom = ManualPreset(
            id = "custom", name = "Custom", emoji = "✊",
            fingers = listOf(true, false, true, false, true, false), direction = CmdDir.GRASP,
        )

        val five = CmdPresetCatalogs.normalizeSaved(listOf(oldDefault, custom), HandDof.DOF_5)
        assertEquals(List(5) { true }, five[0].fingers)
        assertEquals(listOf(true, false, true, false, true), five[1].fingers)

        val seven = CmdPresetCatalogs.normalizeSaved(listOf(oldDefault, custom), HandDof.DOF_7)
        assertEquals(List(7) { true }, seven[0].fingers)
        assertEquals(listOf(true, false, true, false, true, false, false), seven[1].fingers)
    }

    @Test
    fun manualPreset_serializationRoundtrip() {
        val presets = ManualPreset.DEFAULT_PRESETS + ManualPreset(
            id = "custom_test",
            name = "테스트 제스처",
            emoji = "🤙",
            fingers = listOf(true, false, false, false, false, true),
            direction = CmdDir.GRASP,
            isDefault = false,
        )

        val serialized = json.encodeToString(presets)
        val deserialized = json.decodeFromString<List<ManualPreset>>(serialized)

        assertEquals(4, deserialized.size)
        val custom = deserialized.last()
        assertEquals("custom_test", custom.id)
        assertEquals("테스트 제스처", custom.name)
        assertEquals("🤙", custom.emoji)
        assertEquals(listOf(true, false, false, false, false, true), custom.fingers)
        assertEquals(CmdDir.GRASP, custom.direction)
    }
}
