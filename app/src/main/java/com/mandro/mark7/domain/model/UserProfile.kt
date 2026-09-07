package com.mandro.mark7.domain.model

import kotlinx.serialization.Serializable

/**
 * 로컬 사용자(프로필). 모든 설정(HandConfig / ActionMapping / ManualPreset)은
 * 활성 사용자 [id] 기준으로 분리 저장된다. — [com.mandro.mark7.data.local.HandConfigStore]
 */
@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val createdAt: Long = 0L,
)
