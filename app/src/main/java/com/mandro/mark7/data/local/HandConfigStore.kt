package com.mandro.mark7.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mandro.mark7.domain.model.ActionMapping
import com.mandro.mark7.domain.model.DEFAULT_STATE_GESTURES
import com.mandro.mark7.domain.model.HandAction
import com.mandro.mark7.domain.model.HandConfig
import com.mandro.mark7.domain.model.HandDof
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
 * **의수 자유도(DOF)별 분리 저장.** 모든 키에 활성 자유도 id 를 접미사로 붙인다
 * (`hand_config_json__<dofId>`). 활성 자유도가 바뀌면 flow 들이 그 버전의 값으로
 * 자동 재방출된다.
 */
@Singleton
class HandConfigStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val handVersionStore: HandVersionStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun configKey(dofId: String) = stringPreferencesKey("hand_config_json__$dofId")
    private fun mappingKey(dofId: String) = stringPreferencesKey("action_mapping_json__$dofId")
    private fun presetsKey(dofId: String) = stringPreferencesKey("manual_presets_json__$dofId")

    val activeDof: Flow<HandDof> = handVersionStore.activeDof

    private suspend fun dofId(): String = handVersionStore.activeDofOnce().id

    val config: Flow<HandConfig> =
        combine(handVersionStore.activeDof, dataStore.data) { dof, prefs ->
            prefs[configKey(dof.id)]
                ?.let { runCatching { json.decodeFromString<HandConfig>(it) }.getOrNull() }
                ?: HandConfig.DEFAULT
        }.distinctUntilChanged()

    /**
     * 저장된 매핑이 있으면 그대로(비어 있어도 그대로 — 사용자가 전부 지운 상태를 존중),
     * 아직 한 번도 저장한 적 없으면 [DEFAULT_STATE_GESTURES] 로 S2..S5 를 채운 초기값.
     */
    val actionMapping: Flow<ActionMapping> =
        combine(handVersionStore.activeDof, dataStore.data) { dof, prefs ->
            prefs[mappingKey(dof.id)]
                ?.let { runCatching { json.decodeFromString<StoredMapping>(it) }.getOrNull() }
                ?.toDomain() ?: ActionMapping(gestureIdByState = DEFAULT_STATE_GESTURES)
        }.distinctUntilChanged()

    val manualPresets: Flow<List<ManualPreset>> =
        combine(handVersionStore.activeDof, dataStore.data) { dof, prefs ->
            prefs[presetsKey(dof.id)]
                ?.let { runCatching { json.decodeFromString<List<ManualPreset>>(it) }.getOrNull() }
                ?: ManualPreset.DEFAULT_PRESETS
        }.distinctUntilChanged()

    suspend fun saveConfig(config: HandConfig) {
        val key = configKey(dofId())
        dataStore.edit { it[key] = json.encodeToString(config) }
    }

    suspend fun saveMapping(mapping: ActionMapping) {
        val key = mappingKey(dofId())
        dataStore.edit { it[key] = json.encodeToString(StoredMapping.fromDomain(mapping)) }
    }

    suspend fun saveManualPresets(presets: List<ManualPreset>) {
        val key = presetsKey(dofId())
        dataStore.edit { it[key] = json.encodeToString(presets) }
    }

    suspend fun resetManualPresets() {
        val key = presetsKey(dofId())
        dataStore.edit { it.remove(key) }
    }

    /** 특정 자유도의 설정 키 제거 */
    suspend fun deleteUserData(dofId: String) {
        dataStore.edit {
            it.remove(configKey(dofId))
            it.remove(mappingKey(dofId))
            it.remove(presetsKey(dofId))
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
}
