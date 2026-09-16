package com.mandro.mark7.core.ble

import com.mandro.mark7.domain.model.hand.HandDof
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectedDofDetectorTest {

    @Test
    fun `detects every supported STATUS dof`() {
        val detector = ConnectedDofDetector()

        detector.update(5)
        assertEquals(HandDof.DOF_5, detector.dof.value)

        detector.update(6)
        assertEquals(HandDof.DOF_6, detector.dof.value)

        detector.update(7)
        assertEquals(HandDof.DOF_7, detector.dof.value)
    }

    @Test
    fun `ignores unsupported dof values`() {
        val detector = ConnectedDofDetector()
        detector.update(6)

        detector.update(8)

        assertEquals(HandDof.DOF_6, detector.dof.value)
    }

    @Test
    fun `reset clears the connected hand dof`() {
        val detector = ConnectedDofDetector()
        detector.update(7)

        detector.reset()

        assertNull(detector.dof.value)
    }
}
