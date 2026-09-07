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
import androidx.annotation.StringRes
import com.mandro.mark7.R

enum class HandAction(
    @StringRes val displayNameRes: Int,
    /** assets/gesture_guides/<assetDir>/fNN.jpg */
    val assetDir: String,
) {
    FLEXION(R.string.gesture_flexion, "flexion"),
    EXTENSION(R.string.gesture_extension, "extension"),
    CLOSE(R.string.gesture_close, "close"),
    REST(R.string.gesture_rest, "rest"),
}

/**
 * 앱 로컬 저장용 매핑 묶음.
 *
 * - [patternIndexByAction] 액션(F/E/close/rest) → 패턴 인덱스(0..7).
 * - [gestureIdByState]     상태 다이어그램의 상태 id(2..8) → 손 모양([Gesture.id]).
 *   S1 은 대기 고정이라 저장하지 않는다.
 * - [gradualGraspEnabled]  점진적 잡기 사용 여부.
 */
data class ActionMapping(
    val patternIndexByAction: Map<HandAction, Int> = emptyMap(),
    val gestureIdByState: Map<Int, String> = emptyMap(),
    val gradualGraspEnabled: Boolean = false,
) {
    fun patternFor(action: HandAction): Int? = patternIndexByAction[action]

    fun gestureIdFor(stateId: Int): String? = gestureIdByState[stateId]
}
