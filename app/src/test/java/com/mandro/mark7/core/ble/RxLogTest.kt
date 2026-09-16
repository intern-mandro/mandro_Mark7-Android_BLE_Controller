package com.mandro.mark7.core.ble

import org.junit.Assert.assertEquals
import org.junit.Test

/** STATUS 수신 로그 형식 — `scripts/capture-emg-status.ps1` 이 이 줄을 읽는다. */
class RxLogTest {

    private fun statusFrame(dof: Int, temp: IntArray, turn: IntArray, emg: IntArray, voltage: Int): ByteArray {
        val b = ByteArray(MarkSevenProtocol.STATUS_SIZE)
        b[0] = MarkSevenProtocol.HDR_STATUS.toByte()
        b[1] = dof.toByte()
        for (i in 0 until dof) {
            b[2 + i] = temp[i].toByte()
            b[9 + i] = turn[i].toByte()
        }
        b[16] = emg[0].toByte()
        b[17] = emg[1].toByte()
        b[18] = voltage.toByte()
        b[19] = MarkSevenProtocol.xor(b, 1, 19)
        return b
    }

    @Test
    fun `hex prints every byte as two upper-case digits`() {
        assertEquals("F1 06 00 FF", RxLog.hex(byteArrayOf(0xF1.toByte(), 0x06, 0x00, 0xFF.toByte())))
    }

    @Test
    fun `status line carries parsed fields and the whole raw frame`() {
        val frame = statusFrame(
            dof = 6,
            temp = intArrayOf(38, 41, 46, 43, 62, 39),
            turn = intArrayOf(10, 200, 128, 255, 0, 77),
            emg = intArrayOf(5, 6),
            voltage = 123,
        )

        val line = RxLog.statusLine(frame, MarkSevenProtocol.parseStatus(frame))

        assertEquals(
            "RX frame: STATUS 20B — dof=6 temp=[38, 41, 46, 43, 62, 39] turn=[10, 200, 128, 255, 0, 77] " +
                "emg=[5, 6] voltage=123 chkOk=true raw=${RxLog.hex(frame)}",
            line,
        )
    }

    @Test
    fun `status line keeps the raw frame when parsing fails`() {
        val frame = ByteArray(MarkSevenProtocol.STATUS_SIZE).also {
            it[0] = MarkSevenProtocol.HDR_STATUS.toByte()
            it[1] = 9 // 범위 밖 dof
        }

        assertEquals(
            "RX frame: STATUS 20B — parse FAILED raw=${RxLog.hex(frame)}",
            RxLog.statusLine(frame, null),
        )
    }
}
