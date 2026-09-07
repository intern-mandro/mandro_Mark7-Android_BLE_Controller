package com.mandro.mark7.domain.model

import kotlinx.serialization.Serializable

/**
 * 수동 조절 화면에서 원터치로 실행할 수 있는 동작 프리셋.
 *
 * 기본 제공 6개(주먹, 포인팅, 집기, 가위, 삼각집기, 전체펴기)와
 * 사용자가 직접 손가락 조합을 저장한 커스텀 프리셋을 모두 표현한다.
 */
@Serializable
data class ManualPreset(
    val id: String,
    val name: String,
    val emoji: String,
    val fingers: List<Boolean>, // 6개 손가락 (F1..F6)
    val direction: CmdDir,       // GRASP or RELEASE
    val isDefault: Boolean = false,
    /** 사용자가 지정한 사진 경로(`file://…`). null 이면 [emoji] 로 표시. */
    val imageUri: String? = null,
    /**
     * 사진을 정사각/직사각 프레임에 꽉 채워 자를 때(Crop) 어느 부분이 보일지.
     * -1f..1f, 0 = 가운데. 사용자가 드래그로 조절. 프레임 크기·배율은 고정.
     */
    val imageBiasX: Float = 0f,
    val imageBiasY: Float = 0f,
) {
    init {
        require(fingers.size == 6) { "fingers must have exactly 6 elements" }
    }

    companion object {
        val DEFAULT_PRESETS: List<ManualPreset> = listOf(
            ManualPreset(
                id = "fist",
                name = "주먹",
                emoji = "✊",
                fingers = listOf(true, true, true, true, true, true),
                direction = CmdDir.GRASP,
                isDefault = true,
            ),
            ManualPreset(
                id = "point",
                name = "포인팅",
                emoji = "☝️",
                // 검지(F2)만 펴고 엄지(F1) + 중지/약지/새끼(F3..F6) 쥐기
                fingers = listOf(true, false, true, true, true, true),
                direction = CmdDir.GRASP,
                isDefault = true,
            ),
            ManualPreset(
                id = "pinch",
                name = "집기",
                emoji = "🤏",
                // 엄지(F1) + 검지(F2) 쥐기
                fingers = listOf(true, true, false, false, false, false),
                direction = CmdDir.GRASP,
                isDefault = true,
            ),
            ManualPreset(
                id = "peace",
                name = "가위",
                emoji = "✌️",
                // 검지(F2), 중지(F3) 펴고 엄지(F1) + 약지/새끼(F4..F6) 쥐기
                fingers = listOf(true, false, false, true, true, true),
                direction = CmdDir.GRASP,
                isDefault = true,
            ),
            ManualPreset(
                id = "tripod",
                name = "삼각 집기",
                emoji = "🖖",
                // 엄지(F1) + 검지(F2) + 중지(F3) 쥐기
                fingers = listOf(true, true, true, false, false, false),
                direction = CmdDir.GRASP,
                isDefault = true,
            ),
            ManualPreset(
                id = "open",
                name = "전체 펴기",
                emoji = "🖐",
                // 전 손가락 펴기
                fingers = listOf(true, true, true, true, true, true),
                direction = CmdDir.RELEASE,
                isDefault = true,
            ),
        )
    }
}
