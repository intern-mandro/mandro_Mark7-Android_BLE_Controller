package com.mandro.mark7.domain.model

import kotlinx.serialization.Serializable

/**
 * SET 프레임(117 byte)으로 의수에 저장되는 전체 설정.
 *
 * [patterns] 8개는 펌웨어의 상태(MSS1..MSS8)를 정의한다. 노트북 스케치의 S1..S8 이
 * 이것. 상태 사이 전이(어떤 액션에서 어디로 갈지)는 펌웨어의 `transitionTable` 이
 * 고정으로 갖고 있고, 앱은 각 상태가 "어떤 손 모양인지"만 채운다.
 */
@Serializable
data class HandConfig(
    val patterns: List<HandPattern> = List(8) { HandPattern.EMPTY },
    val settings: GlobalSettings = GlobalSettings.DEFAULT,
) {
    init {
        require(patterns.size == 8) { "patterns must have exactly 8 entries" }
    }

    companion object {
        /** hand.py DEFAULT_PATTERNS / DEFAULT_VALUES 를 그대로 옮긴 기본값. */
        val DEFAULT = HandConfig(
            patterns = listOf(
                HandPattern.fromWire(flags = 0x01, bitmask = 0x21, orderDelay = intArrayOf(0x32, 0, 0, 0, 0, 0x31)),
                HandPattern.fromWire(flags = 0x01, bitmask = 0x3F, orderDelay = intArrayOf(0, 1, 1, 1, 1, 0)),
                HandPattern.fromWire(flags = 0x01, bitmask = 0x39, orderDelay = intArrayOf(0x32, 0, 0, 0x03, 0x03, 0x31)),
                HandPattern.EMPTY,
                HandPattern.fromWire(flags = 0x03, bitmask = 0x0E, orderDelay = intArrayOf(0, 1, 1, 1, 0, 0)),
                HandPattern.EMPTY,
                HandPattern.EMPTY,
                HandPattern.EMPTY,
            ),
            settings = GlobalSettings.DEFAULT,
        )
    }
}

/**
 * 하나의 상태(=손 모양) 정의. SET 프레임의 패턴 8바이트에 대응.
 *
 * - [valid]    flags bit0. false 면 펌웨어가 이 상태로의 전이를 무시.
 * - [gradual]  flags bit1. 점진적 잡기(hold 하는 동안 조금씩 더 쥐는) 상태.
 * - [motor]    길이 6. 이 상태로 갈 때 실제로 구동할 모터.
 * - [order]    길이 6. 0 = 구동 안 함, 1.. = 실행 순서.
 * - [delayMs]  길이 6. 해당 order 실행 후 다음 order 전 대기(ms). 전송 시 16ms 단위로 양자화.
 */
@Serializable
data class HandPattern(
    val valid: Boolean,
    val gradual: Boolean,
    val motor: List<Boolean>,
    val order: List<Int>,
    val delayMs: List<Int>,
) {
    companion object {
        val EMPTY = HandPattern(
            valid = false,
            gradual = false,
            motor = List(6) { false },
            order = List(6) { 0 },
            delayMs = List(6) { 0 },
        )

        fun fromWire(flags: Int, bitmask: Int, orderDelay: IntArray): HandPattern = HandPattern(
            valid = flags and 0x01 != 0,
            gradual = flags and 0x02 != 0,
            motor = List(6) { (bitmask shr it) and 0x01 != 0 },
            order = List(6) { orderDelay[it] and 0x0F },
            delayMs = List(6) { ((orderDelay[it] shr 4) and 0x0F) * 16 },
        )
    }
}

/** SET 프레임 byte 65..115 의 전역 설정. 값 단위는 실제 물리량(mA, ms 등). */
@Serializable
data class GlobalSettings(
    val graspCurrent: List<Int>,
    val releaseCurrent: List<Int>,
    val maxCurrent: List<Int>,
    val slSetting: List<Int>,
    val slGradual: List<Int>,
    val graspPos: List<Int>,
    val releasePos: List<Int>,
    val motorSpeed: List<Int>,
    val emgAmp: List<Int>,
    val emgFilter: Int,
) {
    companion object {
        val DEFAULT = GlobalSettings(
            graspCurrent = listOf(1050, 1050, 1050, 1050, 950, 950),
            releaseCurrent = listOf(900, 900, 900, 900, 900, 800),
            maxCurrent = listOf(1200, 1200, 1200, 1200, 1200, 1100),
            slSetting = listOf(24500, 28500, 28500, 28500, 28500, 28000),
            slGradual = listOf(12000, 14000, 14000, 14000, 14000, 12000),
            graspPos = listOf(120, 335, 335, 335, 335, 85),
            releasePos = listOf(127, 345, 345, 345, 345, 95),
            motorSpeed = listOf(255, 255, 255, 255, 255, 255),
            emgAmp = listOf(20, 20),
            emgFilter = 10,
        )
    }
}
