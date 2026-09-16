package com.mandro.mark7.domain.model.action

import androidx.annotation.StringRes
import com.mandro.mark7.R

/**
 * 노드에 넣을 수 있는 손 모양 한 개.
 * 펌웨어 액션 ID 와 사진 파일은 자유도별 목록([GestureCatalog]) 안의 위치로 정해지므로 여기 두지 않는다.
 * - [id] 고유 식별자 문자열 (저장값 키이자 사진 파일명의 뒷부분). 한 번 정하면 바꾸지 않는다.
 * - [displayNameRes] 다국어 표시 이름 문자열 리소스 ID
 * - [enabled] 선택 가능 여부. Flat Hand 는 기본 대기(Idle) 상태이므로 선택 비활성화.
 */
data class Gesture(
    val id: String,
    @StringRes val displayNameRes: Int,
    val enabled: Boolean = true,
)

/**
 * 손 선택 화면에서 한 번에 고르는 묶음이자, 한 슬롯 묶음(S1·S2 등)에 들어갈 손 모양.
 * [lead] 는 앞 노드, [companion] 은 뒤 노드. 단일 액션이면 [companion] 이 null 이고 뒤 노드는 비활성이다.
 */
data class GestureSlots(
    val lead: Gesture,
    val companion: Gesture?,
)

/**
 * 손 모양 정의 모음(id·이름). 자유도별 목록([GestureCatalogs])은 여기서 골라 묶음과 순서를 정한다.
 * 새 손 모양이 필요하면 여기에 추가한 뒤, 쓰려는 자유도 목록에 넣고 그 폴더에 사진을 둔다.
 */
object Gestures {
    val FLAT_HAND = Gesture(id = "flat_hand", displayNameRes = R.string.gesture_flat_hand, enabled = false)

    val CYLINDER_GRIP_OPEN = Gesture(id = "cylinder_grip_open", displayNameRes = R.string.gesture_cylinder_grip_open)
    val CYLINDER_GRIP_CLOSED = Gesture(id = "cylinder_grip_closed", displayNameRes = R.string.gesture_cylinder_grip_closed)
    val TIP_PINCH_OPEN = Gesture(id = "tip_pinch_open", displayNameRes = R.string.gesture_tip_pinch_open)
    val TIP_PINCH_CLOSED = Gesture(id = "tip_pinch_closed", displayNameRes = R.string.gesture_tip_pinch_closed)
    val LATERAL_PINCH_OPEN = Gesture(id = "lateral_pinch_open", displayNameRes = R.string.gesture_lateral_pinch_open)
    val LATERAL_PINCH_CLOSED = Gesture(id = "lateral_pinch_closed", displayNameRes = R.string.gesture_lateral_pinch_closed)
    val TRIPOD_OPEN = Gesture(id = "tripod_open", displayNameRes = R.string.gesture_tripod_open)
    val TRIPOD_CLOSED = Gesture(id = "tripod_closed", displayNameRes = R.string.gesture_tripod_closed)
    val TRIGGER_OPEN = Gesture(id = "trigger_open", displayNameRes = R.string.gesture_trigger_open)
    val TRIGGER_CLOSED = Gesture(id = "trigger_closed", displayNameRes = R.string.gesture_trigger_closed)
    val SCISSOR_GRIP_OPEN = Gesture(id = "scissor_grip_open", displayNameRes = R.string.gesture_scissor_grip_open)
    val SCISSOR_GRIP_CLOSE = Gesture(id = "scissor_grip_close", displayNameRes = R.string.gesture_scissor_grip_close)
    val THREE_FINGER_GRIP_OPEN = Gesture(id = "three_finger_grip_open", displayNameRes = R.string.gesture_three_finger_grip_open)
    val THREE_FINGER_GRIP_CLOSE = Gesture(id = "three_finger_grip_close", displayNameRes = R.string.gesture_three_finger_grip_close)

    val POINTING = Gesture(id = "pointing", displayNameRes = R.string.gesture_pointing)
    val PHONE = Gesture(id = "phone", displayNameRes = R.string.gesture_phone)
    val PEACE = Gesture(id = "peace", displayNameRes = R.string.gesture_peace)
    val THREE_FINGER_SALUTE = Gesture(id = "three_finger_salute", displayNameRes = R.string.gesture_three_finger_salute)
    val FIST = Gesture(id = "fist", displayNameRes = R.string.gesture_fist)
    val MIDDLE_FINGER = Gesture(id = "middle_finger", displayNameRes = R.string.gesture_middle_finger)
    val ROCK = Gesture(id = "rock", displayNameRes = R.string.gesture_rock)
    val RECTANGLE_FRAME = Gesture(id = "rectangle_frame", displayNameRes = R.string.gesture_rectangle_frame)
    val ELEVEN = Gesture(id = "eleven", displayNameRes = R.string.gesture_eleven)
    val SCISSOR = Gesture(id = "scissor", displayNameRes = R.string.gesture_scissor)
    val ANGLE = Gesture(id = "angle", displayNameRes = R.string.gesture_angle)
    val VICTORY = Gesture(id = "victory", displayNameRes = R.string.gesture_victory)
}

/**
 * 한 자유도 의수의 손 모양 목록. [gestures] 안의 위치(0부터)가 곧 펌웨어 MSET 액션 ID 다.
 *
 * - 0번은 대기 손 모양([Gestures.FLAT_HAND])으로 고정 — 자동으로 붙으므로 [groups] 에는 넣지 않는다.
 * - [groups] 는 Pair(앞·뒤 두 손 모양) 또는 단일 손 모양 묶음을 적은 순서. 1번부터 차례로 번호가 붙는다.
 * - 사진은 `assets/<[assetFolder]>/<액션 ID 두 자리>_<id>.<확장자>` 에 둔다 ([imageAssetPath]).
 */
class GestureCatalog(
    val assetFolder: String,
    val groups: List<GestureSlots>,
    val imageExtension: String = "png",
    val imageExtensionOverrides: Map<String, String> = emptyMap(),
) {
    /** 0번 대기 손 모양 + [groups] 를 차례로 편 목록. 이 목록 안의 위치가 액션 ID 다. */
    val gestures: List<Gesture> = listOf(Gestures.FLAT_HAND) + groups.flatMap { listOfNotNull(it.lead, it.companion) }

    init {
        require(gestures.drop(1).all { it.enabled }) { "대기 손 모양은 0번에 자동으로 들어가므로 목록에 넣지 않는다: $groups" }
        require(gestures.map { it.id }.toSet().size == gestures.size) { "손 모양 id 가 중복됐다: $gestures" }
        require(gestures.size <= MAX_ACTION_ID + 1) { "액션 ID 는 1바이트(0..$MAX_ACTION_ID)까지다" }
    }

    /** S0 고정 상태가 쓰는 기본 대기 손 모양. */
    val idle: Gesture = gestures.first()

    /** 손 선택 화면에 보여줄 후보. 대기 손 모양은 S0 기본값이라 뺀다. */
    val selectable: List<Gesture> = gestures.filter { it.enabled }

    /** S1~S4는 쌍 제스처, S5·S7은 단일 제스처. 필터링해도 원래 액션 ID는 유지한다. */
    fun selectableForState(stateId: Int): List<Gesture> = groupsForState(stateId)
        .flatMap { listOfNotNull(it.lead, it.companion) }

    private fun groupsForState(stateId: Int): List<GestureSlots> = when (stateId) {
        in 1..4 -> groups.filter { it.companion != null }
        5, 7 -> groups.filter { it.companion == null }
        else -> emptyList()
    }

    fun slotGesturesForState(stateId: Int, id: String?): GestureSlots? =
        slotGesturesFor(id)?.takeIf { it in groupsForState(stateId) }

    /** 손 모양 id → 그 손 모양이 속한 묶음. */
    private val groupByGestureId: Map<String, GestureSlots> =
        groups.flatMap { group -> listOfNotNull(group.lead, group.companion).map { it.id to group } }.toMap()

    /**
     * 처음 설치 시 상단 두 쌍과 하단 두 단일 제스처를 각 목록 순서대로 채운다.
     * 사용자가 한 번 고르면 [ActionMapping.gestureIdByState] 에 저장되어 이 값을 덮는다.
     */
    val defaultStateGestures: Map<Int, String> = ActionSlotPairs.ALL
        .flatMap { slot ->
            val index = when (slot.primaryStateId) { 1, 5 -> 0; else -> 1 }
            val group = groupsForState(slot.primaryStateId).getOrNull(index) ?: return@flatMap emptyList()
            listOfNotNull(
                slot.primaryStateId to group.lead.id,
                group.companion?.let { slot.companionStateId to it.id },
            )
        }
        .toMap()

    fun byId(id: String?): Gesture? {
        if (id == null) return null
        if (id == LEGACY_IDLE_ID) return idle
        val canonicalId = LEGACY_IDS[id] ?: id
        return gestures.firstOrNull { it.id == canonicalId }
    }

    /** 이 목록 안에서의 위치 = 펌웨어로 보내는 액션 ID. 목록에 없는 손 모양이면 null. */
    fun actionIdFor(id: String?): Int? {
        val gesture = byId(id) ?: return null
        return gestures.indexOf(gesture).takeIf { it >= 0 }
    }

    /** 손 모양 사진의 assets 경로 — `<폴더>/<액션 ID 두 자리>_<id>.<확장자>`. 목록에 없으면 null. */
    fun imageAssetPath(gesture: Gesture): String? {
        val actionId = actionIdFor(gesture.id) ?: return null
        val extension = imageExtensionOverrides[gesture.id] ?: imageExtension
        return "$assetFolder/${actionId.toString().padStart(2, '0')}_${gesture.id}.$extension"
    }

    /**
     * 손 모양이 속한 묶음. Pair 는 둘 중 무엇을 골랐든 같은 묶음(앞=[GestureSlots.lead], 뒤=[GestureSlots.companion])이다.
     * 선택할 수 없는 손 모양(대기)이나 이 목록에 없는 ID 는 null.
     */
    fun slotGesturesFor(id: String?): GestureSlots? = byId(id)?.let { groupByGestureId[it.id] }

    companion object {
        private const val MAX_ACTION_ID = 255
        private const val LEGACY_IDLE_ID = "idle"

        /** 예전 저장값(HandAction 시절 id)을 지금 손 모양 id 로 읽기 위한 대응표. */
        private val LEGACY_IDS = mapOf(
            "flexion" to "fist",
            "extension" to "flat_hand",
            "close" to "fist",
            "rest" to "flat_hand",
            "pinch" to "tip_pinch_closed",
            "tripod" to "tripod_closed",
            "point" to "pointing",
        )
    }
}
