package com.mandro.mark7.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mandro.mark7.domain.model.hand.HandConfig
import com.mandro.mark7.domain.model.hand.ManualPreset
import com.mandro.mark7.domain.model.hand.CmdPresetCatalogs
import com.mandro.mark7.domain.model.action.ActionMapping
import com.mandro.mark7.domain.model.action.GestureCatalogs
import com.mandro.mark7.domain.model.hand.HandDof
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
     * 아직 한 번도 저장한 적 없으면 그 자유도 손 모양 목록의 기본값으로 S1..S8 을 채운 초기값.
     * 매핑에는 자유도가 함께 실려, 액션 ID·Pair 규칙을 그 자유도 목록에서 계산한다.
     */
    val actionMapping: Flow<ActionMapping> =
        combine(handVersionStore.activeDof, dataStore.data) { dof, prefs ->
            prefs[mappingKey(dof.id)]
                ?.let { runCatching { json.decodeFromString<StoredMapping>(it) }.getOrNull() }
                ?.toDomain(dof)
                ?: ActionMapping(gestureIdByState = GestureCatalogs.forDof(dof).defaultStateGestures, dof = dof)
        }.distinctUntilChanged()

    fun manualPresetsForDof(dofFlow: Flow<HandDof>): Flow<List<ManualPreset>> =
        combine(dofFlow, dataStore.data) { dof, prefs ->
            val defaults = CmdPresetCatalogs.forDof(dof)
            prefs[presetsKey(dof.id)]
                ?.let { runCatching { json.decodeFromString<List<ManualPreset>>(it) }.getOrNull() }
                ?.let { CmdPresetCatalogs.normalizeSaved(it, dof) }
                ?: defaults
        }.distinctUntilChanged()

    val manualPresets: Flow<List<ManualPreset>> = manualPresetsForDof(handVersionStore.activeDof)

    suspend fun saveConfig(config: HandConfig) {
        val key = configKey(dofId())
        dataStore.edit { it[key] = json.encodeToString(config) }
    }

    suspend fun saveMapping(mapping: ActionMapping) {
        val key = mappingKey(dofId())
        dataStore.edit { it[key] = json.encodeToString(StoredMapping.fromDomain(mapping)) }
    }

    suspend fun saveManualPresets(presets: List<ManualPreset>, dof: HandDof) {
        val key = presetsKey(dof.id)
        dataStore.edit { it[key] = json.encodeToString(presets) }
    }

    suspend fun resetManualPresets(dof: HandDof) {
        val key = presetsKey(dof.id)
        dataStore.edit { it.remove(key) }
    }

    @Serializable
    private data class StoredMapping(
        val schemaVersion: Int = 1,
        val byState: Map<String, String> = emptyMap(),
        val gradual: Boolean = false,
    ) {
        fun toDomain(dof: HandDof) = ActionMapping(
            gestureIdByState = migrateStoredStateIds(byState, schemaVersion),
            gradualGraspEnabled = gradual,
            dof = dof,
        )

        companion object {
            fun fromDomain(m: ActionMapping) = StoredMapping(
                schemaVersion = CURRENT_MAPPING_SCHEMA_VERSION,
                byState = m.gestureIdByState.mapKeys { it.key.toString() },
                gradual = m.gradualGraspEnabled,
            )
        }
    }

    private companion object {
        const val CURRENT_MAPPING_SCHEMA_VERSION = 2
    }
}

/** 기존 S1(IDLE)+S2..S9 저장값을 S0(IDLE)+S1..S8 체계로 한 칸 이동한다. */
internal fun migrateStoredStateIds(
    byState: Map<String, String>,
    schemaVersion: Int,
): Map<Int, String> {
    val offset = if (schemaVersion < 2) -1 else 0
    return byState.mapNotNull { (rawStateId, gestureId) ->
        rawStateId.toIntOrNull()
            ?.plus(offset)
            ?.takeIf { it in 1..8 }
            ?.let { it to gestureId }
    }.toMap()
}
