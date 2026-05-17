package com.proxu.app.handler

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow

object SettingsChangeManager {
    private val _restartService = MutableStateFlow(false)
    private val _setupGroupTab = MutableStateFlow(false)
    val setupGroupTabSignal = MutableLiveData<Boolean>()
    
    fun makeRestartService() {
        _restartService.value = true
    }

    fun consumeRestartService(): Boolean {
        val v = _restartService.value
        _restartService.value = false
        return v
    }

    fun makeSetupGroupTab() {
        _setupGroupTab.value = true
        setupGroupTabSignal.postValue(true)
    }

    fun consumeSetupGroupTab(): Boolean {
        val v = _setupGroupTab.value
        _setupGroupTab.value = false
        return v
    }
}
