package com.mandro.mark7.domain.model.hand

import kotlinx.serialization.Serializable

// 의수에 저장되는 전역 설정
@Serializable
data class HandConfig(
    val settings: GlobalSettings = GlobalSettings.DEFAULT,
) {
    fun forDof(dof: HandDof): HandConfig = copy(settings = settings.forDof(dof.dof))

    companion object {
        val DEFAULT = HandConfig()
    }
}

// 앱에 저장되는 전역 설정 (값 단위는 실제 물리량 mA, ms 등)
@Serializable
data class GlobalSettings(
    val graspCurrent: List<Int>,    // 쥘 때 각 모터에 흘리는 전류(mA)
    val releaseCurrent: List<Int>,  // 펼 때 각 모터에 흘리는 전류(mA)
    val maxCurrent: List<Int>,      // 안전 상한 전류
    val slSetting: List<Int>,
    val slGradual: List<Int>,
    val graspPos: List<Int>,
    val releasePos: List<Int>,
    val motorSpeed: List<Int>,      // 손가락별 모터 구동 속도
    val emgAmp: List<Int>,          // EMG 채널 증폭 게인 (값 두개)
    val emgFilter: Int,             // EMG 신호 필터 강도
) {
    // MSET 이 모터 수만큼 max_current·motor_speed 를 싣도록, 모자란 모터 칸을 기본값으로 채움 (이미 있는 값은 유지)
    fun forDof(dof: Int): GlobalSettings = copy(
        maxCurrent = maxCurrent.padTo(dof, DEFAULT_MAX_CURRENT_MA),
        motorSpeed = motorSpeed.padTo(dof, DEFAULT_MOTOR_SPEED),
    )

    companion object {
        private const val DEFAULT_MAX_CURRENT_MA = 1200
        private const val DEFAULT_MOTOR_SPEED = 255

        private fun List<Int>.padTo(size: Int, fill: Int): List<Int> =
            if (this.size >= size) this else this + List(size - this.size) { fill }

        val DEFAULT = GlobalSettings(
            graspCurrent = listOf(1050, 1050, 1050, 1050, 950, 950),
            releaseCurrent = listOf(900, 900, 900, 900, 900, 800),
            maxCurrent = listOf(1200, 1200, 1200, 1200, 1200, 1100),
            slSetting = listOf(24500, 28500, 28500, 28500, 28500, 28000),
            slGradual = listOf(12000, 14000, 14000, 14000, 14000, 12000),
            graspPos = listOf(120, 335, 335, 335, 335, 85),
            releasePos = listOf(127, 345, 345, 345, 345, 95),
            motorSpeed = listOf(255, 255, 255, 255, 255, 255),
            emgAmp = listOf(10, 10),
            emgFilter = 10,
        )
    }
}
