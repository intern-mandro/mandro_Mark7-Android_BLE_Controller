package com.mandro.mark7.domain.model.hand

import androidx.annotation.StringRes
import com.mandro.mark7.R

enum class HandDof(
    val dof: Int,                              // 실제 모터(자유도) 개수
    val id: String,                            // 식별용 문자열 키
    @StringRes val titleRes: Int,              // 제목
    @StringRes val descRes: Int,               // 설명
    @StringRes val badgeRes: Int? = null,
) {
    DOF_7(
        dof = 7,
        id = "dof_7",
        titleRes = R.string.dof_7_title,
        descRes = R.string.dof_7_desc,
        badgeRes = R.string.dof_extended_badge,
    ),
    DOF_6(
        dof = 6,
        id = "dof_6",
        titleRes = R.string.dof_6_title,
        descRes = R.string.dof_6_desc,
        badgeRes = R.string.dof_standard_badge,
    ),
    DOF_5(
        dof = 5,
        id = "dof_5",
        titleRes = R.string.dof_5_title,
        descRes = R.string.dof_5_desc,
        badgeRes = null,
    );

    /** 의수 버전 대표 사진 에셋 경로 (Coil AsyncImage 용) */
    val imageAssetPath: String
        get() = when (this) {
            DOF_5 -> "file:///android_asset/dof_models/dof_5.jpg"
            DOF_6 -> "file:///android_asset/dof_models/dof_6.png"
            DOF_7 -> "file:///android_asset/dof_models/dof_7.png"
        }

    /** 이 자유도에 대응하는 모터 전체 표시 이름 리소스 목록. */
    val motorNameRes: List<Int>
        get() = when (this) {
            DOF_5 -> listOf(
                R.string.motor_f1, R.string.motor_f2, R.string.motor_f3,
                R.string.motor_f4, R.string.motor_f5,
            )
            DOF_6 -> listOf(
                R.string.motor_f1, R.string.motor_f2, R.string.motor_f3,
                R.string.motor_f4, R.string.motor_f5, R.string.motor_f6,
            )
            DOF_7 -> listOf(
                R.string.motor_f1, R.string.motor_f2, R.string.motor_f3,
                R.string.motor_f4, R.string.motor_f5, R.string.motor_f6,
                R.string.motor_f7,
            )
        }

    /** 이 자유도에 대응하는 모터 짧은 이름 리소스 목록(수동 제어 칩용). */
    val motorShortRes: List<Int>
        get() = when (this) {
            DOF_5 -> listOf(
                R.string.motor_short_f1, R.string.motor_short_f2, R.string.motor_short_f3,
                R.string.motor_short_f4, R.string.motor_short_f5,
            )
            DOF_6 -> listOf(
                R.string.motor_short_f1, R.string.motor_short_f2, R.string.motor_short_f3,
                R.string.motor_short_f4, R.string.motor_short_f5, R.string.motor_short_f6,
            )
            DOF_7 -> listOf(
                R.string.motor_short_f1, R.string.motor_short_f2, R.string.motor_short_f3,
                R.string.motor_short_f4, R.string.motor_short_f5, R.string.motor_short_f6,
                R.string.motor_short_f7,
            )
        }

    companion object {
        /** 앱 기본 의수 버전: 6자유도 */
        val DEFAULT = DOF_6

        fun fromDof(dof: Int): HandDof =
            entries.firstOrNull { it.dof == dof } ?: DEFAULT

        fun fromId(id: String?): HandDof =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
