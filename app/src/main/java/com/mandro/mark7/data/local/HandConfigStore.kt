package com.mandro.mark7.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.HandAction
import com.mandro.mark7.domain.model.HandConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SET 설정([HandConfig])과 액션 매핑([ActionMapping])의 로컬 영속화.
 * JSON 문자열로 직렬화해 Preferences DataStore 에 저장한다 (Room 은 나중에 다중
 * 프리셋 관리가 필요해지면 `data/local/db/` 로 추가).
 */
@Singleton
class HandConfigStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val configKey = stringPreferencesKey("hand_config_json")
    private val mappingKey = stringPreferencesKey("action_mapping_json")

    val config: Flow<HandConfig> = dataStore.data.map { prefs ->
        prefs[configKey]?.let { runCatching { json.decodeFromString<HandConfig>(it) }.getOrNull() }
            ?: HandConfig.DEFAULT
    }

    val actionMapping: Flow<ActionMapping> = dataStore.data.map { prefs ->
        prefs[mappingKey]?.let { runCatching { json.decodeFromString<StoredMapping>(it) }.getOrNull() }
            ?.toDomain() ?: ActionMapping()
    }

    suspend fun saveConfig(config: HandConfig) {
        dataStore.edit { it[configKey] = json.encodeToString(config) }
    }

    suspend fun saveMapping(mapping: ActionMapping) {
        dataStore.edit { it[mappingKey] = json.encodeToString(StoredMapping.fromDomain(mapping)) }
    }

    @Serializable
    private data class StoredMapping(
        val byAction: Map<String, Int> = emptyMap(),
        val gradual: Boolean = false,
    ) {
        fun toDomain() = ActionMapping(
            patternIndexByAction = byAction.mapNotNull { (k, v) ->
                runCatching { HandAction.valueOf(k) }.getOrNull()?.let { it to v }
            }.toMap(),
            gradualGraspEnabled = gradual,
        )

        companion object {
            fun fromDomain(m: ActionMapping) = StoredMapping(
                byAction = m.patternIndexByAction.mapKeys { it.key.name },
                gradual = m.gradualGraspEnabled,
            )
        }
    }
}
