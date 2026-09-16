package com.mandro.mark7.domain.model.hand

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ManualPreset(
    val id: String,
    val name: String,
    val emoji: String,
    val fingers: List<Boolean>, // 자유도에 맞는 모터 선택값 (5~7개)
    val direction: CmdDir,       // GRASP or RELEASE
    val isDefault: Boolean = false,
    /** 사용자 사진 경로(`file://…`). null 이면 기본 프리셋 에셋, 에셋도 없으면 [emoji] 로 표시. */
    val imageUri: String? = null,
    val imageBiasX: Float = 0f,
    val imageBiasY: Float = 0f,
    /** 기본 목록의 사진 파일명. 앱 저장값에는 포함하지 않고 최신 목록에서 조회한다. */
    @Transient val imageFileName: String? = null,
) {
    init {
        require(fingers.size in 5..7) { "fingers must have 5 to 7 elements" }
    }

    companion object {
        /** 기존 호출부를 위한 6DOF 기본값. 화면과 저장소는 [CmdPresetCatalogs.forDof]를 쓴다. */
        val DEFAULT_PRESETS: List<ManualPreset> get() = CmdPresetCatalogs.DOF_6
    }
}

/** 자유도별 CMD 기본 프리셋 목록. 각 목록의 항목과 순서를 독립적으로 바꿀 수 있다. */
object CmdPresetCatalogs {
    val DOF_5: List<ManualPreset> = with(HandDof.DOF_5) { listOf(flatHand(), fist(), pointing()) }
    val DOF_6: List<ManualPreset> = with(HandDof.DOF_6) { listOf(flatHand(), fist(), pointing()) }
    val DOF_7: List<ManualPreset> = with(HandDof.DOF_7) { listOf(flatHand(), fist(), pointing()) }

    fun forDof(dof: HandDof): List<ManualPreset> = when (dof) {
        HandDof.DOF_5 -> DOF_5
        HandDof.DOF_6 -> DOF_6
        HandDof.DOF_7 -> DOF_7
    }

    /** 이전 버전의 6개 모터 저장값을 해당 자유도의 CMD 선택값으로 읽는다. */
    fun normalizeSaved(presets: List<ManualPreset>, dof: HandDof): List<ManualPreset> {
        val defaults = forDof(dof)
        return presets.map { preset ->
            val currentDefault = defaults.firstOrNull { preset.isDefault && it.id == preset.id }
            val fingers = currentDefault?.fingers ?: preset.fingers.take(dof.dof) +
                List((dof.dof - preset.fingers.size).coerceAtLeast(0)) { false }
            if (preset.fingers == fingers) preset else preset.copy(fingers = fingers)
        }
    }

    private fun HandDof.flatHand() = ManualPreset(
        id = "open", name = "Flat Hand", emoji = "✋", imageFileName = "00_flat_hand.jpg",
        fingers = List(dof) { true }, direction = CmdDir.RELEASE, isDefault = true,
    )

    private fun HandDof.fist() = ManualPreset(
        id = "fist", name = "Fist", emoji = "✊", imageFileName = "02_fist.jpg",
        fingers = List(dof) { true }, direction = CmdDir.GRASP, isDefault = true,
    )

    private fun HandDof.pointing() = ManualPreset(
        id = "point", name = "Pointing", emoji = "☝️", imageFileName = "03_pointing.jpg",
        fingers = List(dof) { it != 1 }, direction = CmdDir.GRASP, isDefault = true,
    )
}
