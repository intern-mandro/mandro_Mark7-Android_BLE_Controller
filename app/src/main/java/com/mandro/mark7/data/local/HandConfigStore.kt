package com.mandro.mark7.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.DEFAULT_STATE_GESTURES
import com.mandro.mark7.domain.model.HandAction
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.ManualPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SET 설정([HandConfig]), 액션 매핑([ActionMapping]), Manual 프리셋의 로컬 영속화.
 *
 * **사용자별 분리 저장.** 모든 키에 활성 사용자 id 를 접미사로 붙인다
 * (`hand_config_json__<userId>`). 활성 사용자가 바뀌면 flow 들이 그 사용자의 값으로
 * 자동 재방출된다. 활성 사용자가 아직 없으면 [FALLBACK_UID] 버킷을 쓴다.
 */
@Singleton
class HandConfigStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val userStore: UserStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun configKey(uid: String) = stringPreferencesKey("hand_config_json__$uid")
    private fun mappingKey(uid: String) = stringPreferencesKey("action_mapping_json__$uid")
    private fun presetsKey(uid: String) = stringPreferencesKey("manual_presets_json__$uid")

    private suspend fun uid(): String = userStore.activeUserIdOnce() ?: FALLBACK_UID

    val config: Flow<HandConfig> =
        combine(userStore.activeUserId, dataStore.data) { activeId, prefs ->
            prefs[configKey(activeId ?: FALLBACK_UID)]
                ?.let { runCatching { json.decodeFromString<HandConfig>(it) }.getOrNull() }
                ?: HandConfig.DEFAULT
        }.distinctUntilChanged()

    /**
     * 저장된 매핑이 있으면 그대로(비어 있어도 그대로 — 사용자가 전부 지운 상태를 존중),
     * 아직 한 번도 저장한 적 없으면 [DEFAULT_STATE_GESTURES] 로 S2..S5 를 채운 초기값.
     */
    val actionMapping: Flow<ActionMapping> =
        combine(userStore.activeUserId, dataStore.data) { activeId, prefs ->
            prefs[mappingKey(activeId ?: FALLBACK_UID)]
                ?.let { runCatching { json.decodeFromString<StoredMapping>(it) }.getOrNull() }
                ?.toDomain() ?: ActionMapping(gestureIdByState = DEFAULT_STATE_GESTURES)
        }.distinctUntilChanged()

    val manualPresets: Flow<List<ManualPreset>> =
        combine(userStore.activeUserId, dataStore.data) { activeId, prefs ->
            prefs[presetsKey(activeId ?: FALLBACK_UID)]
                ?.let { runCatching { json.decodeFromString<List<ManualPreset>>(it) }.getOrNull() }
                ?: ManualPreset.DEFAULT_PRESETS
        }.distinctUntilChanged()

    suspend fun saveConfig(config: HandConfig) {
        val key = configKey(uid())
        dataStore.edit { it[key] = json.encodeToString(config) }
    }

    suspend fun saveMapping(mapping: ActionMapping) {
        val key = mappingKey(uid())
        dataStore.edit { it[key] = json.encodeToString(StoredMapping.fromDomain(mapping)) }
    }

    suspend fun saveManualPresets(presets: List<ManualPreset>) {
        val key = presetsKey(uid())
        dataStore.edit { it[key] = json.encodeToString(presets) }
    }

    suspend fun resetManualPresets() {
        val key = presetsKey(uid())
        dataStore.edit { it.remove(key) }
    }

    /** 사용자 삭제 시 그 사용자의 모든 설정 키 제거. */
    suspend fun deleteUserData(uid: String) {
        dataStore.edit {
            it.remove(configKey(uid))
            it.remove(mappingKey(uid))
            it.remove(presetsKey(uid))
        }
    }

    @Serializable
    private data class StoredMapping(
        val byAction: Map<String, Int> = emptyMap(),
        val byState: Map<String, String> = emptyMap(),
        val gradual: Boolean = false,
    ) {
        fun toDomain() = ActionMapping(
            patternIndexByAction = byAction.mapNotNull { (k, v) ->
                runCatching { HandAction.valueOf(k) }.getOrNull()?.let { it to v }
            }.toMap(),
            gestureIdByState = byState.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
            gradualGraspEnabled = gradual,
        )

        companion object {
            fun fromDomain(m: ActionMapping) = StoredMapping(
                byAction = m.patternIndexByAction.mapKeys { it.key.name },
                byState = m.gestureIdByState.mapKeys { it.key.toString() },
                gradual = m.gradualGraspEnabled,
            )
        }
    }

    companion object {
        /** 활성 사용자가 아직 지정되지 않았을 때 쓰는 버킷 id. */
        const val FALLBACK_UID = "_default"
    }
}
