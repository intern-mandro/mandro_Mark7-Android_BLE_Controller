package com.mandro.mark7.domain.model

import androidx.annotation.StringRes
import com.mandro.mark7.R

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
 * - [id]    1..8 (S1..S8). 펌웨어 패턴 슬롯 인덱스 + 1.
 * - [x],[y] 다이어그램 영역 안에서의 중심 위치(0f..1f 정규화).
 * - [fixed] true 면 편집 불가. S1 은 항상 대기(idle) 상태로 고정.
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
        const val IDLE_STATE_ID = 1

        /**
         * `docs/mode_flow.png` 그대로: idle(S1) + 상태 4개, 두 체인으로 갈라진다.
         *
         * - F 체인 : S1 -F→ S2 -F→ S3,  각 단계 E 로 한 칸 복귀
         * - E 체인 : S1 -E→ S4 -F→ S5,  각 단계 E 로 한 칸 복귀
         * - 리프(S3·S5)에서 F 는 제자리 유지(전이 없음)
         *
         * 전이 배선은 펌웨어 `transitionTable` 고정값. 상태 수(4)도 고정.
         */
        val MODE_1: ModeFlowGraph = ModeFlowGraph(
            states = listOf(
                ModeState(id = 1, x = 0.10f, y = 0.50f, fixed = true),
                ModeState(id = 2, x = 0.46f, y = 0.16f),
                ModeState(id = 3, x = 0.84f, y = 0.16f),
                ModeState(id = 4, x = 0.46f, y = 0.84f),
                ModeState(id = 5, x = 0.84f, y = 0.84f),
            ),
            transitions = listOf(
                ModeTransition(1, 2, ModeInput.FLEXION),
                ModeTransition(2, 1, ModeInput.EXTENSION),
                ModeTransition(2, 3, ModeInput.FLEXION),
                ModeTransition(3, 2, ModeInput.EXTENSION),
                ModeTransition(1, 4, ModeInput.EXTENSION),
                ModeTransition(4, 1, ModeInput.EXTENSION),
                ModeTransition(4, 5, ModeInput.FLEXION),
                ModeTransition(5, 4, ModeInput.EXTENSION),
            ),
        )

        val MODE_2: ModeFlowGraph = ModeFlowGraph(
            states = listOf(
                ModeState(id = 1, x = 0.10f, y = 0.50f, fixed = true),
                ModeState(id = 6, x = 0.46f, y = 0.16f),
                ModeState(id = 7, x = 0.84f, y = 0.16f),
                ModeState(id = 8, x = 0.46f, y = 0.84f),
                ModeState(id = 9, x = 0.84f, y = 0.84f),
            ),
            transitions = listOf(
                ModeTransition(1, 6, ModeInput.FLEXION),
                ModeTransition(6, 1, ModeInput.EXTENSION),
                ModeTransition(6, 7, ModeInput.FLEXION),
                ModeTransition(7, 6, ModeInput.EXTENSION),
                ModeTransition(1, 8, ModeInput.EXTENSION),
                ModeTransition(8, 1, ModeInput.EXTENSION),
                ModeTransition(8, 9, ModeInput.FLEXION),
                ModeTransition(9, 8, ModeInput.EXTENSION),
            ),
        )

        val DEFAULT: ModeFlowGraph = MODE_1
    }
}

/**
 * 노드에 넣을 수 있는 손 모양 후보. 지금은 `assets/gesture_guides/<assetDir>` 의
 * 4개 카테고리를 임시 카탈로그로 쓴다 — reference.png 처럼 grip 종류(정밀집기·삼각집기…)
 * 로 넓히려면 여기에 항목과 사진을 추가하면 된다.
 */
data class Gesture(
    val id: String,
    @StringRes val displayNameRes: Int,
    val assetDir: String,
)

object GestureCatalog {
    val ALL: List<Gesture> = listOf(
        Gesture(id = "flexion", displayNameRes = R.string.gesture_flexion, assetDir = "flexion"),
        Gesture(id = "extension", displayNameRes = R.string.gesture_extension, assetDir = "extension"),
        Gesture(id = "close", displayNameRes = R.string.gesture_close, assetDir = "close"),
        Gesture(id = "rest", displayNameRes = R.string.gesture_rest, assetDir = "rest"),
        // 아래 4개는 아직 assets/gesture_guides 사진이 없어 그리드에서 "사진" 자리표시자로 뜬다.
        // 사진을 추가하려면 assetDir 이름의 폴더에 fNN.jpg 를 넣으면 자동으로 썸네일이 잡힌다.
        Gesture(id = "pinch", displayNameRes = R.string.gesture_pinch, assetDir = "pinch"),
        Gesture(id = "tripod", displayNameRes = R.string.gesture_tripod, assetDir = "tripod"),
        Gesture(id = "hook", displayNameRes = R.string.gesture_hook, assetDir = "hook"),
        Gesture(id = "point", displayNameRes = R.string.gesture_point, assetDir = "point"),
    )

    /** S1 고정 상태가 쓰는 대기 손 모양. */
    val IDLE: Gesture = Gesture(id = "idle", displayNameRes = R.string.gesture_idle, assetDir = "rest")

    fun byId(id: String?): Gesture? = when (id) {
        null -> null
        IDLE.id -> IDLE
        else -> ALL.firstOrNull { it.id == id }
    }
}

/**
 * S2..S9 를 우선 임시로 채워 화면이 비어 보이지 않게 하는 표시용 기본값.
 * 사용자가 실제로 한 번 고르면 [ActionMapping.gestureIdByState] 에 저장되어 이 값을 덮는다.
 */
val DEFAULT_STATE_GESTURES: Map<Int, String> = mapOf(
    2 to "flexion",
    3 to "extension",
    4 to "close",
    5 to "rest",
    6 to "flexion",
    7 to "extension",
    8 to "close",
    9 to "rest",
)
