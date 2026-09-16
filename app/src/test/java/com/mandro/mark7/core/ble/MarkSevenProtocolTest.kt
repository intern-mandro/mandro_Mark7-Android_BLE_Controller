package com.mandro.mark7.core.ble

import com.mandro.mark7.domain.model.hand.CmdDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/Mark7_BLE_Protocol_detailed_final.pdf` 기준 바이트 단위 검증.
 * 프레임은 7DOF 크기 고정, `[1]` = dof, checksum = XOR(bytes[1 .. len-2]).
 */
class MarkSevenProtocolTest {

    // ── CMD (14B) ────────────────────────────────────────────────

    @Test
    fun `cmd frame is 14 bytes with header dof dir and valid checksum`() {
        val frame = MarkSevenProtocol.buildCmd(
            dof = 6,
            select = BooleanArray(6) { it == 1 },
            speedRaw = 0,
            currentMa = 0,
            posDeg = IntArray(6),
            dir = CmdDir.GRASP,
        )

        assertEquals(MarkSevenProtocol.CMD_SIZE, frame.size)
        assertEquals(0xFF.toByte(), frame[0])
        assertEquals(6.toByte(), frame[1])
        assertEquals(CmdDir.GRASP.wire.toByte(), frame[12])
        assertEquals(MarkSevenProtocol.xor(frame, 1, 13), frame[13])
    }

    @Test
    fun `cmd finger select bit order matches firmware reversal`() {
        // 펌웨어: cmd->sel[(dof-1)-i] = (buf[2] >> i) & 1  → F1(index 0) 켜면 최상위 비트
        val frame = MarkSevenProtocol.buildCmd(
            dof = 6,
            select = booleanArrayOf(true, false, false, false, false, false),
            speedRaw = 0, currentMa = 0, posDeg = IntArray(6), dir = CmdDir.RELEASE,
        )
        assertEquals(0b100000, frame[2].toInt() and 0xFF)
    }

    @Test
    fun `cmd fills selected fingers with pos preset and 0 for the rest and padding`() {
        val frame = MarkSevenProtocol.buildCmd(
            dof = 6,
            select = booleanArrayOf(false, true, true, false, false, false),
            speedRaw = 0, currentMa = 0, posDeg = IntArray(6), dir = CmdDir.GRASP,
        )
        assertEquals(0x00.toByte(), frame[5])                                    // F1 미선택
        assertEquals(MarkSevenProtocol.GRASP_POS_PRESET.toByte(), frame[6])      // F2 선택
        assertEquals(MarkSevenProtocol.GRASP_POS_PRESET.toByte(), frame[7])      // F3 선택
        assertEquals(0x00.toByte(), frame[8])                                    // F4 미선택
        assertEquals(0x00.toByte(), frame[11])                                   // 7번째 슬롯 = 패딩(6DOF)
    }

    @Test
    fun `grasp and release both send dir 1 and differ only by pos preset`() {
        fun cmd(dir: CmdDir) = MarkSevenProtocol.buildCmd(
            dof = 6, select = BooleanArray(6) { true },
            speedRaw = 0, currentMa = 0, posDeg = IntArray(6), dir = dir,
        )
        val grasp = cmd(CmdDir.GRASP)
        val release = cmd(CmdDir.RELEASE)

        assertEquals(1.toByte(), grasp[12])   // wire dir 통일
        assertEquals(1.toByte(), release[12])
        assertEquals(MarkSevenProtocol.GRASP_POS_PRESET.toByte(), grasp[5])
        assertEquals(MarkSevenProtocol.RELEASE_POS_PRESET.toByte(), release[5])
    }

    @Test
    fun `cmd posDeg override wins over preset when positive`() {
        val frame = MarkSevenProtocol.buildCmd(
            dof = 6, select = BooleanArray(6) { true },
            speedRaw = 0, currentMa = 0,
            posDeg = intArrayOf(30, 0, 0, 0, 0, 0), dir = CmdDir.GRASP,
        )
        assertEquals(30.toByte(), frame[5])                                     // override
        assertEquals(MarkSevenProtocol.GRASP_POS_PRESET.toByte(), frame[6])     // 프리셋
    }

    @Test
    fun `cmd 7dof fills all seven pos slots with preset`() {
        val frame = MarkSevenProtocol.buildCmd(
            dof = 7,
            select = BooleanArray(7) { true },
            speedRaw = 0, currentMa = 0, posDeg = IntArray(7), dir = CmdDir.GRASP,
        )
        assertEquals(7.toByte(), frame[1])
        for (i in 0 until 7) {
            assertEquals(MarkSevenProtocol.GRASP_POS_PRESET.toByte(), frame[5 + i])
        }
    }

    // ── MSET (27B) ───────────────────────────────────────────────

    @Test
    fun `mset frame is 27 bytes with 0xE7 header dof and valid checksum`() {
        val frame = MarkSevenProtocol.buildMset(
            dof = 6,
            actionIds = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            maxCurrentMa = IntArray(6) { 1200 },
            motorSpeed = IntArray(6) { 200 },
            emgAmp = intArrayOf(12, 10),
        )

        assertEquals(MarkSevenProtocol.MSET_SIZE, frame.size)
        assertEquals(0xE7.toByte(), frame[0])
        assertEquals(6.toByte(), frame[1])
        assertEquals(MarkSevenProtocol.xor(frame, 1, 26), frame[26])
    }

    @Test
    fun `mset encodes action ids currents speeds and emg`() {
        val frame = MarkSevenProtocol.buildMset(
            dof = 6,
            actionIds = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            maxCurrentMa = IntArray(6) { 1200 },
            motorSpeed = IntArray(6) { 200 },
            emgAmp = intArrayOf(12, 10),
        )

        assertEquals(1.toByte(), frame[2])                       // action_id[0]
        assertEquals(8.toByte(), frame[9])                       // action_id[7]
        assertEquals(((1200 - 600) / 3).toByte(), frame[10])     // max_current[0]
        assertEquals(0.toByte(), frame[16])                      // max_current[6] = 6DOF 패딩
        assertEquals(200.toByte(), frame[17])                    // motor_speed[0]
        assertEquals(0.toByte(), frame[23])                      // motor_speed[6] = 패딩
        assertEquals(12.toByte(), frame[24])                     // emg_amp[0]
        assertEquals(10.toByte(), frame[25])                     // emg_amp[1]
    }

    @Test
    fun `mset 7dof fills all seven current and speed slots`() {
        val frame = MarkSevenProtocol.buildMset(
            dof = 7,
            actionIds = IntArray(8),
            maxCurrentMa = IntArray(7) { 900 },
            motorSpeed = IntArray(7) { 128 },
            emgAmp = intArrayOf(0, 0),
        )
        assertEquals(7.toByte(), frame[1])
        assertEquals(((900 - 600) / 3).toByte(), frame[16]) // max_current[6] 채워짐
        assertEquals(128.toByte(), frame[23])               // motor_speed[6] 채워짐
    }

    // ── STATUS (20B) ─────────────────────────────────────────────

    /** `docs/STATUS_Protocol.png` STATUS 20B: hdr + dof + temp[7] + turn[7] + emg[2] + voltage + chk(XOR 1..18). */
    @Test
    fun `STATUS frame is fixed at 20 bytes`() {
        assertEquals(20, MarkSevenProtocol.STATUS_SIZE)
    }

    private fun statusFrame(
        dof: Int,
        temp: IntArray,
        turn: IntArray,
        emg: IntArray,
        voltage: Int = 0,
    ): ByteArray {
        val b = ByteArray(MarkSevenProtocol.STATUS_SIZE)
        b[0] = MarkSevenProtocol.HDR_STATUS.toByte()
        b[1] = dof.toByte()
        for (i in 0 until dof) {
            b[2 + i] = temp[i].toByte()   // temp[7] @ 2
            b[9 + i] = turn[i].toByte()   // turn[7] @ 9
        }
        b[16] = emg[0].toByte()
        b[17] = emg[1].toByte()
        b[18] = voltage.toByte()          // voltage @ 18
        b[19] = MarkSevenProtocol.xor(b, 1, 19)
        return b
    }

    @Test
    fun `parseStatus returns null for short buffer`() {
        assertNull(MarkSevenProtocol.parseStatus(ByteArray(10)))
    }

    @Test
    fun `parseStatus returns null when dof byte is out of range`() {
        val b = statusFrame(6, IntArray(6) { 40 }, IntArray(6) { 100 }, intArrayOf(10, 20))
        b[1] = 9 // 범위 밖
        b[19] = MarkSevenProtocol.xor(b, 1, 19)
        assertNull(MarkSevenProtocol.parseStatus(b))
    }

    @Test
    fun `parseStatus decodes dof temp turn and emg`() {
        val frame = statusFrame(
            dof = 6,
            temp = intArrayOf(38, 41, 46, 43, 62, 39),
            turn = intArrayOf(10, 200, 128, 255, 0, 77),
            emg = intArrayOf(120, 240),
            voltage = 123,
        )

        val status = MarkSevenProtocol.parseStatus(frame)!!

        assertEquals(6, status.dof)
        assertEquals(listOf(38, 41, 46, 43, 62, 39), status.motorTemp.toList())
        assertEquals(listOf(10, 200, 128, 255, 0, 77), status.motorTurn.toList())
        assertEquals(listOf(120, 240), status.emg.toList())
        assertEquals(123, status.voltage)
        assertTrue(status.checksumOk)
    }

    @Test
    fun `parseStatus reports checksum mismatch without discarding`() {
        val frame = statusFrame(7, IntArray(7) { 30 }, IntArray(7) { 5 }, intArrayOf(1, 2))
        frame[3] = (frame[3] + 1).toByte() // 체크섬 깨기

        val status = MarkSevenProtocol.parseStatus(frame)!!
        assertEquals(7, status.dof)
        assertTrue(!status.checksumOk)
    }

    @Test
    fun `voltage is byte 18 and is covered by the checksum`() {
        val frame = statusFrame(5, IntArray(5) { 30 }, IntArray(5) { 5 }, intArrayOf(1, 2), voltage = 200)

        val status = MarkSevenProtocol.parseStatus(frame)!!
        assertEquals(200, status.voltage)
        assertTrue(status.checksumOk)

        frame[18] = 201.toByte()
        assertTrue("voltage 바이트가 바뀌면 체크섬이 깨져야 한다", !MarkSevenProtocol.parseStatus(frame)!!.checksumOk)
    }

    // ── ACK ──────────────────────────────────────────────────────

    @Test
    fun `isAck recognises SETok prefix`() {
        assertTrue(MarkSevenProtocol.isAck("SETok".toByteArray()))
        assertTrue(MarkSevenProtocol.isAck("SETokextra".toByteArray()))
    }
}
