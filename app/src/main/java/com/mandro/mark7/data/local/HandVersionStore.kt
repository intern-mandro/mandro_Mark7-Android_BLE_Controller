package com.mandro.mark7.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mandro.mark7.domain.model.HandDof
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.handVersionDataStore: DataStore<Preferences> by preferencesDataStore(name = "mark7_hand_version")

/**
 * 사용자가 선택한 로봇 의수 버전(자유도: 5 DOF, 6 DOF, 7 DOF) 영속 저장소.
 * 기본값은 Mark7 표준인 [HandDof.DOF_6].
 */
@Singleton
class HandVersionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.handVersionDataStore
    private val dofKey = stringPreferencesKey("selected_dof_id")

    /** 현재 선택된 의수 자유도 Flow */
    val activeDof: Flow<HandDof> = ds.data.map { prefs ->
        HandDof.fromId(prefs[dofKey])
    }

    /** 1회성 현재 자유도 조회 */
    suspend fun activeDofOnce(): HandDof = activeDof.first()

    /** 의수 자유도 버전 변경 및 저장 */
    suspend fun setActiveDof(dof: HandDof) {
        ds.edit { prefs ->
            prefs[dofKey] = dof.id
        }
    }
}
