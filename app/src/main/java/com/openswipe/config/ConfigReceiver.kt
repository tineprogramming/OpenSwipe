package com.openswipe.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openswipe.OpenSwipeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * adb entry point for configuration. Protected by android.permission.DUMP, so only the
 * shell (adb) and the system can send it — normal apps cannot.
 *
 *   adb shell am broadcast -a com.openswipe.action.IMPORT_CONFIG -n com.openswipe/.config.ConfigReceiver
 *   adb shell am broadcast -a com.openswipe.action.IMPORT_CONFIG -n com.openswipe/.config.ConfigReceiver --es path /sdcard/openswipe.json
 *   adb shell am broadcast -a com.openswipe.action.EXPORT_CONFIG -n com.openswipe/.config.ConfigReceiver
 */
class ConfigReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_IMPORT = "com.openswipe.action.IMPORT_CONFIG"
        const val ACTION_EXPORT = "com.openswipe.action.EXPORT_CONFIG"
        private const val TAG = "OpenSwipeConfig"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as OpenSwipeApp
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_IMPORT -> {
                        val json = intent.getStringExtra("json")
                        val path = intent.getStringExtra("path")
                        val msg = when {
                            json != null ->
                                if (ConfigManager.importJson(app, json)) "Imported inline JSON" else "Invalid inline JSON"
                            path != null ->
                                if (ConfigManager.importFile(app, File(path))) "Imported $path" else "Failed to import $path"
                            else -> ConfigManager.importFromDefaultLocations(app)
                                .fold({ it }, { it.message ?: "Import failed" })
                        }
                        Log.i(TAG, msg)
                        pending.resultData = msg
                    }
                    ACTION_EXPORT -> {
                        val msg = ConfigManager.exportToFile(app)
                            .fold({ "Exported to ${it.absolutePath}" }, { "Export failed: ${it.message}" })
                        Log.i(TAG, msg)
                        pending.resultData = msg
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
