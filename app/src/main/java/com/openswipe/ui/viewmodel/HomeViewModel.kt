package com.openswipe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openswipe.OpenSwipeApp
import com.openswipe.gesture.ForegroundBlocklist
import com.openswipe.gesture.GestureConfig
import kotlinx.coroutines.flow.StateFlow
import com.openswipe.config.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenSwipeApp

    val configState: StateFlow<GestureConfig> = app.gestureConfigFlow

    fun setEdgeTriggerWidth(dp: Float) {
        viewModelScope.launch {
            app.updateEdgeTriggerWidth(dp)
        }
    }

    fun setDisableBottomOnIme(value: Boolean) {
        viewModelScope.launch { app.updateDisableBottomOnIme(value) }
    }

    fun setDisableSidesOnIme(value: Boolean) {
        viewModelScope.launch { app.updateDisableSidesOnIme(value) }
    }

    /** Saves the blocklist from a text field (one entry per line). */
    fun setBlockedActivities(text: String) {
        viewModelScope.launch {
            app.updateBlockedActivities(ForegroundBlocklist.parseList(text))
        }
    }

    fun resetBlockedActivities() {
        viewModelScope.launch { app.resetBlockedActivities() }
    }

    /** Path of the config file used by Import/Export. */
    val configFilePath: String get() = ConfigManager.primaryFile(app).absolutePath

    fun importConfig(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                ConfigManager.importFromDefaultLocations(app).fold({ it }, { it.message ?: "Import failed" })
            }
            onResult(msg)
        }
    }

    fun exportConfig(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                ConfigManager.exportToFile(app).fold({ "Exported to ${it.absolutePath}" }, { "Export failed: ${it.message}" })
            }
            onResult(msg)
        }
    }

    fun setBottomTriggerHeight(dp: Float) {
        viewModelScope.launch {
            app.updateBottomTriggerHeight(dp)
        }
    }
}
