package com.mandro.mark7.domain.model

/** 스캔으로 발견한 BLE 기기. */
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

/** 의수 BLE 링크 상태. */
sealed interface BleState {
    data object Idle : BleState
    data object Scanning : BleState
    data class DevicesFound(val devices: List<BleDevice>) : BleState
    data class Connecting(val device: BleDevice) : BleState
    data class Connected(val device: BleDevice) : BleState
    data object Disconnected : BleState
    data class Error(val message: String) : BleState
}

/** SET 프레임 전송 결과. */
sealed interface ConfigPushState {
    data object Idle : ConfigPushState
    data object Sending : ConfigPushState
    data object Acked : ConfigPushState
    data class Error(val message: String) : ConfigPushState
}

/**
 * Mark7 의수의 BLE 모듈(Chipsen)이 광고하는 기기 이름 프리픽스.
 * 스캔 목록 필터 기준 — `startsWith` 로 비교하므로 "CHIPSEN", "CHIPSEN-1234" 등 모두 매치.
 */
const val MARK7_NAME_PREFIX = "chipsen"
