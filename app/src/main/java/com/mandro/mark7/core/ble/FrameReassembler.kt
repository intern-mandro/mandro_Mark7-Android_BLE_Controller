package com.mandro.mark7.core.ble

/**
 * BLE notify 는 바이트 스트림을 임의 크기로 쪼개 던진다. Mark7 수신 프레임은
 *  - ACK    : "SETok" (5 byte, 고정 리터럴)
 *  - STATUS : 20 byte 고정 — `[0]` 헤더([MarkSevenProtocol.HDR_STATUS]) + `[1]` dof … `[18]` voltage,
 *             끝바이트가 XOR 체크섬 (`XOR(buf[1 .. STATUS_SIZE-2]) == buf[STATUS_SIZE-1]`)
 *
 * ACK 와 STATUS 가 같은 notify(fff1) 로 섞여 오므로 버퍼 앞에서 판별한다:
 *  1. "SETok" 로 시작하면 ACK (5 byte 소비)
 *  2. 헤더 바이트가 맞고 앞 STATUS_SIZE byte 의 XOR 체크섬이 맞으면 STATUS 소비
 *  3. 둘 다 아니면 1 byte 버리고 재시도 → 스트림이 어긋나도 다음 유효 프레임에서 재정렬
 *
 * 헤더 바이트가 재동기 기준점이라 (2) 는 헤더 스캔 + 체크섬 확인 한 번으로 끝난다.
 * 프레임이 7DOF 크기로 고정이므로 reassembler 는 DOF 를 알 필요가 없다.
 *
 * 스레드 안전하지 않다 — BleManager 의 gatt 콜백(단일 바인더 스레드)에서만 쓴다.
 */
class FrameReassembler(
    /** 재정렬로 바이트를 버렸을 때 1회 호출 (이번에 버린 누적 바이트 수). 관측/로깅용. */
    private val onResync: ((droppedBytes: Int) -> Unit)? = null,
) {

    sealed interface Frame {
        data object Ack : Frame
        data class Status(val bytes: ByteArray) : Frame
    }

    private val buffer = ArrayDeque<Byte>()

    /** 마지막 정상 프레임 이후 재정렬로 버린 누적 바이트 수. */
    private var droppedSinceGood = 0

    /** 총 재정렬 발생 횟수. */
    var resyncCount = 0
        private set

    fun offer(chunk: ByteArray): List<Frame> {
        buffer.addAll(chunk.asList())
        val out = mutableListOf<Frame>()
        while (true) {
            val frame = takeOne() ?: break
            out += frame
        }
        return out
    }

    fun reset() {
        buffer.clear()
        droppedSinceGood = 0
    }

    private val ack get() = MarkSevenProtocol.ACK_SET
    private val statusSize get() = MarkSevenProtocol.STATUS_SIZE

    private fun takeOne(): Frame? {
        var slidThisCall = 0
        while (true) {
            // 1) ACK — "SETok" 고정 리터럴 (모호성 없음)
            if (matchesAt(ack, 0)) {
                repeat(ack.size) { buffer.removeFirst() }
                return finish(Frame.Ack)
            }

            // 아직 STATUS 한 개도 검증 못 함 → 더 받아야 함
            if (buffer.size < statusSize) return null

            // 2) STATUS — 헤더 바이트 + 앞 STATUS_SIZE byte XOR 체크섬 검증 (+ 재정렬 중이면 뒤 프레임까지 확인)
            if (statusOkAt(0) && confirmedAt(0)) {
                val bytes = ByteArray(statusSize) { buffer.removeFirst() }
                return finish(Frame.Status(bytes))
            }

            // 3) ACK 도 아니고 유효 STATUS 도 아님 → 1 byte 버리고 재정렬
            buffer.removeFirst()
            droppedSinceGood++
            if (++slidThisCall >= statusSize * 2) return null // 이번 호출은 여기까지, 다음 chunk 에서 계속
        }
    }

    private fun finish(frame: Frame): Frame {
        if (droppedSinceGood > 0) {
            resyncCount++
            onResync?.invoke(droppedSinceGood)
            droppedSinceGood = 0
        }
        return frame
    }

    private fun matchesAt(pattern: ByteArray, offset: Int): Boolean {
        if (buffer.size < offset + pattern.size) return false
        for (i in pattern.indices) if (buffer.elementAt(offset + i) != pattern[i]) return false
        return true
    }

    /** offset 위치가 헤더 바이트로 시작하고 XOR(buf[1 .. statusSize-2]) == buf[statusSize-1] 인가. */
    private fun statusOkAt(offset: Int): Boolean {
        if (buffer.size < offset + statusSize) return false
        if ((buffer.elementAt(offset).toInt() and 0xFF) != MarkSevenProtocol.HDR_STATUS) return false
        var acc = 0
        for (i in 1 until statusSize - 1) acc = acc xor (buffer.elementAt(offset + i).toInt() and 0xFF)
        return acc.toByte() == buffer.elementAt(offset + statusSize - 1)
    }

    /**
     * 재정렬 중(직전에 바이트를 버린 상태)에는 우연한 헤더+체크섬 일치로 잘못 lock 되는 걸
     * 막기 위해, 바로 뒤가 또 다른 유효 프레임 시작인지 한 번 더 본다.
     * 정상 흐름(`droppedSinceGood == 0`)에서는 지연 없이 통과.
     */
    private fun confirmedAt(offset: Int): Boolean {
        if (droppedSinceGood == 0) return true
        val next = offset + statusSize
        return when {
            matchesAt(ack, next) -> true
            statusOkAt(next) -> true
            buffer.size < next + statusSize -> true // 뒤를 아직 확인 불가 — 통과 (제한된 위험)
            else -> false
        }
    }
}
