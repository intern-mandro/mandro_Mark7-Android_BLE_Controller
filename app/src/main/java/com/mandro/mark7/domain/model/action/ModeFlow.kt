package com.mandro.mark7.domain.model.action

/**
 * 홈(액션) 탭에 그리는 상태 전이 다이어그램의 정의.
 *
 * 노트북 스케치(`docs/mode_flow.png`)의 S0..S8 / F·E 화살표를 그대로 옮긴 것.
 * 각 상태(S1..S8)는 펌웨어의 MSS1..MSS8 슬롯에 대응하고, 상태 사이 전이는
 * 펌웨어 `transitionTable` 이 고정으로 갖는다 — 앱은 "각 상태가 어떤 손 모양인지"
 * 만 채운다([ActionMapping.gestureIdByState]).
 *
 * 지금 [ModeFlowGraph.DEFAULT] 의 전이 배선은 스케치의 형태(F 로 더 깊이, E 로
 * 한 단계 복귀)를 8슬롯으로 일반화한 임시 값이다. 실제 펌웨어 표와 맞추는 작업은 TODO.
 */
enum class ModeInput(val short: String) {
    FLEXION("F"),
    EXTENSION("E"),
}

/**
 * 다이어그램의 노드 하나.
 *
 * - [id]    0..8 (S0..S8). S0는 IDLE, S1..S8은 MSET action_id[0..7]에 대응한다.
 * - [x],[y] 다이어그램 영역 안에서의 중심 위치(0f..1f 정규화).
 * - [fixed] true 면 편집 불가. S0 은 항상 대기(idle) 상태로 고정.
 */
data class ModeState(
    val id: Int,
    val x: Float,
    val y: Float,
    val fixed: Boolean = false,
) {
    val label: String get() = "S$id"
}

/** [from] 상태에서 [input] 입력을 받으면 [to] 상태로 간다. */
data class ModeTransition(
    val from: Int,
    val to: Int,
    val input: ModeInput,
)

data class ModeFlowGraph(
    val states: List<ModeState>,
    val transitions: List<ModeTransition>,
) {
    fun state(id: Int): ModeState? = states.firstOrNull { it.id == id }

    companion object {
        const val IDLE_STATE_ID = 0

        /**
         * `docs/mode_flow.png` 그대로: idle(S0) + 상태 4개, 두 체인으로 갈라진다.
         *
         * - F 체인 : S0 -F→ S1 -F→ S2,  각 단계 E 로 한 칸 복귀
         * - E 체인 : S0 -E→ S3 -F→ S4,  각 단계 E 로 한 칸 복귀
         * - 리프(S2·S4)에서 F 는 제자리 유지(전이 없음)
         *
         * 전이 배선은 펌웨어 `transitionTable` 고정값. 상태 수(4)도 고정.
         */
        val MODE_1: ModeFlowGraph = ModeFlowGraph(
            states = listOf(
                ModeState(id = 0, x = 0.10f, y = 0.50f, fixed = true),
                ModeState(id = 1, x = 0.46f, y = 0.16f),
                ModeState(id = 2, x = 0.84f, y = 0.16f),
                ModeState(id = 3, x = 0.46f, y = 0.84f),
                ModeState(id = 4, x = 0.84f, y = 0.84f),
            ),
            transitions = listOf(
                ModeTransition(0, 1, ModeInput.FLEXION),
                ModeTransition(1, 0, ModeInput.EXTENSION),
                ModeTransition(1, 2, ModeInput.FLEXION),
                ModeTransition(2, 1, ModeInput.EXTENSION),
                ModeTransition(0, 3, ModeInput.EXTENSION),
                ModeTransition(3, 0, ModeInput.EXTENSION),
                ModeTransition(3, 4, ModeInput.FLEXION),
                ModeTransition(4, 3, ModeInput.EXTENSION),
            ),
        )

        val MODE_2: ModeFlowGraph = ModeFlowGraph(
            states = listOf(
                ModeState(id = 0, x = 0.10f, y = 0.50f, fixed = true),
                ModeState(id = 5, x = 0.46f, y = 0.16f),
                ModeState(id = 6, x = 0.84f, y = 0.16f),
                ModeState(id = 7, x = 0.46f, y = 0.84f),
                ModeState(id = 8, x = 0.84f, y = 0.84f),
            ),
            transitions = listOf(
                ModeTransition(0, 5, ModeInput.FLEXION),
                ModeTransition(5, 0, ModeInput.EXTENSION),
                ModeTransition(5, 6, ModeInput.FLEXION),
                ModeTransition(6, 5, ModeInput.EXTENSION),
                ModeTransition(0, 7, ModeInput.EXTENSION),
                ModeTransition(7, 0, ModeInput.EXTENSION),
                ModeTransition(7, 8, ModeInput.FLEXION),
                ModeTransition(8, 7, ModeInput.EXTENSION),
            ),
        )

        val DEFAULT: ModeFlowGraph = MODE_1
    }
}
