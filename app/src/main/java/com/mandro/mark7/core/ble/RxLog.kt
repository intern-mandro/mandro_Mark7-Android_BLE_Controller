package com.mandro.mark7.core.ble

import com.mandro.mark7.domain.model.hand.HandStatus

/**
 * BLE 수신 로그 문자열. `scripts/capture-emg-status.ps1` 이 `RX frame: STATUS` 줄을 읽어
 * 초당 수신 개수와 프레임 전체를 기록하므로, 형식을 바꾸면 스크립트도 같이 고친다.
 */
internal object RxLog {

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    /** 해석 결과(실패하면 `parse FAILED`) 뒤에 프레임 원본 전체를 `raw=` 로 붙인다. */
    fun statusLine(frame: ByteArray, status: HandStatus?): String {
        val parsed = if (status == null) {
            "parse FAILED"
        } else {
            "dof=${status.dof} temp=${status.motorTemp.toList()} turn=${status.motorTurn.toList()} " +
                "emg=${status.emg.toList()} voltage=${status.voltage} chkOk=${status.checksumOk}"
        }
        return "RX frame: STATUS ${frame.size}B — $parsed raw=${hex(frame)}"
    }
}
