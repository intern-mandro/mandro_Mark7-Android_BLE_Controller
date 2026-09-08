package com.mandro.mark7.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HandDofTest {

    @Test
    fun `HandDof entries have correct dof counts and motor lists`() {
        assertEquals(5, HandDof.DOF_5.dof)
        assertEquals(5, HandDof.DOF_5.motorNameRes.size)
        assertEquals(5, HandDof.DOF_5.motorShortRes.size)
        assertNull(HandDof.DOF_5.badgeRes)

        assertEquals(6, HandDof.DOF_6.dof)
        assertEquals(6, HandDof.DOF_6.motorNameRes.size)
        assertEquals(6, HandDof.DOF_6.motorShortRes.size)
        assertNotNull(HandDof.DOF_6.badgeRes)

        assertEquals(7, HandDof.DOF_7.dof)
        assertEquals(7, HandDof.DOF_7.motorNameRes.size)
        assertEquals(7, HandDof.DOF_7.motorShortRes.size)
        assertNotNull(HandDof.DOF_7.badgeRes)
    }

    @Test
    fun `fromDof returns corresponding HandDof or DEFAULT`() {
        assertEquals(HandDof.DOF_5, HandDof.fromDof(5))
        assertEquals(HandDof.DOF_6, HandDof.fromDof(6))
        assertEquals(HandDof.DOF_7, HandDof.fromDof(7))
        assertEquals(HandDof.DOF_6, HandDof.fromDof(999))
    }

    @Test
    fun `fromId returns corresponding HandDof or DEFAULT`() {
        assertEquals(HandDof.DOF_5, HandDof.fromId("dof_5"))
        assertEquals(HandDof.DOF_6, HandDof.fromId("dof_6"))
        assertEquals(HandDof.DOF_7, HandDof.fromId("dof_7"))
        assertEquals(HandDof.DOF_6, HandDof.fromId(null))
        assertEquals(HandDof.DOF_6, HandDof.fromId("unknown"))
    }
}
