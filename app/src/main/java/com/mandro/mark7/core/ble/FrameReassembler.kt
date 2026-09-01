package com.mandro.mark7.core.ble

/**
 * BLE notify 는 바이트 스트림을 임의 크기로 쪼개 던진다. Mark7 수신 프레임은
 *  - ACK    : "SETok" (5 byte)
 *  - STATUS : 36 byte, 헤더 없음 — 길이로만 경계
 * 두 종류뿐이라, 버퍼 앞에서 ACK 를 먼저 떼고 그다음 36 byte 단위로 끊는다.
 * (PC 도구 `ble/hand.py::take_frame` 과 같은 전략)
 *
 * 스레드 안전하지 않다 — BleManager 의 gatt 콜백(단일 바인더 스레드)에서만 쓴다.
 */
class FrameReassembler {

    sealed interface Frame {
        data object Ack : Frame
        data class Status(val bytes: ByteArray) : Frame
    }

    private val buffer = ArrayDeque<Byte>()

    fun offer(chunk: ByteArray): List<Frame> {
        buffer.addAll(chunk.asList())
        val out = mutableListOf<Frame>()
        while (true) {
            val frame = takeOne() ?: break
            out += frame
        }
        return out
    }

    fun reset() = buffer.clear()

    private fun takeOne(): Frame? {
        if (buffer.size >= MarkSevenProtocol.ACK_SET.size && startsWithAck()) {
            repeat(MarkSevenProtocol.ACK_SET.size) { buffer.removeFirst() }
            return Frame.Ack
        }
        if (buffer.size >= MarkSevenProtocol.STATUS_SIZE) {
            val bytes = ByteArray(MarkSevenProtocol.STATUS_SIZE) { buffer.removeFirst() }
            return Frame.Status(bytes)
        }
        return null
    }

    private fun startsWithAck(): Boolean {
        val ack = MarkSevenProtocol.ACK_SET
        for (i in ack.indices) if (buffer.elementAt(i) != ack[i]) return false
        return true
    }
}
