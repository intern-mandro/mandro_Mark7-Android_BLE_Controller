package com.mandro.mark7.domain.model.action

import androidx.annotation.StringRes
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.hand.HandDof


// 암밴드가 인식하는 근전도 신호 4종류
enum class HandAction(
    @StringRes val displayNameRes: Int,
) {
    FLEXION(R.string.gesture_flexion),
    EXTENSION(R.string.gesture_extension),
    CLOSE(R.string.gesture_close),
    REST(R.string.gesture_rest),
}

// 앱 로컬 저장용 매핑 묶음 (의수 자유도별로 따로 저장)
data class ActionMapping(
    val patternIndexByAction: Map<HandAction, Int> = emptyMap(),
    val gestureIdByState: Map<Int, String> = emptyMap(),
    val gradualGraspEnabled: Boolean = false,
    val dof: HandDof = HandDof.DEFAULT,
) {
    // dof가 바뀌면 고를 수 있는 손 모양 목록 자체가 바뀌므로, 저장하지 않고 매번 계산해서 가져옴
    val catalog: GestureCatalog get() = GestureCatalogs.forDof(dof)

    // 단순 조회용 헬퍼 (가공 없이 원본 그대로 반환)
    fun gestureIdFor(stateId: Int): String? = gestureIdByState[stateId]

    // Pair 규칙까지 적용한 뒤 해당 상태에 실제로 사용할 손 모양 ID를 반환
    fun effectiveGestureIdFor(stateId: Int): String? {

        // 이 상태(stateId)가 짝(Pair)에 속하지 않으면 짝 규칙 적용 없이 저장된 값 그대로 반환
        val pair = ActionSlotPairs.pairForState(stateId) ?: return gestureIdFor(stateId)

        // 짝에 속할 경우, 짝의 앞 슬롯(Primary)에 저장된 손 모양을 기준으로 lead/companion 다시 계산
        val slots = catalog.slotGesturesForState(pair.primaryStateId, gestureIdFor(pair.primaryStateId)) ?: return null
        // stateId가 앞 슬롯이면 lead를, 뒤 슬롯이면 companion 반환
        return if (stateId == pair.primaryStateId) slots.lead.id else slots.companion?.id
    }

    // 앞 슬롯은 선택 가능, 뒤 슬롯은 Pair 자동 지정 또는 단일 액션에 따른 비활성 상태
    fun slotModeFor(stateId: Int): ActionSlotMode {

        // 짝이 없는 상태는 비활성 (Pair 자체가 정의 안된 stateId)
        val pair = ActionSlotPairs.pairForState(stateId) ?: return ActionSlotMode.DISABLED

        // 앞 슬롯이면 항상 직접 편집 가능
        if (stateId == pair.primaryStateId) return ActionSlotMode.EDITABLE

        // 뒤 슬롯인 경우, 계산된 실제 값이 있으면 자동 지정됨 표시 / 없으면 비활성 처리
        return if (effectiveGestureIdFor(stateId) != null) {
            ActionSlotMode.AUTO_ASSIGNED
        } else {
            ActionSlotMode.DISABLED
        }
    }

    // 앞 슬롯 묶음에 액션을 지정함 (Pair는 둘 중 무엇을 골랐든 앞=홀수, 뒤=짝수로 채움 / 단일 액션은 앞 슬롯만 채운 뒤 뒤 슬롯은 비활성)
    fun assignGesture(primaryStateId: Int, gestureId: String): ActionMapping {

        // PrimaryStateId가 실제로 어떤 Pair의 앞인지 확인 (아니면 아무것도 안하고 그대로 반환)
        val pair = ActionSlotPairs.pairForPrimary(primaryStateId) ?: return this

        // 고른 gestureId에 대응하는 lead/companion 손 모양 세트를 카탈로그에서 조회
        val slots = catalog.slotGesturesForState(primaryStateId, gestureId) ?: return this

        // 1) 앞 슬롯에 lead 저장, 2) 혹시 남아있던 뒤 슬롯 값은 일단 제거
        val withLead = gestureIdByState + (primaryStateId to slots.lead.id) - pair.companionStateId
        // companion이 존재하면 뒤 슬롯에 채움, 없으면 withLead 그대로 둬서 뒤 슬롯은 빈 상태 유지
        val updated = slots.companion?.let { withLead + (pair.companionStateId to it.id) } ?: withLead
        return copy(gestureIdByState = updated)
    }

    fun clearGestureGroup(primaryStateId: Int): ActionMapping {
        val pair = ActionSlotPairs.pairForPrimary(primaryStateId) ?: return this
        // 앞/뒤 두 슬롯을 한 번에 삭제 (묶음 단위 데이터 삭제)
        return copy(gestureIdByState = gestureIdByState - primaryStateId - pair.companionStateId)
    }

    // MSET의 S1~S8 슬롯을 항상 8개의 펌웨어 액션ID로 만듦 (비활성 슬롯ID는 99)
    fun toActionIds(): IntArray = (1..8)
        .map { stateId ->
            catalog.actionIdFor(effectiveGestureIdFor(stateId)) ?: 99
        }
        .toIntArray()
}

// 노드 한 칸의 상태
enum class ActionSlotMode(val opensPicker: Boolean) {
    EDITABLE(opensPicker = true),       // Pair 묶음의 앞 노드 (항상 고를 수 있음)
    AUTO_ASSIGNED(opensPicker = true),  // Pair로 자동 지정된 뒤 노드(눌러서 묶음을 다시 고를 수 있음)
    DISABLED(opensPicker = false),      // 단일 액션 옆이거나 비어있는 묶음의 뒤 노드
}

// 앞(Primary) - 뒤(Companion) 슬롯 한 쌍을 나타내는 단순 데이터 클래스
data class ActionSlotPair(
    val primaryStateId: Int,
    val companionStateId: Int,
)

object ActionSlotPairs {

    // 전체 8개 상태를 4쌍으로 고정 정의 (1, 2) (3, 4) (5, 6) (7, 8)
    val ALL: List<ActionSlotPair> = listOf(
        ActionSlotPair(primaryStateId = 1, companionStateId = 2),
        ActionSlotPair(primaryStateId = 3, companionStateId = 4),
        ActionSlotPair(primaryStateId = 5, companionStateId = 6),
        ActionSlotPair(primaryStateId = 7, companionStateId = 8),
    )

    // 주어진 stateId가 어떤 쌍의 앞인지 찾기
    fun pairForPrimary(stateId: Int): ActionSlotPair? =
        ALL.firstOrNull { it.primaryStateId == stateId }

    // 주어진 stateId가 앞이든 뒤든 속한 쌍을 찾기
    fun pairForState(stateId: Int): ActionSlotPair? =
        ALL.firstOrNull { stateId == it.primaryStateId || stateId == it.companionStateId }
}
