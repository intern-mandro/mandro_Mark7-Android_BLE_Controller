package com.mandro.mark7.domain.model.action

import com.mandro.mark7.core.ble.MarkSevenProtocol
import com.mandro.mark7.domain.model.hand.HandDof
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotGesturePolicyTest {
    @Test
    fun `six dof filters pairs and singles without renumbering`() {
        val catalog = GestureCatalogs.DOF_6
        for (state in 1..4) {
            assertEquals((1..10).toList(), catalog.selectableForState(state).map { catalog.actionIdFor(it.id) })
        }
        for (state in listOf(5, 7)) {
            assertEquals((11..19).toList(), catalog.selectableForState(state).map { catalog.actionIdFor(it.id) })
        }
        for (state in listOf(0, 6, 8, 9)) assertTrue(catalog.selectableForState(state).isEmpty())
    }

    @Test
    fun `all dofs enforce slot types even for stale saved mappings`() {
        for (dof in HandDof.entries) {
            val mapping = ActionMapping(dof = dof, gestureIdByState = mapOf(
                1 to "pointing", 2 to "phone", 5 to "trigger_open", 6 to "trigger_closed",
                7 to "phone", 8 to "fist",
            ))
            val phoneId = mapping.catalog.actionIdFor("phone") ?: error("phone missing in $dof")
            assertArrayEquals(intArrayOf(99, 99, 99, 99, 99, 99, phoneId, 99), mapping.toActionIds())
            for (state in listOf(6, 8)) assertEquals(ActionSlotMode.DISABLED, mapping.slotModeFor(state))
            assertEquals(mapping, mapping.assignGesture(1, "pointing"))
            assertEquals(mapping, mapping.assignGesture(5, "trigger_open"))
        }
    }

    @Test
    fun `six dof sends paired actions and single 18 with disabled companions`() {
        val mapping = ActionMapping(dof = HandDof.DOF_6)
            .assignGesture(1, "trigger_closed")
            .assignGesture(3, "tip_pinch_open")
            .assignGesture(5, "pointing")
            .assignGesture(7, "angle")
        assertArrayEquals(intArrayOf(9, 10, 3, 4, 11, 99, 19, 99), mapping.toActionIds())
        val frame = MarkSevenProtocol.buildMset(
            dof = 6,
            actionIds = mapping.toActionIds(),
            maxCurrentMa = IntArray(6) { 600 },
            motorSpeed = IntArray(6),
            emgAmp = IntArray(2),
        )
        assertArrayEquals(byteArrayOf(9, 10, 3, 4, 11, 99, 19, 99), frame.copyOfRange(2, 10))
    }

    @Test
    fun `other dofs use their own single catalogs`() {
        val five = GestureCatalogs.DOF_5
        val seven = GestureCatalogs.DOF_7
        assertEquals((11..19).toList(), five.selectableForState(5).map { five.actionIdFor(it.id) })
        assertEquals((1..14).toList(), seven.selectableForState(1).map { seven.actionIdFor(it.id) })
        assertEquals((15..24).toList(), seven.selectableForState(5).map { seven.actionIdFor(it.id) })
    }
}
