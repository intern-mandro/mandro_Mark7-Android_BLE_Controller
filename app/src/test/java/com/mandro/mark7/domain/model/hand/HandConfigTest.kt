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
