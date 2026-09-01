package com.mandro.mark7.domain.model

/**
 * 근전도 암밴드가 내보내는 4가지 액션.
 *
 * 노트북 스케치의 F / E / CLOSE / REST. 펌웨어 `config.h::InputCode` 로는
 * 대략 flexion→I_I, extension→E_E, close→TRG 계열, rest→NONE 에 대응한다.
 *
 * 이 앱에서는 "각 액션을 어떤 패턴(상태)으로 연결할지" 를 사용자가 사진을 보고
 * 고르는 매핑 UI 로 다룬다. 매핑 자체는 앱 로컬(DataStore)에 저장하고, 실제
 * 상태 전이는 의수 펌웨어가 수행한다.
 */
enum class HandAction(
    val displayName: String,
    /** assets/gesture_guides/<assetDir>/fNN.jpg */
    val assetDir: String,
) {
    FLEXION("굽히기 (Flexion)", "flexion"),
    EXTENSION("펴기 (Extension)", "extension"),
    CLOSE("쥐기 (Close)", "close"),
    REST("휴식 (Rest)", "rest"),
}

/** 액션 → 패턴 인덱스(0..7) 매핑 + 점진적 잡기 사용 여부. 앱 로컬 저장용. */
data class ActionMapping(
    val patternIndexByAction: Map<HandAction, Int> = emptyMap(),
    val gradualGraspEnabled: Boolean = false,
) {
    fun patternFor(action: HandAction): Int? = patternIndexByAction[action]
}
