package com.mandro.mark7.domain.model.action

import com.mandro.mark7.domain.model.hand.HandDof
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 자유도별 손 모양 목록([GestureCatalogs])과 목록 한 개([GestureCatalog])의 규칙 검증. */
class GestureCatalogTest {

    @Test
    fun `each dof has its own catalog`() {
        assertNotSame(GestureCatalogs.forDof(HandDof.DOF_5), GestureCatalogs.forDof(HandDof.DOF_6))
        assertNotSame(GestureCatalogs.forDof(HandDof.DOF_6), GestureCatalogs.forDof(HandDof.DOF_7))
        assertNotSame(GestureCatalogs.forDof(HandDof.DOF_5), GestureCatalogs.forDof(HandDof.DOF_7))
    }

    @Test
    fun `every dof catalog starts with flat hand as idle`() {
        HandDof.entries.forEach { dof ->
            val catalog = GestureCatalogs.forDof(dof)
            assertEquals("$dof", Gestures.FLAT_HAND, catalog.idle)
            assertEquals("$dof", 0, catalog.actionIdFor(Gestures.FLAT_HAND.id))
            assertTrue("$dof", catalog.selectable.none { it == Gestures.FLAT_HAND })
        }
    }

    @Test
    fun `mapping uses the catalog of its own dof`() {
        HandDof.entries.forEach { dof ->
            assertSame("$dof", GestureCatalogs.forDof(dof), ActionMapping(dof = dof).catalog)
        }
    }

    @Test
    fun `action ids follow the group order`() {
        val catalog = GestureCatalog(
            assetFolder = TEST_FOLDER,
            groups = listOf(GestureSlots(Gestures.POINTING, Gestures.FIST), single(Gestures.PHONE)),
        )

        assertEquals(listOf(Gestures.FLAT_HAND, Gestures.POINTING, Gestures.FIST, Gestures.PHONE), catalog.gestures)
        assertEquals(3, catalog.actionIdFor(Gestures.PHONE.id))
        assertNull("목록에 없는 손 모양", catalog.actionIdFor(Gestures.PEACE.id))

        val pair = catalog.slotGesturesFor(Gestures.FIST.id)
        assertEquals(Gestures.POINTING, pair?.lead)
        assertEquals(Gestures.FIST, pair?.companion)

        val single = catalog.slotGesturesFor(Gestures.PHONE.id)
        assertEquals(Gestures.PHONE, single?.lead)
        assertNull(single?.companion)
    }

    @Test
    fun `a dof can use its own pair and single layout`() {
        // 예: 3 Pair + 4 단일
        val catalog = GestureCatalog(
            assetFolder = TEST_FOLDER,
            groups = with(Gestures) {
                listOf(
                    GestureSlots(CYLINDER_GRIP_OPEN, CYLINDER_GRIP_CLOSED),
                    GestureSlots(TIP_PINCH_OPEN, TIP_PINCH_CLOSED),
                    GestureSlots(TRIGGER_OPEN, TRIGGER_CLOSED),
                    single(POINTING), single(PHONE), single(FIST), single(PEACE),
                )
            },
        )

        assertEquals(
            listOf(1 to 2, 3 to 4, 5 to 6, 7 to null, 8 to null, 9 to null, 10 to null),
            layoutOf(catalog),
        )
    }

    @Test
    fun `pairs are exactly what the list groups, not odd and even numbers`() {
        val catalog = GestureCatalog(
            assetFolder = TEST_FOLDER,
            groups = listOf(single(Gestures.PHONE), GestureSlots(Gestures.TRIPOD_OPEN, Gestures.TRIPOD_CLOSED)),
        )

        assertEquals(listOf(1 to null, 2 to 3), layoutOf(catalog))
    }

    @Test
    fun `default state gestures place pairs above and singles below`() {
        val catalog = GestureCatalog(
            assetFolder = TEST_FOLDER,
            groups = listOf(single(Gestures.PHONE), GestureSlots(Gestures.TRIPOD_OPEN, Gestures.TRIPOD_CLOSED)),
        )

        assertEquals(mapOf(1 to "tripod_open", 2 to "tripod_closed", 5 to "phone"), catalog.defaultStateGestures)
    }

    @Test
    fun `same selection sends different action ids per dof catalog`() {
        val shortCatalog = GestureCatalog(assetFolder = TEST_FOLDER, groups = listOf(single(Gestures.PHONE)))
        val defaultCatalog = GestureCatalogs.forDof(HandDof.DEFAULT)

        assertEquals(1, shortCatalog.actionIdFor(Gestures.PHONE.id))
        assertEquals(12, defaultCatalog.actionIdFor(Gestures.PHONE.id))
    }

    @Test
    fun `catalog rejects lists that break the id rules`() {
        val duplicate = runCatching {
            GestureCatalog(assetFolder = TEST_FOLDER, groups = listOf(single(Gestures.FIST), single(Gestures.FIST)))
        }
        val idleInGroups = runCatching {
            GestureCatalog(assetFolder = TEST_FOLDER, groups = listOf(GestureSlots(Gestures.PHONE, Gestures.FLAT_HAND)))
        }

        assertTrue(duplicate.exceptionOrNull() is IllegalArgumentException)
        assertTrue("Flat Hand 는 0번에 자동으로 들어간다", idleInGroups.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `flat hand is fixed at action id 0 ahead of the listed actions`() {
        val catalog = GestureCatalog(assetFolder = TEST_FOLDER, groups = listOf(single(Gestures.PHONE), single(Gestures.FIST)))

        assertEquals(listOf(Gestures.FLAT_HAND, Gestures.PHONE, Gestures.FIST), catalog.gestures)
        assertEquals(Gestures.FLAT_HAND, catalog.idle)
        assertEquals(listOf(Gestures.PHONE, Gestures.FIST), catalog.selectable)
    }

    @Test
    fun `each dof keeps its photos in its own asset folder`() {
        assertEquals("mset_hand_shape/5dof", GestureCatalogs.forDof(HandDof.DOF_5).assetFolder)
        assertEquals("mset_hand_shape/6dof", GestureCatalogs.forDof(HandDof.DOF_6).assetFolder)
        assertEquals("mset_hand_shape/7dof", GestureCatalogs.forDof(HandDof.DOF_7).assetFolder)
    }

    @Test
    fun `photo path is folder plus two digit action id and gesture id`() {
        val catalog = GestureCatalog(assetFolder = TEST_FOLDER, groups = listOf(single(Gestures.PHONE)))

        assertEquals("$TEST_FOLDER/00_flat_hand.png", catalog.imageAssetPath(Gestures.FLAT_HAND))
        assertEquals("$TEST_FOLDER/01_phone.png", catalog.imageAssetPath(Gestures.PHONE))
        assertNull("목록에 없는 손 모양", catalog.imageAssetPath(Gestures.PEACE))
        assertEquals("mset_hand_shape/5dof/19_angle.jpg", GestureCatalogs.DOF_5.imageAssetPath(Gestures.ANGLE))
        assertEquals("mset_hand_shape/6dof/00_flat_hand.jpg", GestureCatalogs.DOF_6.imageAssetPath(Gestures.FLAT_HAND))
        assertEquals("mset_hand_shape/6dof/19_angle.jpg", GestureCatalogs.DOF_6.imageAssetPath(Gestures.ANGLE))
        assertEquals("mset_hand_shape/5dof/16_middle_finger.png", GestureCatalogs.DOF_5.imageAssetPath(Gestures.MIDDLE_FINGER))
        assertEquals("mset_hand_shape/6dof/16_middle_finger.png", GestureCatalogs.DOF_6.imageAssetPath(Gestures.MIDDLE_FINGER))
        assertEquals("mset_hand_shape/7dof/20_middle_finger.png", GestureCatalogs.DOF_7.imageAssetPath(Gestures.MIDDLE_FINGER))
    }

    @Test
    fun `seven dof pairs end at 14 and singles start at 15`() {
        val catalog = GestureCatalogs.DOF_7
        val pairs = catalog.groups.filter { it.companion != null }
        val singles = catalog.groups.filter { it.companion == null }

        assertEquals(7, pairs.size)
        assertEquals(10, singles.size)
        assertEquals("scissor_grip_open", pairs[5].lead.id)
        assertEquals("scissor_grip_close", pairs[5].companion?.id)
        assertEquals("three_finger_grip_open", pairs[6].lead.id)
        assertEquals("three_finger_grip_close", pairs[6].companion?.id)
        assertEquals((1..14).toList(), pairs.flatMap { listOfNotNull(it.lead, it.companion) }
            .map { catalog.actionIdFor(it.id) })
        assertEquals((15..24).toList(), singles.map { catalog.actionIdFor(it.lead.id) })
        assertEquals("mset_hand_shape/7dof/24_victory.png", catalog.imageAssetPath(singles.last().lead))
        assertEquals(listOf(99, 99), listOf(6, 8).map {
            ActionMapping(dof = HandDof.DOF_7).toActionIds()[it - 1]
        })
    }

    private fun single(gesture: Gesture) = GestureSlots(gesture, null)

    /** 선택 후보마다 (앞 액션 ID, 뒤 액션 ID) — 같은 묶음은 한 번만. */
    private fun layoutOf(catalog: GestureCatalog): List<Pair<Int?, Int?>> =
        catalog.selectable
            .mapNotNull { catalog.slotGesturesFor(it.id) }
            .distinct()
            .map { slots -> catalog.actionIdFor(slots.lead.id) to slots.companion?.let { catalog.actionIdFor(it.id) } }

    private companion object {
        const val TEST_FOLDER = "test_gesture_guides"
    }
}
