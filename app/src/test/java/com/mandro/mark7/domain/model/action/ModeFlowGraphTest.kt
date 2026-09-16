package com.mandro.mark7.domain.model.action
import com.mandro.mark7.domain.model.hand.HandDof
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 상태 다이어그램 정의와 매핑 헬퍼 검증.
 * `docs/mode_flow.png` = idle(S0) + 상태 4개, 두 체인. 상태 수는 고정.
 */
class ModeFlowGraphTest {

    private val graph = ModeFlowGraph.DEFAULT
    private val catalog = GestureCatalogs.forDof(HandDof.DEFAULT)

    @Test
    fun `graph has idle plus exactly four more states`() {
        assertEquals((0..4).toList(), graph.states.map { it.id }.sorted())
        assertEquals(4, graph.states.count { !it.fixed })
    }

    @Test
    fun `only S0 is fixed and it is the idle state id`() {
        assertEquals(listOf(0), graph.states.filter { it.fixed }.map { it.id })
        assertEquals(0, ModeFlowGraph.IDLE_STATE_ID)
    }

    @Test
    fun `state layout coordinates stay within the normalized 0 to 1 box`() {
        graph.states.forEach { s ->
            assertTrue("S${s.id}.x=${s.x}", s.x in 0f..1f)
            assertTrue("S${s.id}.y=${s.y}", s.y in 0f..1f)
        }
    }

    @Test
    fun `every transition connects two declared states without self loops`() {
        val ids = graph.states.map { it.id }.toSet()
        graph.transitions.forEach { t ->
            assertTrue("from ${t.from}", t.from in ids)
            assertTrue("to ${t.to}", t.to in ids)
            assertTrue("self loop on ${t.from}", t.from != t.to)
        }
    }

    @Test
    fun `each state has at most one outgoing edge per input`() {
        graph.transitions
            .groupBy { it.from to it.input }
            .forEach { (key, edges) -> assertEquals("duplicate edge for $key", 1, edges.size) }
    }

    @Test
    fun `every state has at least one outgoing transition`() {
        graph.states.forEach { s ->
            assertTrue("S${s.id} has no outgoing transition", graph.transitions.any { it.from == s.id })
        }
    }

    @Test
    fun `idle branches to a flexion chain and an extension chain`() {
        val fromIdle = graph.transitions.filter { it.from == ModeFlowGraph.IDLE_STATE_ID }
        assertEquals(
            setOf(ModeInput.FLEXION, ModeInput.EXTENSION),
            fromIdle.map { it.input }.toSet(),
        )
    }

    @Test
    fun `every non-idle state can walk back toward idle`() {
        // 각 상태에서 E 를 반복하면 결국 S1 에 도달해야 한다.
        graph.states.filter { !it.fixed }.forEach { start ->
            var cur = start.id
            var guard = 0
            while (cur != ModeFlowGraph.IDLE_STATE_ID && guard++ < 10) {
                val back = graph.transitions.firstOrNull { it.from == cur && it.input == ModeInput.EXTENSION }
                assertTrue("S$cur 에서 E 로 나갈 수 없음", back != null)
                cur = back!!.to
            }
            assertEquals("S${start.id} 에서 idle 로 못 돌아옴", ModeFlowGraph.IDLE_STATE_ID, cur)
        }
    }

    @Test
    fun `default temp gestures stay within S1 to S8 and follow pair rules for every dof`() {
        HandDof.entries.forEach { dof ->
            val defaults = GestureCatalogs.forDof(dof).defaultStateGestures
            assertTrue("$dof", defaults.isNotEmpty() && defaults.keys.all { it in 1..8 })

            // 기본값도 Pair 규칙을 따라야 저장값과 화면 표시가 어긋나지 않는다.
            val mapping = ActionMapping(gestureIdByState = defaults, dof = dof)
            defaults.forEach { (stateId, id) ->
                assertEquals("$dof S$stateId", id, mapping.effectiveGestureIdFor(stateId))
            }
        }
    }

    @Test
    fun `default dof catalog follows action_list order`() {
        assertEquals(
            listOf(
                "flat_hand",
                "cylinder_grip_open", "cylinder_grip_closed",
                "tip_pinch_open", "tip_pinch_closed",
                "lateral_pinch_open", "lateral_pinch_closed",
                "tripod_open", "tripod_closed",
                "trigger_open", "trigger_closed",
                "pointing", "phone", "peace", "three_finger_salute", "fist", "middle_finger",
                "eleven", "scissor", "angle",
            ),
            catalog.gestures.map { it.id },
        )
        assertEquals((0..19).toList(), catalog.gestures.map { catalog.actionIdFor(it.id) })
    }

    @Test
    fun `action slots are managed as four primary and companion pairs`() {
        assertEquals(
            listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8),
            ActionSlotPairs.ALL.map { it.primaryStateId to it.companionStateId },
        )
    }

    @Test
    fun `actions 1 to 10 pair up in order and later actions stay single`() {
        val actual = (1..16).associateWith { actionId ->
            catalog.slotGesturesFor(catalog.gestures[actionId].id)
                ?.let { slots -> catalog.actionIdFor(slots.lead.id) to slots.companion?.let { catalog.actionIdFor(it.id) } }
        }

        assertEquals(
            mapOf(
                1 to (1 to 2), 2 to (1 to 2),
                3 to (3 to 4), 4 to (3 to 4),
                5 to (5 to 6), 6 to (5 to 6),
                7 to (7 to 8), 8 to (7 to 8),
                9 to (9 to 10), 10 to (9 to 10),
                11 to (11 to null), 12 to (12 to null), 13 to (13 to null),
                14 to (14 to null), 15 to (15 to null), 16 to (16 to null),
            ),
            actual,
        )
    }

    @Test
    fun `picker candidates exclude only flat hand`() {
        assertEquals(catalog.gestures.filter { it.id != catalog.idle.id }, catalog.selectable)
    }

    @Test
    fun `flat hand cannot be assigned to any slot`() {
        assertNull(catalog.slotGesturesFor("flat_hand"))
        assertEquals(ActionMapping(), ActionMapping().assignGesture(primaryStateId = 1, gestureId = "flat_hand"))
    }

    @Test
    fun `selecting front member of a pair fills primary then companion`() {
        val mapping = ActionMapping().assignGesture(primaryStateId = 1, gestureId = "cylinder_grip_open")

        assertEquals("cylinder_grip_open", mapping.effectiveGestureIdFor(1))
        assertEquals("cylinder_grip_closed", mapping.effectiveGestureIdFor(2))
        assertEquals(ActionSlotMode.EDITABLE, mapping.slotModeFor(1))
        assertEquals(ActionSlotMode.AUTO_ASSIGNED, mapping.slotModeFor(2))
        assertArrayEquals(intArrayOf(1, 2, 99, 99, 99, 99, 99, 99), mapping.toActionIds())
    }

    @Test
    fun `selecting back member of a pair still places the pair in order`() {
        val mapping = ActionMapping().assignGesture(primaryStateId = 3, gestureId = "trigger_closed")

        assertEquals("trigger_open", mapping.effectiveGestureIdFor(3))
        assertEquals("trigger_closed", mapping.effectiveGestureIdFor(4))
        assertArrayEquals(intArrayOf(99, 99, 9, 10, 99, 99, 99, 99), mapping.toActionIds())
    }

    @Test
    fun `selecting single action takes primary and disables companion`() {
        val mapping = ActionMapping()
            .assignGesture(primaryStateId = 5, gestureId = "pointing")

        assertEquals("pointing", mapping.effectiveGestureIdFor(5))
        assertNull(mapping.gestureIdFor(6))
        assertEquals(ActionSlotMode.DISABLED, mapping.slotModeFor(6))
        assertArrayEquals(intArrayOf(99, 99, 99, 99, 11, 99, 99, 99), mapping.toActionIds())
    }

    @Test
    fun `back node opens the picker only while a pair fills it`() {
        val pair = ActionMapping().assignGesture(primaryStateId = 1, gestureId = "cylinder_grip_open")
        val single = ActionMapping().assignGesture(primaryStateId = 5, gestureId = "pointing")

        assertTrue(pair.slotModeFor(1).opensPicker)
        assertTrue("Pair 로 채워진 뒤 노드", pair.slotModeFor(2).opensPicker)
        assertFalse("단일 액션 옆 비활성 노드", single.slotModeFor(6).opensPicker)
        assertFalse("비어 있는 묶음의 뒤 노드", ActionMapping().slotModeFor(2).opensPicker)
    }

    @Test
    fun `stale companion values never override app managed slot rules`() {
        val mapping = ActionMapping(
            gestureIdByState = mapOf(
                5 to "pointing",
                6 to "phone",
            ),
        )

        assertNull(mapping.effectiveGestureIdFor(6))
        assertArrayEquals(intArrayOf(99, 99, 99, 99, 11, 99, 99, 99), mapping.toActionIds())
    }

    @Test
    fun `stored back member in primary slot is normalized to pair order`() {
        val mapping = ActionMapping(gestureIdByState = mapOf(3 to "lateral_pinch_closed"))

        assertEquals("lateral_pinch_open", mapping.effectiveGestureIdFor(3))
        assertEquals("lateral_pinch_closed", mapping.effectiveGestureIdFor(4))
        assertArrayEquals(intArrayOf(99, 99, 5, 6, 99, 99, 99, 99), mapping.toActionIds())
    }

    @Test
    fun `selected gesture action ids are emitted for S1 to S8`() {
        val mapping = ActionMapping(
            gestureIdByState = mapOf(
                1 to "tip_pinch_open",
                2 to "cylinder_grip_closed",
                3 to "middle_finger",
                4 to "phone",
                5 to "three_finger_salute",
                6 to "pointing",
                7 to "flat_hand",
                8 to "unknown",
            ),
        )

        assertArrayEquals(
            intArrayOf(3, 4, 99, 99, 14, 99, 99, 99),
            mapping.toActionIds(),
        )
    }

    @Test
    fun `MODE_2 has idle plus S5 to S8 states`() {
        val g2 = ModeFlowGraph.MODE_2
        assertEquals(listOf(0, 5, 6, 7, 8), g2.states.map { it.id }.sorted())
        assertEquals(4, g2.states.count { !it.fixed })
    }

    @Test
    fun `gestureIdFor returns stored assignment or null`() {
        val mapping = ActionMapping(gestureIdByState = mapOf(3 to "close"))
        assertEquals("close", mapping.gestureIdFor(3))
        assertNull(mapping.gestureIdFor(4))
    }

    @Test
    fun `GestureCatalog byId resolves catalog entries idle and unknowns`() {
        assertEquals("fist", catalog.byId("close")?.id)
        assertEquals(15, catalog.actionIdFor("close"))
        assertEquals(catalog.idle, catalog.byId("idle"))
        assertNull(catalog.byId("nope"))
        assertNull(catalog.byId(null))
    }
}
