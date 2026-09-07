package com.mandro.mark7.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 상태 다이어그램 정의와 매핑 헬퍼 검증.
 * `docs/mode_flow.png` = idle(S1) + 상태 4개, 두 체인. 상태 수는 고정.
 */
class ModeFlowGraphTest {

    private val graph = ModeFlowGraph.DEFAULT

    @Test
    fun `graph has idle plus exactly four more states`() {
        assertEquals((1..5).toList(), graph.states.map { it.id }.sorted())
        assertEquals(4, graph.states.count { !it.fixed })
    }

    @Test
    fun `only S1 is fixed and it is the idle state id`() {
        assertEquals(listOf(1), graph.states.filter { it.fixed }.map { it.id })
        assertEquals(1, ModeFlowGraph.IDLE_STATE_ID)
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
    fun `default temp gestures cover S2 to S9 and reference real catalog ids`() {
        val catalogIds = GestureCatalog.ALL.map { it.id }.toSet()
        assertEquals(setOf(2, 3, 4, 5, 6, 7, 8, 9), DEFAULT_STATE_GESTURES.keys)
        DEFAULT_STATE_GESTURES.values.forEach { id -> assertTrue("$id not in catalog", id in catalogIds) }
    }

    @Test
    fun `MODE_2 has idle plus S6 to S9 states`() {
        val g2 = ModeFlowGraph.MODE_2
        assertEquals(listOf(1, 6, 7, 8, 9), g2.states.map { it.id }.sorted())
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
        assertEquals("close", GestureCatalog.byId("close")?.id)
        assertEquals(GestureCatalog.IDLE, GestureCatalog.byId("idle"))
        assertNull(GestureCatalog.byId("nope"))
        assertNull(GestureCatalog.byId(null))
    }
}
