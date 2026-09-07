package com.mandro.mark7.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualPresetTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun defaultPresets_haveSixItemsAndCorrectConfiguration() {
        val defaults = ManualPreset.DEFAULT_PRESETS
        assertEquals(6, defaults.size)

        // Fist
        val fist = defaults.first { it.id == "fist" }
        assertEquals("✊", fist.emoji)
        assertEquals(CmdDir.GRASP, fist.direction)
        assertEquals(6, fist.fingers.size)
        assertTrue(fist.fingers.all { it })

        // Point
        val point = defaults.first { it.id == "point" }
        assertEquals("☝️", point.emoji)
        assertEquals(CmdDir.GRASP, point.direction)
        // Index finger (F2, index 1) is false, others true
        assertEquals(listOf(true, false, true, true, true, true), point.fingers)

        // Open
        val open = defaults.first { it.id == "open" }
        assertEquals("🖐", open.emoji)
        assertEquals(CmdDir.RELEASE, open.direction)
        assertTrue(open.fingers.all { it })
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

        assertEquals(7, deserialized.size)
        val custom = deserialized.last()
        assertEquals("custom_test", custom.id)
        assertEquals("테스트 제스처", custom.name)
        assertEquals("🤙", custom.emoji)
        assertEquals(listOf(true, false, false, false, false, true), custom.fingers)
        assertEquals(CmdDir.GRASP, custom.direction)
    }
}
