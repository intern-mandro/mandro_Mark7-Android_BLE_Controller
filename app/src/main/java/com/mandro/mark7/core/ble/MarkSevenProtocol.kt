package com.mandro.mark7.core.ble

import com.mandro.mark7.domain.model.hand.CmdDir
import com.mandro.mark7.domain.model.hand.HandStatus

object MarkSevenProtocol {

    const val MIN_DOF = 5

    const val MAX_DOF = 7
    const val EMG_CH = 2
    const val ACTION_ID_COUNT = 8

    const val HDR_CMD = 0xFF
    const val HDR_MSET = 0xE7
    const val HDR_STATUS = 0xF1

    // 프레임 크기 = 헤더1 + dof1 + 필드… + chk1  (전부 MAX_DOF 파생)
    const val CMD_SIZE = 7 + MAX_DOF                 // hdr+dof+sel+spd+cur + pos[N] + dir + chk
    const val MSET_SIZE = 13 + 2 * MAX_DOF           // hdr+dof + action_id[8] + max_current[N] + motor_speed[N] + emg[2] + chk
    const val STATUS_SIZE = 6 + 2 * MAX_DOF          // hdr+dof + temp[N] + turn[N] + emg[2] + voltage + chk

    // 필드 오프셋 (MAX_DOF 파생)
    private const val CMD_POS_OFF = 5
    private const val CMD_DIR_OFF = CMD_POS_OFF + MAX_DOF
    private const val MSET_ACTION_ID_OFF = 2
    private const val MSET_MAX_CURRENT_OFF = MSET_ACTION_ID_OFF + ACTION_ID_COUNT
    private const val MSET_MOTOR_SPEED_OFF = MSET_MAX_CURRENT_OFF + MAX_DOF
    private const val MSET_EMG_OFF = MSET_MOTOR_SPEED_OFF + MAX_DOF
    private const val STATUS_TEMP_OFF = 2
    private const val STATUS_TURN_OFF = STATUS_TEMP_OFF + MAX_DOF
    private const val STATUS_EMG_OFF = STATUS_TURN_OFF + MAX_DOF
    private const val STATUS_VOLTAGE_OFF = STATUS_EMG_OFF + EMG_CH

    val ACK_SET = byteArrayOf(0x53, 0x45, 0x54, 0x6F, 0x6B) // "SETok"

    private const val SPD_STEP = 200          // spd  = byte * 200
    private const val CUR_BASE = 600          // cur  = byte * 3 + 600
    private const val CUR_STEP = 3

    // EMG 민감도: 5·6·7DOF 공통 0 ~ 20, 기본 10
    const val EMG_AMP_MIN = 0
    const val EMG_AMP_MAX = 20
    const val EMG_AMP_DEFAULT = 10

    // 임시값
    const val GRASP_POS_PRESET = 0xC0
    const val RELEASE_POS_PRESET = 0x50

    // ──────────────────────────────────────────────────────────────
    // 전송: CMD (직접 손가락 구동) — 14 byte 고정
    // ──────────────────────────────────────────────────────────────

    /**
     * @param dof       연결된 의수 자유도(5..7). `[1]` 바이트로 실림.
     * @param select    손가락 선택. index 0 = F1(엄지). 펌웨어가 bit i → motor((dof-1)-i) 로 되돌리므로 뒤집어 담는다.
     * @param speedRaw  0 = 미지정. 아니면 byte = raw / 200 (0..255).
     * @param currentMa 0 = 미지정. 아니면 byte = (mA - 600) / 3.
     * @param posDeg    선택된 손가락별 pos 바이트 override. 값이 0 이하면 [GRASP_POS_PRESET] /
     *                  [RELEASE_POS_PRESET] 하드코딩 프리셋을 쓴다 (현재 UI 미연결 → 항상 프리셋).
     * @param dir       STOP / GRASP / RELEASE / RESET_COUNTER / RESET_POWER.
     *                  GRASP·RELEASE 는 wire 에서 둘 다 dir=1 로 나가고 pos 로만 구분.
     */
    fun buildCmd(
        dof: Int,
        select: BooleanArray,
        speedRaw: Int,
        currentMa: Int,
        posDeg: IntArray,
        dir: CmdDir,
    ): ByteArray {
        val n = dof.coerceIn(MIN_DOF, MAX_DOF)
        val buf = ByteArray(CMD_SIZE)
        buf[0] = HDR_CMD.toByte()
        buf[1] = n.toByte()

        var sel = 0
        for (i in 0 until n) {
            if (select.getOrElse(i) { false }) sel = sel or (1 shl ((n - 1) - i))
        }
        buf[2] = sel.toByte()
        buf[3] = if (speedRaw <= 0) 0 else (speedRaw / SPD_STEP).coerceIn(0, 255).toByte()
        buf[4] = if (currentMa <= 0) 0 else ((currentMa - CUR_BASE) / CUR_STEP).coerceIn(0, 255).toByte()

        val isMotion = dir == CmdDir.GRASP || dir == CmdDir.RELEASE
        // ⚠ 임시: grasp/release 는 pos 프리셋으로만 구분. posDeg override 가 있으면 그걸 우선.
        val motionPreset = if (dir == CmdDir.RELEASE) RELEASE_POS_PRESET else GRASP_POS_PRESET
        for (i in 0 until MAX_DOF) {
            val b = when {
                i >= n -> 0                                    // 의미 없는 패딩 슬롯
                !select.getOrElse(i) { false } -> 0
                !isMotion -> 0
                posDeg.getOrElse(i) { 0 } > 0 -> posDeg[i].coerceIn(0, 255)
                else -> motionPreset
            }
            buf[CMD_POS_OFF + i] = b.toByte()
        }
        // ⚠ 임시: GRASP·RELEASE 는 wire dir 을 1(GRASP)로 통일. STOP/RESET_* 은 원래 값.
        val wireDir = if (isMotion) CmdDir.GRASP.wire else dir.wire
        buf[CMD_DIR_OFF] = wireDir.toByte()
        buf[CMD_SIZE - 1] = xor(buf, 1, CMD_SIZE - 1)
        return buf
    }

    // ──────────────────────────────────────────────────────────────
    // 전송: MSET (SET 대체) — 27 byte 고정
    // ──────────────────────────────────────────────────────────────

    /**
     * @param dof          연결된 의수 자유도(5..7).
     * @param actionIds    길이 8. S1~S8 상태 → ACTION_ID[0..7]. 부족하면 0 패딩.
     * @param maxCurrentMa 길이 dof. byte = (mA - 600) / 3.
     * @param motorSpeed   길이 dof. 원값 0..255.
     * @param emgAmp       길이 2. [EMG_AMP_MIN]..[EMG_AMP_MAX], 값이 없으면 [EMG_AMP_DEFAULT].
     */
    fun buildMset(
        dof: Int,
        actionIds: IntArray,
        maxCurrentMa: IntArray,
        motorSpeed: IntArray,
        emgAmp: IntArray,
    ): ByteArray {
        val n = dof.coerceIn(MIN_DOF, MAX_DOF)
        val buf = ByteArray(MSET_SIZE)
        buf[0] = HDR_MSET.toByte()
        buf[1] = n.toByte()

        for (i in 0 until ACTION_ID_COUNT) {
            buf[MSET_ACTION_ID_OFF + i] = actionIds.getOrElse(i) { 0 }.coerceIn(0, 255).toByte()
        }
        for (i in 0 until MAX_DOF) {
            buf[MSET_MAX_CURRENT_OFF + i] =
                if (i >= n) 0
                else ((maxCurrentMa.getOrElse(i) { 0 } - CUR_BASE) / CUR_STEP).coerceIn(0, 255).toByte()
            buf[MSET_MOTOR_SPEED_OFF + i] =
                if (i >= n) 0
                else motorSpeed.getOrElse(i) { 0 }.coerceIn(0, 255).toByte()
        }
        buf[MSET_EMG_OFF] = emgAmp.getOrElse(0) { EMG_AMP_DEFAULT }.coerceIn(EMG_AMP_MIN, EMG_AMP_MAX).toByte()
        buf[MSET_EMG_OFF + 1] = emgAmp.getOrElse(1) { EMG_AMP_DEFAULT }.coerceIn(EMG_AMP_MIN, EMG_AMP_MAX).toByte()
        buf[MSET_SIZE - 1] = xor(buf, 1, MSET_SIZE - 1)
        return buf
    }

    // ──────────────────────────────────────────────────────────────
    // 수신: STATUS — 20 byte 고정 (헤더 + dof 자기서술, `docs/STATUS_Protocol.png`)
    // ──────────────────────────────────────────────────────────────

    fun parseStatus(data: ByteArray): HandStatus? {
        if (data.size < STATUS_SIZE) return null
        // data[0] = 헤더(값 확정 전이라 검사 생략), data[1] = dof
        val dof = (data[1].toInt() and 0xFF).takeIf { it in MIN_DOF..MAX_DOF } ?: return null

        val temp = IntArray(dof) { data[STATUS_TEMP_OFF + it].toInt() and 0xFF }
        val turn = IntArray(dof) { data[STATUS_TURN_OFF + it].toInt() and 0xFF }
        val emg = IntArray(EMG_CH) { data[STATUS_EMG_OFF + it].toInt() and 0xFF }
        val voltage = data[STATUS_VOLTAGE_OFF].toInt() and 0xFF
        val checksumOk = xor(data, 1, STATUS_SIZE - 1) == data[STATUS_SIZE - 1]

        return HandStatus(
            dof = dof,
            motorTemp = temp,
            motorTurn = turn,
            emg = emg,
            voltage = voltage,
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
}
