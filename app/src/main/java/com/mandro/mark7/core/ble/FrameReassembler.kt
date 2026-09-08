package com.mandro.mark7.core.ble

/**
 * BLE notify 는 바이트 스트림을 임의 크기로 쪼개 던진다. Mark7 수신 프레임은
 *  - ACK    : "SETok" (5 byte, 고정 리터럴)
 *  - STATUS : 36 byte, 헤더 없음 — 끝바이트가 XOR 체크섬 (`buf[0..34]` XOR == `buf[35]`)
 *
 * ACK 와 STATUS 가 같은 notify(FFF1) 로 섞여 오므로 버퍼 앞에서 판별한다:
 *  1. "SETok" 로 시작하면 ACK (5 byte 소비)
 *  2. 아니면 앞 36 byte 의 XOR 체크섬이 맞을 때만 STATUS (36 byte 소비)
 *  3. 둘 다 아니면 1 byte 버리고 재시도 → 스트림이 어긋나도 다음 유효 프레임에서 재정렬
 *
 * (2) 의 체크섬 검증이 없으면 notify 한 개만 유실돼도 36 byte 경계가 영구히 밀린다
 * (STATUS 에 헤더가 없어 재동기 기준점이 이 체크섬뿐).
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

            // 2) STATUS — 앞 36 byte XOR 체크섬 검증 (+ 재정렬 중이면 뒤 프레임까지 확인)
            if (statusChecksumOkAt(0) && confirmedAt(0)) {
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

    private fun statusChecksumOkAt(offset: Int): Boolean {
        if (buffer.size < offset + statusSize) return false
        var acc = 0
        for (i in 0 until statusSize - 1) acc = acc xor (buffer.elementAt(offset + i).toInt() and 0xFF)
        return acc.toByte() == buffer.elementAt(offset + statusSize - 1)
    }

    /**
     * 재정렬 중(직전에 바이트를 버린 상태)에는 1/256 확률의 우연한 체크섬 일치로 잘못
     * lock 되는 걸 막기 위해, 바로 뒤가 또 다른 유효 프레임 시작인지 한 번 더 본다.
     * 정상 흐름(`droppedSinceGood == 0`)에서는 지연 없이 체크섬만으로 통과.
     */
    private fun confirmedAt(offset: Int): Boolean {
        if (droppedSinceGood == 0) return true
        val next = offset + statusSize
        return when {
            matchesAt(ack, next) -> true
            statusChecksumOkAt(next) -> true
            buffer.size < next + statusSize -> true // 뒤를 아직 확인 불가 — 통과 (제한된 위험)
            else -> false
        }
    }
}
