package com.mandro.mark7.core.ble

import com.mandro.mark7.domain.model.CmdDir
import com.mandro.mark7.domain.model.GlobalSettings
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandPattern
import com.mandro.mark7.domain.model.HandStatus

/**
 * Mark7 로봇 의수 BLE 프로토콜 코덱.
 *
 * 기존 MandroProject 앱(`core/ble/MandroProtocol.kt`)과 목적은 같지만 프레임 구조가
 * 완전히 다르다. 근거는 Mark7 펌웨어(`Mark7_T4_modular_20260625/ble.cpp`)와 PC 설정
 * 도구(`Mark7_BLE_GUI/ble/hand.py`). 자세한 표는 `docs/PROTOCOL.md`.
 *
 * 전송 (App → 의수)
 *   - CMD  12 byte : HDR(0xFF) + sel + spd + cur + pos[6] + dir + XOR(1..10)
 *   - SET 117 byte : HDR(0xEF) + 패턴 8개(64) + 전역설정(51) + XOR(1..115)
 * 수신 (의수 → App)
 *   - STATUS 36 byte : temp[6] + curAvg[6] + turn[6] + emg[2] + XOR(0..34)  (헤더 없음)
 *   - ACK     5 byte : "SETok"
 *
 * 바이트 스트림은 HM-10류 시리얼 브리지(Service 0xFFE0 / Char 0xFFE1) 위로 그대로
 * 흐른다 — GATT characteristic 분리는 없다.
 */
object MarkSevenProtocol {

    const val DOF = 6
    const val EMG_CH = 2
    const val PATTERN_COUNT = 8

    const val HDR_CMD = 0xFF
    const val HDR_SET = 0xEF

    const val CMD_SIZE = 12
    const val SET_SIZE = 117
    const val STATUS_SIZE = 36

    /** STATUS byte34 하위 니블 = 현재 상태 인덱스(MSS0..MSS8). */
    const val STATE_INDEX_MAX = 8
    val ACK_SET = byteArrayOf(0x53, 0x45, 0x54, 0x6F, 0x6B) // "SETok"

    /** 펌웨어 `ble.cpp` 값 역산 상수. */
    private const val SPD_STEP = 200          // spd  = byte * 200
    private const val CUR_BASE = 600          // cur  = byte * 3 + 600
    private const val CUR_STEP = 3
    private const val POS_BASE = -21          // pos  = byte * 2 + (-21)  (역: (pos - (-21)) / 2)  ※ hand.py 는 pos 원바이트 전송
    private const val POS_STEP = 2
    private const val SL_BASE = 6000          // sl_setting = byte * 100 + 6000
    private const val SL_STEP = 100
    const val HIGHEST_CURRENT_LIMIT = 1500

    // ──────────────────────────────────────────────────────────────
    // 전송: CMD (직접 손가락 구동)
    /**
     * 펌웨어 `ble.cpp:67`는 pos = (-21 + byte * 2)로 역산한다.
     * byte가 0이면 pos는 -21이 되고, 펌웨어 `serial.cpp`에서 uint8_t 오버플로우(235)가
     * 발생하여 RELEASE(펴기) 동작 시 `STOP BY POS3` 조건에 즉시 걸려 모터가 멈추는 치명적 버그가 발생한다.
     * 따라서 `send_cmd.py` 및 `exo_armband_hybrid.ino` 표준 프리셋과 동일하게
     * 선택된 손가락의 기본 위치 바이트는 0x90 (144 -> 펌웨어 위치 267)을 사용한다.
     */
    const val DEFAULT_CMD_POS_BYTE = 0x90

    /**
     * @param select    길이 6 BooleanArray. index 0 = F1(엄지) … 5 = F6(엄지외전).
     *                  펌웨어가 bit i → motor(DOF-1-i)로 되돌리므로 여기서 뒤집어 담는다.
     * @param speedRaw  0 이면 "속도 미지정". 아니면 실제 rpm 대략값 → byte = raw / 200 (0..255).
     * @param currentMa 0 이면 "전류 미지정". 아니면 mA → byte = (mA - 600) / 3.
     * @param posDeg    길이 6. 펌웨어 환산 pos = byte*2 - 21 → byte = ((pos + 21) / 2). 0 이면 DEFAULT_CMD_POS_BYTE(0x90).
     * @param dir       STOP / GRASP / RELEASE / RESET_COUNTER / RESET_POWER
     */
    fun buildCmd(
        select: BooleanArray,
        speedRaw: Int,
        currentMa: Int,
        posDeg: IntArray,
        dir: CmdDir,
    ): ByteArray {
        require(select.size == DOF) { "select must be length $DOF" }
        require(posDeg.size == DOF) { "posDeg must be length $DOF" }

        val buf = ByteArray(CMD_SIZE)
        buf[0] = HDR_CMD.toByte()

        var sel = 0
        for (i in 0 until DOF) {
            if (select[i]) sel = sel or (1 shl ((DOF - 1) - i))
        }
        buf[1] = sel.toByte()
        buf[2] = if (speedRaw <= 0) 0 else (speedRaw / SPD_STEP).coerceIn(0, 255).toByte()
        buf[3] = if (currentMa <= 0) 0 else ((currentMa - CUR_BASE) / CUR_STEP).coerceIn(0, 255).toByte()
        val isMotion = dir == CmdDir.GRASP || dir == CmdDir.RELEASE
        for (i in 0 until DOF) {
            val b = when {
                !select[i] -> 0
                !isMotion -> 0
                posDeg[i] > 0 -> ((posDeg[i] - POS_BASE) / POS_STEP).coerceIn(0, 255)
                else -> DEFAULT_CMD_POS_BYTE
            }
            buf[4 + i] = b.toByte()
        }
        buf[10] = dir.wire.toByte()
        buf[11] = xor(buf, 1, CMD_SIZE - 1)
        return buf
    }

    // ──────────────────────────────────────────────────────────────
    // 전송: SET (패턴 8개 + 전역 설정)
    // ──────────────────────────────────────────────────────────────

    fun buildSet(config: HandConfig): ByteArray {
        val buf = ByteArray(SET_SIZE)
        buf[0] = HDR_SET.toByte()

        // 패턴 8개 — byte 1..64
        for (p in 0 until PATTERN_COUNT) {
            val base = 1 + p * 8
            val pat = config.patterns.getOrElse(p) { HandPattern.EMPTY }
            var flags = 0
            if (pat.valid) flags = flags or 0x01
            if (pat.gradual) flags = flags or 0x02   // 펌웨어 transitionTable 의 isGradual 대응 (mode1 계열)
            buf[base] = flags.toByte()

            var bitmask = 0
            for (i in 0 until DOF) if (pat.motor[i]) bitmask = bitmask or (1 shl i)
            buf[base + 1] = bitmask.toByte()

            for (i in 0 until DOF) {
                val order = pat.order[i].coerceIn(0, 15)
                val delayStep = (pat.delayMs[i] / 16).coerceIn(0, 15)
                buf[base + 2 + i] = ((delayStep shl 4) or order).toByte()
            }
        }

        val g = config.settings
        putArray(buf, 65) { i -> ((g.graspCurrent[i] - CUR_BASE) / CUR_STEP) }
        putArray(buf, 71) { i -> ((g.releaseCurrent[i] - CUR_BASE) / CUR_STEP) }
        putArray(buf, 77) { i -> ((g.maxCurrent[i] - CUR_BASE) / CUR_STEP) }
        putArray(buf, 83) { i -> ((g.slSetting[i] - SL_BASE) / SL_STEP) }
        putArray(buf, 89) { i -> (g.slGradual[i] / SL_STEP) }
        putArray(buf, 95) { i -> (g.graspPos[i] / POS_STEP) }
        putArray(buf, 101) { i -> (g.releasePos[i] / POS_STEP) }
        putArray(buf, 107) { i -> g.motorSpeed[i] }

        buf[113] = g.emgAmp[0].coerceIn(0, 20).toByte()
        buf[114] = g.emgAmp[1].coerceIn(0, 20).toByte()
        buf[115] = g.emgFilter.coerceIn(0, 90).toByte()

        buf[SET_SIZE - 1] = xor(buf, 1, SET_SIZE - 1)
        return buf
    }

    // ──────────────────────────────────────────────────────────────
    // 수신: STATUS (헤더 없음, 길이로만 경계)
    // ──────────────────────────────────────────────────────────────

    fun parseStatus(data: ByteArray): HandStatus? {
        if (data.size < STATUS_SIZE) return null

        val temp = IntArray(DOF) { data[it].u() }
        val currentAvg = IntArray(DOF) { i -> beU16(data, 6 + i * 2) }
        val turn = IntArray(DOF) { i -> beS16(data, 18 + i * 2) }
        val emg = IntArray(EMG_CH) { i -> beU16(data, 30 + i * 2) }
        // TODO(protocol): byte34 = [상위 니블] program_mode(0→M1,1→M2) + [하위 니블] 현재 MSS.
        //  실기기로 확인 필요.
        val raw34 = data[34].u()
        val state = (raw34 and 0x0F).takeIf { it in 0..STATE_INDEX_MAX }
        val mode = ((raw34 shr 4) and 0x0F).let { if (it in 0..1) it + 1 else null }

        val checksumOk = xor(data, 0, STATUS_SIZE - 1) == data[STATUS_SIZE - 1]
        return HandStatus(
            motorTemp = temp,
            motorCurrentAvg = currentAvg,
            motorTurn = turn,
            emg = emg,
            currentState = state,
            programMode = mode,
            checksumOk = checksumOk,
        )
    }

    fun isAck(data: ByteArray): Boolean =
        data.size >= ACK_SET.size && data.copyOfRange(0, ACK_SET.size).contentEquals(ACK_SET)

    // ──────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────

    /** XOR of buf[from until to] (to exclusive). */
    fun xor(buf: ByteArray, from: Int, to: Int): Byte {
        var acc = 0
        for (i in from until to) acc = acc xor (buf[i].toInt() and 0xFF)
        return acc.toByte()
    }

    private inline fun putArray(buf: ByteArray, offset: Int, value: (Int) -> Int) {
        for (i in 0 until DOF) buf[offset + i] = value(i).coerceIn(0, 255).toByte()
    }

    private fun Byte.u(): Int = this.toInt() and 0xFF
    private fun beU16(b: ByteArray, i: Int): Int = (b[i].u() shl 8) or b[i + 1].u()
    private fun beS16(b: ByteArray, i: Int): Int {
        val v = beU16(b, i)
        return if (v > 32767) v - 65536 else v
    }
}
