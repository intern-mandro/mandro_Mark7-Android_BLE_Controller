package com.mandro.mark7.domain.model

import androidx.annotation.StringRes
import com.mandro.mark7.R

/**
 * 로봇 의수 자유도(DOF) 버전.
 *
 * - [DOF_5]: 5자유도 (F1 엄지 ~ F5 소지, 5개 모터)
 * - [DOF_6]: 6자유도 (F1 엄지 ~ F5 소지 + F6 엄지외전, 6개 모터 — Mark7 표준)
 * - [DOF_7]: 7자유도 (F1 엄지 ~ F6 엄지외전 + F7 손목회전, 7개 모터 — 확장 모델)
 */
enum class HandDof(
    val dof: Int,
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
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
