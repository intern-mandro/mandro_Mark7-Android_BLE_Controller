package com.mandro.mark7.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mandro.mark7.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "mark7_users")

/**
 * 로컬 사용자 목록 + 현재 활성 사용자 id 저장소. `mark7_settings` 와 별도 DataStore.
 * 설정 데이터 자체는 [HandConfigStore] 가 활성 사용자 id 로 네임스페이스를 나눠 저장한다.
 */
@Singleton
class UserStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.userDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val usersKey = stringPreferencesKey("users_json")
    private val activeKey = stringPreferencesKey("active_user_id")

    val users: Flow<List<UserProfile>> = ds.data.map { prefs ->
        prefs[usersKey]?.let { runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrNull() }
            ?: emptyList()
    }

    /** 활성 사용자 id. 아직 아무도 없으면 null. */
    val activeUserId: Flow<String?> = ds.data.map { it[activeKey] }

    suspend fun activeUserIdOnce(): String? = activeUserId.first()

    suspend fun hasAnyUser(): Boolean = users.first().isNotEmpty()

    /** 새 사용자 생성 후 바로 활성 사용자로 지정. */
    suspend fun createUser(name: String): UserProfile {
        val user = UserProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "사용자" },
            createdAt = System.currentTimeMillis(),
        )
        ds.edit { prefs ->
            val current = prefs[usersKey]
                ?.let { runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrNull() }
                ?: emptyList()
            prefs[usersKey] = json.encodeToString(current + user)
            prefs[activeKey] = user.id
        }
        return user
    }

    suspend fun setActiveUser(id: String) {
        ds.edit { it[activeKey] = id }
    }

    /** 사용자 삭제. 삭제 대상이 활성 사용자였으면 남은 사용자 중 하나로 옮기거나(없으면) 활성 해제. */
    suspend fun deleteUser(id: String) {
        ds.edit { prefs ->
            val current = prefs[usersKey]
                ?.let { runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrNull() }
                ?: emptyList()
            val next = current.filterNot { it.id == id }
            prefs[usersKey] = json.encodeToString(next)
            if (prefs[activeKey] == id) {
                val fallback = next.firstOrNull()?.id
                if (fallback != null) prefs[activeKey] = fallback else prefs.remove(activeKey)
            }
        }
    }
}
