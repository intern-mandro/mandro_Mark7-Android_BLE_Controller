package com.mandro.mark7.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class HandConfigStoreMigrationTest {

    @Test
    fun `legacy S2 to S9 keys shift to S1 to S8`() {
        val legacy = (2..9).associate { it.toString() to "gesture-$it" }

        assertEquals(
            (1..8).associateWith { "gesture-${it + 1}" },
            migrateStoredStateIds(legacy, schemaVersion = 1),
        )
    }

    @Test
    fun `current S1 to S8 keys keep their state ids`() {
        val current = (1..8).associate { it.toString() to "gesture-$it" }

        assertEquals(
            (1..8).associateWith { "gesture-$it" },
            migrateStoredStateIds(current, schemaVersion = 2),
        )
    }

    @Test
    fun `migration drops idle malformed and out of range state ids`() {
        val stored = mapOf(
            "1" to "old-idle",
            "2" to "valid",
            "bad" to "malformed",
            "10" to "too-high",
        )

        assertEquals(
            mapOf(1 to "valid"),
            migrateStoredStateIds(stored, schemaVersion = 1),
        )
    }
}
