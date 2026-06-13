package de.lolo.lolotrans

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BubbleRuntimeState {
    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible

    fun setVisible(visible: Boolean) {
        _isVisible.value = visible
    }
}

