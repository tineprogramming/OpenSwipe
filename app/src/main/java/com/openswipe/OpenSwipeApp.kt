package com.openswipe

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openswipe.config.ConfigManager
import com.openswipe.gesture.ForegroundBlocklist
import com.openswipe.gesture.GestureConfig
import com.openswipe.rule.CompiledRuleSet
import com.openswipe.rule.GestureRuleGraph
import com.openswipe.rule.Presets
import com.openswipe.rule.RuleSerializer.toGestureRuleGraph
import com.openswipe.rule.RuleSerializer.toJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class OpenSwipeApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var gestureConfigFlow: StateFlow<GestureConfig>
        private set

    private val _compiledRuleSet = MutableStateFlow(CompiledRuleSet.EMPTY)
    val compiledRuleSet: StateFlow<CompiledRuleSet> = _compiledRuleSet.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this

        gestureConfigFlow = settingsDataStore.data
            .map { prefs ->
                GestureConfig(
                    leftEnabled = prefs[GestureConfig.KEY_LEFT_ENABLED] ?: true,
                    rightEnabled = prefs[GestureConfig.KEY_RIGHT_ENABLED] ?: true,
                    bottomEnabled = prefs[GestureConfig.KEY_BOTTOM_ENABLED] ?: true,
                    edgeTriggerWidthDp = prefs[GestureConfig.KEY_EDGE_TRIGGER_WIDTH] ?: 20f,
                    bottomTriggerHeightDp = prefs[GestureConfig.KEY_BOTTOM_TRIGGER_HEIGHT] ?: BuildConfig.DEFAULT_BOTTOM_HEIGHT_DP,
                    disableBottomOnIme = prefs[GestureConfig.KEY_DISABLE_BOTTOM_ON_IME] ?: true,
                    disableSidesOnIme = prefs[GestureConfig.KEY_DISABLE_SIDES_ON_IME] ?: false,
                    blockedActivities = prefs[GestureConfig.KEY_BLOCKED_ACTIVITIES]
                        ?.let { ForegroundBlocklist.parseList(it) }
                        ?: GestureConfig.DEFAULT_BLOCKED_ACTIVITIES,
                )
            }
            .stateIn(appScope, SharingStarted.Eagerly, GestureConfig())

        // Load rules from DataStore on startup
        appScope.launch(Dispatchers.IO) {
            // Import a pushed config file (adb push openswipe.json …) if it is new/changed.
            // If it contains rules, applyRules() inside the import updates the rule set.
            runCatching { ConfigManager.importIfChanged(this@OpenSwipeApp) }
            if (_compiledRuleSet.value !== CompiledRuleSet.EMPTY) return@launch
            val prefs = settingsDataStore.data.first()
            val json = prefs[KEY_RULES_JSON]
            val graph = if (json != null) {
                runCatching { json.toGestureRuleGraph() }.getOrElse { Presets.DEFAULT }
            } else {
                Presets.DEFAULT
            }
            setGraph(graph)
        }
    }

    /** The rule graph behind [compiledRuleSet]. */
    @Volatile
    private var ruleGraph: GestureRuleGraph = Presets.DEFAULT

    fun currentRuleGraph(): GestureRuleGraph = ruleGraph

    private fun setGraph(graph: GestureRuleGraph) {
        ruleGraph = graph
        _compiledRuleSet.value = graph.compile()
    }

    suspend fun applyRules(graph: GestureRuleGraph) {
        val json = graph.toJson()
        settingsDataStore.edit { prefs ->
            prefs[KEY_RULES_JSON] = json
        }
        setGraph(graph)
    }

    suspend fun updateDisableBottomOnIme(value: Boolean) {
        settingsDataStore.edit { prefs -> prefs[GestureConfig.KEY_DISABLE_BOTTOM_ON_IME] = value }
    }

    suspend fun updateBlockedActivities(entries: List<String>) {
        settingsDataStore.edit { prefs ->
            prefs[GestureConfig.KEY_BLOCKED_ACTIVITIES] = entries.joinToString("\n")
        }
    }

    suspend fun resetBlockedActivities() {
        settingsDataStore.edit { prefs -> prefs.remove(GestureConfig.KEY_BLOCKED_ACTIVITIES) }
    }

    suspend fun updateDisableSidesOnIme(value: Boolean) {
        settingsDataStore.edit { prefs -> prefs[GestureConfig.KEY_DISABLE_SIDES_ON_IME] = value }
    }

    suspend fun updateEdgeTriggerWidth(dp: Float) {
        settingsDataStore.edit { prefs ->
            prefs[GestureConfig.KEY_EDGE_TRIGGER_WIDTH] = dp
        }
    }

    suspend fun updateBottomTriggerHeight(dp: Float) {
        settingsDataStore.edit { prefs ->
            prefs[GestureConfig.KEY_BOTTOM_TRIGGER_HEIGHT] = dp
        }
    }

    suspend fun loadSavedRules(): GestureRuleGraph? {
        val prefs = settingsDataStore.data.first()
        val json = prefs[KEY_RULES_JSON] ?: return null
        return runCatching { json.toGestureRuleGraph() }.getOrNull()
    }

    /**
     * 同步加载规则 — 在 onServiceConnected 中调用，确保 Service 启动时规则立即可用。
     * 学习 STB 策略：不依赖异步 flow，在 Service 绑定时同步完成初始化。
     */
    fun ensureRulesLoadedSync() {
        if (_compiledRuleSet.value !== CompiledRuleSet.EMPTY) return
        try {
            // DataStore 的底层文件也可以通过 SharedPreferences 的方式同步读取
            // 但更可靠的方式是用 runBlocking 读取 DataStore
            kotlinx.coroutines.runBlocking {
                val dataStorePrefs = settingsDataStore.data.first()
                val json = dataStorePrefs[KEY_RULES_JSON]
                val graph = if (json != null) {
                    runCatching { json.toGestureRuleGraph() }.getOrElse { Presets.DEFAULT }
                } else {
                    Presets.DEFAULT
                }
                setGraph(graph)
            }
        } catch (e: Exception) {
            // 兜底：使用默认预设
            setGraph(Presets.DEFAULT)
        }
    }

    companion object {
        private val KEY_RULES_JSON = stringPreferencesKey("gesture_rules_json")
        private lateinit var instance: OpenSwipeApp
        fun getInstance(): OpenSwipeApp = instance
    }
}
