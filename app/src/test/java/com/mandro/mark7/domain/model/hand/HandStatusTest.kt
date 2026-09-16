package com.mandro.mark7.domain.model.hand

import org.junit.Assert.assertEquals
import org.junit.Test

/** STATUS voltage 원시값(uint8) → 표시 전압: 원시값 ÷ 10, 소수 첫째 자리까지. */
class HandStatusTest {

    @Test
    fun `voltage is raw divided by 10 shown to one decimal`() {
        assertEquals("25.5", HandStatus.formatVoltage(255))
        assertEquals("12.7", HandStatus.formatVoltage(127))
        assertEquals("20.0", HandStatus.formatVoltage(200))
        assertEquals("20.1", HandStatus.formatVoltage(201))
        assertEquals("3.9", HandStatus.formatVoltage(39))
        assertEquals("0.1", HandStatus.formatVoltage(1))
        assertEquals("0.0", HandStatus.formatVoltage(0))
    }

    @Test
    fun `voltage text follows the status voltage byte`() {
        val status = HandStatus(
            dof = 6,
            motorTemp = IntArray(6),
            motorTurn = IntArray(6),
            emg = intArrayOf(0, 0),
            voltage = 127,
            checksumOk = true,
        )

        assertEquals("12.7", status.voltageText)
    }
}
