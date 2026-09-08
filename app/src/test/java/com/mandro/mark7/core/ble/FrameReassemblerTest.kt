package com.mandro.mark7.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FrameReassembler 는 같은 notify 채널로 섞여 오는 ACK("SETok") 와 STATUS(36B) 를
 * 버퍼 앞에서 판별하고, STATUS 는 XOR 체크섬으로 검증한다. 스트림이 어긋나면
 * 1 byte 씩 버리며 다음 유효 프레임에 재정렬한다.
 */
class FrameReassemblerTest {

    private val statusSize = MarkSevenProtocol.STATUS_SIZE

    /** 체크섬이 올바른 STATUS 36바이트 프레임. seed 로 내용만 바꾼다. */
    private fun status(seed: Int = 0): ByteArray {
        val b = ByteArray(statusSize) { ((it * 7 + seed * 31 + 1) and 0xFF).toByte() }
        b[statusSize - 1] = MarkSevenProtocol.xor(b, 0, statusSize - 1)
        return b
    }

    @Test
    fun `clean stream yields consecutive status frames`() {
        val r = FrameReassembler()

        val frames = r.offer(status(1) + status(2) + status(3))

        assertEquals(3, frames.size)
        assertTrue(frames.all { it is FrameReassembler.Frame.Status })
        assertEquals(0, r.resyncCount)
    }

    @Test
    fun `ack prefix is taken before status`() {
        val r = FrameReassembler()

        val frames = r.offer("SETok".toByteArray() + status(9))

        assertEquals(FrameReassembler.Frame.Ack, frames[0])
        assertTrue(frames[1] is FrameReassembler.Frame.Status)
    }

    @Test
    fun `status split across multiple offers is reassembled`() {
        val r = FrameReassembler()
        val s = status(4)

        assertTrue(r.offer(s.copyOfRange(0, 20)).isEmpty())
        val frames = r.offer(s.copyOfRange(20, statusSize))

        assertEquals(1, frames.size)
        assertTrue(frames[0] is FrameReassembler.Frame.Status)
        assertEquals(0, r.resyncCount)
    }

    @Test
    fun `bad checksum frame does not desync following frames`() {
        val r = FrameReassembler()
        val corrupt = status(5).also { it[10] = (it[10] + 1).toByte() } // 체크섬 깨짐

        val frames = r.offer(corrupt + status(6) + status(7))

        // 깨진 프레임은 버려지고 뒤 두 개는 정상 수신
        assertEquals(2, frames.count { it is FrameReassembler.Frame.Status })
        assertTrue(r.resyncCount >= 1)
    }

    @Test
    fun `realigns after leading garbage bytes`() {
        val r = FrameReassembler()
        val junk = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55)

        val frames = r.offer(junk + status(1) + status(2))

        assertEquals(2, frames.count { it is FrameReassembler.Frame.Status })
        assertEquals(1, r.resyncCount)
    }

    @Test
    fun `recovers from a single dropped byte mid-stream`() {
        val r = FrameReassembler()
        val s1 = status(1)
        val s2 = status(2)
        // s2 의 첫 바이트가 유실된 스트림 + 뒤에 정상 프레임들
        val stream = s1 + s2.copyOfRange(1, statusSize) + status(3) + status(4) + status(5)

        val frames = r.offer(stream)

        assertTrue(frames.first() is FrameReassembler.Frame.Status) // s1 은 정상
        assertTrue("re-locked onto later frames", frames.count { it is FrameReassembler.Frame.Status } >= 3)
        assertTrue(r.resyncCount >= 1)
    }

    @Test
    fun `reset clears buffered partial frame`() {
        val r = FrameReassembler()
        r.offer(status(1).copyOfRange(0, 20))
        r.reset()

        val frames = r.offer(status(2))

        assertEquals(1, frames.size)
        assertTrue(frames[0] is FrameReassembler.Frame.Status)
        assertEquals(0, r.resyncCount)
    }
}
