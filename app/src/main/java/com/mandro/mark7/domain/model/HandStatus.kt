package com.mandro.mark7.domain.model

/**
 * 의수 → 앱 STATUS 프레임(36 byte)의 파싱 결과.
 *
 * - [motorTemp]       모터 6개 온도(°C 근사, uint8)
 * - [motorCurrentAvg] 모터 6개 평균 전류(mA, uint16)
 * - [motorTurn]       모터 6개 회전량/위치(signed int16)
 * - [emg]             EMG 2채널 원시값(uint16)
 * - [checksumOk]      말미 XOR 체크섬 일치 여부 (표시만; 불일치라고 버리지 않음)
 */
data class HandStatus(
    val motorTemp: IntArray,
    val motorCurrentAvg: IntArray,
    val motorTurn: IntArray,
    val emg: IntArray,
    val checksumOk: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HandStatus) return false
        return motorTemp.contentEquals(other.motorTemp) &&
            motorCurrentAvg.contentEquals(other.motorCurrentAvg) &&
            motorTurn.contentEquals(other.motorTurn) &&
            emg.contentEquals(other.emg) &&
            checksumOk == other.checksumOk
    }

    override fun hashCode(): Int {
        var result = motorTemp.contentHashCode()
        result = 31 * result + motorCurrentAvg.contentHashCode()
        result = 31 * result + motorTurn.contentHashCode()
        result = 31 * result + emg.contentHashCode()
        result = 31 * result + checksumOk.hashCode()
        return result
    }

    companion object {
        val MOTOR_NAMES = listOf("F1 엄지", "F2 검지", "F3 중지", "F4 약지", "F5 소지", "F6 엄지외전")
    }
}
