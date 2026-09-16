package com.mandro.mark7.core.ble

import com.mandro.mark7.domain.model.hand.HandDof
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks the DOF reported by the currently connected hand's STATUS frame. */
internal class ConnectedDofDetector {
    private val _dof = MutableStateFlow<HandDof?>(null)
    val dof: StateFlow<HandDof?> = _dof.asStateFlow()

    fun update(dof: Int) {
        val detected = HandDof.entries.firstOrNull { it.dof == dof } ?: return
        _dof.value = detected
    }

    fun reset() {
        _dof.value = null
    }
}
