package com.mandro.mark7.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * 개발용 데이터 소스 스위치. true 면 BLE 대신 mock(합성) 데이터를, false 면 실제 BLE 를 쓴다.
 *
 * 시작값은 `BuildConfig.USE_MOCK_BLE` (app/build.gradle.kts 의 빌드 타입별 true/false) 이고,
 * 실행 중에는 [setMockMode] 로 다시 빌드하지 않고 바꿀 수 있다.
 */
interface MockModeController {
    val isMockMode: StateFlow<Boolean>

    /** 이전 소스의 스캔·연결을 정리한 뒤 전환한다. 이미 같은 값이면 아무것도 하지 않는다. */
    suspend fun setMockMode(enabled: Boolean)
}
