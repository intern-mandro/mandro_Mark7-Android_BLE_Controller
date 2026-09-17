package com.mandro.mark7.domain.model.hand

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandConfigTest {
    private val storeJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `default EMG sensitivity is 10 for both channels`() {
        assertEquals(listOf(10, 10), GlobalSettings.DEFAULT.emgAmp)
    }

    @Test
    fun `7dof settings fill the seventh motor current and speed with defaults`() {
        val settings = GlobalSettings.DEFAULT.forDof(7)

        assertEquals(listOf(1200, 1200, 1200, 1200, 1200, 1100, 1200), settings.maxCurrent)
        assertEquals(List(7) { 255 }, settings.motorSpeed)
    }

    @Test
    fun `forDof keeps values the user already set`() {
        val saved = GlobalSettings.DEFAULT.copy(
            maxCurrent = listOf(800, 800, 800, 800, 800, 800, 900),
            motorSpeed = listOf(10, 20, 30, 40, 50, 60),
        )

        val settings = saved.forDof(7)

        assertEquals(saved.maxCurrent, settings.maxCurrent)
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 255), settings.motorSpeed)
    }

    @Test
    fun `forDof does not shrink lists for fewer motors`() {
        assertEquals(GlobalSettings.DEFAULT, GlobalSettings.DEFAULT.forDof(5))
    }

    @Test
    fun `serialized hand config contains only active settings`() {
        val encoded = storeJson.encodeToString(HandConfig.DEFAULT)

        assertFalse(encoded.contains("\"patterns\""))
        assertTrue(encoded.contains("\"settings\""))
    }

    @Test
    fun `legacy pattern data is ignored when loading saved config`() {
        val currentJson = storeJson.encodeToString(HandConfig.DEFAULT)
        val legacyJson = currentJson.replaceFirst("{", "{\"patterns\":[{\"legacy\":true}],")

        val decoded = storeJson.decodeFromString<HandConfig>(legacyJson)

        assertEquals(HandConfig.DEFAULT, decoded)
    }
}
