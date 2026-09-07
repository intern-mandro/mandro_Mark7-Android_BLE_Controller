package com.mandro.mark7.domain.model

import com.mandro.mark7.R

/**
 * 의수 → 앱 STATUS 프레임(36 byte)의 파싱 결과.
 *
 * - [motorTemp]       모터 6개 온도(°C 근사, uint8)
 * - [motorCurrentAvg] 모터 6개 평균 전류(mA, uint16)
 * - [motorTurn]       모터 6개 회전량/위치(signed int16)
 * - [emg]             EMG 2채널 원시값(uint16)
 * - [currentState]    펌웨어 현재 상태(MSS) 인덱스. STATUS byte34 하위 니블로 가정 —
 *   실기기로 확인 필요(`docs/PROTOCOL.md §5`). 없거나 범위를 벗어나면 null.
 * - [programMode]     펌웨어 program_mode. 1 = MODE1(P0), 2 = MODE2(P1). STATUS byte34
 *   상위 니블로 가정. `close` 입력이 오면 펌웨어가 자동 전환한다.
 * - [checksumOk]      말미 XOR 체크섬 일치 여부 (표시만; 불일치라고 버리지 않음)
 */
data class HandStatus(
    val motorTemp: IntArray,
    val motorCurrentAvg: IntArray,
    val motorTurn: IntArray,
    val emg: IntArray,
    val checksumOk: Boolean,
    val currentState: Int? = null,
    val programMode: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HandStatus) return false
        return motorTemp.contentEquals(other.motorTemp) &&
            motorCurrentAvg.contentEquals(other.motorCurrentAvg) &&
            motorTurn.contentEquals(other.motorTurn) &&
            emg.contentEquals(other.emg) &&
            currentState == other.currentState &&
            programMode == other.programMode &&
            checksumOk == other.checksumOk
    }

    override fun hashCode(): Int {
        var result = motorTemp.contentHashCode()
        result = 31 * result + motorCurrentAvg.contentHashCode()
        result = 31 * result + motorTurn.contentHashCode()
        result = 31 * result + emg.contentHashCode()
        result = 31 * result + (currentState ?: -1)
        result = 31 * result + (programMode ?: -1)
        result = 31 * result + checksumOk.hashCode()
        return result
    }

    companion object {
        val MOTOR_NAMES = listOf("F1 엄지", "F2 검지", "F3 중지", "F4 약지", "F5 소지", "F6 엄지외전")

        /** 모터 6개 표시 이름 리소스(로케일 반영). */
        val MOTOR_NAME_RES = listOf(
            R.string.motor_f1, R.string.motor_f2, R.string.motor_f3,
            R.string.motor_f4, R.string.motor_f5, R.string.motor_f6,
        )

        /** 모터 6개 짧은 이름(수동 제어 칩용). */
        val MOTOR_SHORT_RES = listOf(
            R.string.motor_short_f1, R.string.motor_short_f2, R.string.motor_short_f3,
            R.string.motor_short_f4, R.string.motor_short_f5, R.string.motor_short_f6,
        )
    }
}
