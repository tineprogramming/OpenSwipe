package com.openswipe.config

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.openswipe.OpenSwipeApp
import com.openswipe.gesture.GestureConfig
import com.openswipe.rule.GestureRuleGraph
import com.openswipe.rule.RuleSerializer.toGestureRuleGraph
import com.openswipe.rule.RuleSerializer.toJsonObject
import com.openswipe.settingsDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File

/**
 * Import/export of the whole app configuration (rules + settings) as a JSON file,
 * so a device can be set up with `adb push` instead of tapping through the UI.
 *
 * File format:
 * ```
 * {
 *   "version": 1,
 *   "settings": {
 *     "edgeTriggerWidthDp": 20, "bottomTriggerHeightDp": 40,
 *     "disableBottomOnIme": true, "disableSidesOnIme": false
 *   },
 *   "rules": [ ... same objects as the rule serializer ... ]
 * }
 * ```
 * Both "settings" and "rules" are optional; missing keys keep their current value.
 *
 * Files looked up (first existing one wins):
 *  1. /sdcard/Android/data/com.openswipe/files/openswipe.json  (no permission needed)
 *  2. /sdcard/openswipe.json, /sdcard/Download/openswipe.json  (needs "All files access":
 *     `adb shell appops set com.openswipe MANAGE_EXTERNAL_STORAGE allow`)
 *  3. /data/local/tmp/openswipe.json (best effort)
 *
 * A file is imported automatically at startup when it is new or has changed since the last
 * import, so UI edits are not overwritten on every launch.
 */
object ConfigManager {

    private const val TAG = "OpenSwipeConfig"
    const val FILE_NAME = "openswipe.json"

    private val KEY_IMPORTED_SIGNATURE = stringPreferencesKey("config_imported_signature")
    private val KEY_IMPORTED_AT = longPreferencesKey("config_imported_at")

    fun primaryFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)

    fun candidateFiles(context: Context): List<File> {
        @Suppress("DEPRECATION")
        val sdcard = Environment.getExternalStorageDirectory()
        return listOf(
            primaryFile(context),
            File(sdcard, FILE_NAME),
            File(sdcard, "Download/$FILE_NAME"),
            File("/data/local/tmp/$FILE_NAME"),
        )
    }

    private fun findReadableFile(context: Context): File? =
        candidateFiles(context).firstOrNull { f -> runCatching { f.isFile && f.canRead() }.getOrDefault(false) }

    private fun signatureOf(file: File): String = "${file.absolutePath}|${file.length()}|${file.lastModified()}"

    /** Imports the config file at startup if it is new or changed since the last import. */
    suspend fun importIfChanged(app: OpenSwipeApp): Boolean {
        val file = findReadableFile(app) ?: return false
        val signature = signatureOf(file)
        val last = app.settingsDataStore.data.first()[KEY_IMPORTED_SIGNATURE]
        if (signature == last) return false
        return importFile(app, file)
    }

    /** Imports from the first config file found. Returns a human-readable result. */
    suspend fun importFromDefaultLocations(app: OpenSwipeApp): Result<String> {
        val file = findReadableFile(app)
            ?: return Result.failure(IllegalStateException("No $FILE_NAME found. Push it to ${primaryFile(app).absolutePath}"))
        return if (importFile(app, file)) Result.success("Imported ${file.absolutePath}")
        else Result.failure(IllegalArgumentException("Invalid config in ${file.absolutePath}"))
    }

    suspend fun importFile(app: OpenSwipeApp, file: File): Boolean {
        val text = runCatching { file.readText() }.getOrElse {
            Log.w(TAG, "Cannot read $file", it); return false
        }
        val ok = importJson(app, text)
        if (ok) {
            app.settingsDataStore.edit { prefs ->
                prefs[KEY_IMPORTED_SIGNATURE] = signatureOf(file)
                prefs[KEY_IMPORTED_AT] = System.currentTimeMillis()
            }
            Log.i(TAG, "Imported config from $file")
        }
        return ok
    }

    /** Applies a JSON config string. Returns false (and changes nothing) if it cannot be parsed. */
    suspend fun importJson(app: OpenSwipeApp, text: String): Boolean {
        val root = runCatching { JSONObject(text) }.getOrElse {
            Log.w(TAG, "Invalid JSON", it); return false
        }
        val graph: GestureRuleGraph? = if (root.has("rules")) {
            runCatching { text.toGestureRuleGraph() }.getOrElse {
                Log.w(TAG, "Invalid rules", it); return false
            }
        } else null
        val settings = root.optJSONObject("settings")

        if (settings != null) {
            app.settingsDataStore.edit { prefs -> applySettings(prefs, settings) }
        }
        if (graph != null) app.applyRules(graph)
        return true
    }

    private fun applySettings(prefs: MutablePreferences, s: JSONObject) {
        if (s.has("edgeTriggerWidthDp")) prefs[GestureConfig.KEY_EDGE_TRIGGER_WIDTH] = s.getDouble("edgeTriggerWidthDp").toFloat()
        if (s.has("bottomTriggerHeightDp")) prefs[GestureConfig.KEY_BOTTOM_TRIGGER_HEIGHT] = s.getDouble("bottomTriggerHeightDp").toFloat()
        if (s.has("disableBottomOnIme")) prefs[GestureConfig.KEY_DISABLE_BOTTOM_ON_IME] = s.getBoolean("disableBottomOnIme")
        if (s.has("disableSidesOnIme")) prefs[GestureConfig.KEY_DISABLE_SIDES_ON_IME] = s.getBoolean("disableSidesOnIme")
    }

    /** Current configuration (saved rules + settings) as JSON. */
    suspend fun exportJson(app: OpenSwipeApp): String {
        val config = app.gestureConfigFlow.value
        val graph = app.loadSavedRules() ?: app.currentRuleGraph()
        val root = graph.toJsonObject()
        root.put("settings", JSONObject().apply {
            put("edgeTriggerWidthDp", config.edgeTriggerWidthDp.toDouble())
            put("bottomTriggerHeightDp", config.bottomTriggerHeightDp.toDouble())
            put("disableBottomOnIme", config.disableBottomOnIme)
            put("disableSidesOnIme", config.disableSidesOnIme)
        })
        return root.toString(2)
    }

    /** Writes the current configuration to [primaryFile]. */
    suspend fun exportToFile(app: OpenSwipeApp): Result<File> = runCatching {
        val file = primaryFile(app)
        file.parentFile?.mkdirs()
        file.writeText(exportJson(app))
        // Remember this file as already imported, so exporting doesn't trigger a re-import.
        app.settingsDataStore.edit { prefs -> prefs[KEY_IMPORTED_SIGNATURE] = signatureOf(file) }
        file
    }
}
