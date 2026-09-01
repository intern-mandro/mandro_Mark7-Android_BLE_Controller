package com.mandro.mark7.domain.model

/** CMD 프레임 dir 필드 (`config.h::CmdDir`). */
enum class CmdDir(val wire: Int) {
    STOP(0),
    GRASP(1),
    RELEASE(2),
    RESET_COUNTER(3),
    RESET_POWER(4),
}

/**
 * 수동 제어 화면에서 만드는 직접 구동 명령.
 * [MarkSevenProtocol.buildCmd] 의 인자로 그대로 넘어간다.
 */
data class MotorCommand(
    val select: BooleanArray,       // 길이 6
    val speedRaw: Int = 0,
    val currentMa: Int = 0,
    val posDeg: IntArray,           // 길이 6
    val dir: CmdDir = CmdDir.GRASP,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MotorCommand) return false
        return select.contentEquals(other.select) &&
            speedRaw == other.speedRaw &&
            currentMa == other.currentMa &&
            posDeg.contentEquals(other.posDeg) &&
            dir == other.dir
    }

    override fun hashCode(): Int {
        var result = select.contentHashCode()
        result = 31 * result + speedRaw
        result = 31 * result + currentMa
        result = 31 * result + posDeg.contentHashCode()
        result = 31 * result + dir.hashCode()
        return result
    }

    companion object {
        fun allFingers(dir: CmdDir) = MotorCommand(
            select = BooleanArray(6) { true },
            posDeg = IntArray(6),
            dir = dir,
        )
    }
}
