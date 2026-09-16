package com.mandro.mark7.domain.model.hand

data class HandStatus(
    val dof: Int,
    val motorTemp: IntArray,
    val motorTurn: IntArray,
    val emg: IntArray,
    val voltage: Int,
    val checksumOk: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HandStatus) return false
        return dof == other.dof &&
            motorTemp.contentEquals(other.motorTemp) &&
            motorTurn.contentEquals(other.motorTurn) &&
            emg.contentEquals(other.emg) &&
            voltage == other.voltage &&
            checksumOk == other.checksumOk
    }

    override fun hashCode(): Int {
        var result = dof
        result = 31 * result + motorTemp.contentHashCode()
        result = 31 * result + motorTurn.contentHashCode()
        result = 31 * result + emg.contentHashCode()
        result = 31 * result + voltage
        result = 31 * result + checksumOk.hashCode()
        return result
    }

    /** [voltage] 를 화면 표시용 전압으로 바꾼 값 (예: 127 → "12.7"). */
    val voltageText: String get() = formatVoltage(voltage)

    companion object {
        /** 표시 전압 = voltage 원시값 ÷ 10 (127 → 12.7, 255 → 25.5). */
        const val VOLTAGE_DIVISOR = 10

        /**
         * voltage 원시값 → 표시 전압 문자열.
         * 받아온 값 ÷ [VOLTAGE_DIVISOR] (10) 을 소수 첫째 자리까지 표시한다 (예: 127 → "12.7").
         * 나눗수를 바꿔 소수 둘째 자리 이하가 생기면 버린다(절삭).
         */
        fun formatVoltage(raw: Int): String {
            val tenths = raw * 10 / VOLTAGE_DIVISOR
            return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
        }
    }
}
