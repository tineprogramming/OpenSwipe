package com.openswipe.gesture

import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.openswipe.action.ActionDispatcher
import com.openswipe.action.ActionType
import com.openswipe.gesture.model.GestureResult
import com.openswipe.model.ActionNode
import com.openswipe.model.GestureType
import com.openswipe.model.TriggerMode
import com.openswipe.overlay.Edge
import com.openswipe.overlay.EdgeSensorView
import com.openswipe.overlay.OverlayManager
import com.openswipe.overlay.OverlayWindowFactory
import com.openswipe.rule.CompiledRuleSet
import com.openswipe.service.GestureAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GestureEngine(
    private val configFlow: StateFlow<GestureConfig>,
    private val actionDispatcher: ActionDispatcher,
    private val overlayManager: OverlayManager,
    private val compiledRuleSetFlow: StateFlow<CompiledRuleSet>,
) : EdgeSensorView.OnEdgeTouchListener {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val detectors = mutableMapOf<Edge, EdgeGestureDetector>()
    private var currentConfig: GestureConfig = configFlow.value
    private var started = false
    private var lastRuleSet: CompiledRuleSet? = null

    /** Screen Y (px) of the top of the soft keyboard window, or null when it is hidden. */
    private var imeTopPx: Int? = null

    /**
     * Called by the accessibility service when the IME window appears, moves or hides
     * ([topPx] = its top edge in screen pixels, null = hidden).
     *  - BOTTOM sensor is removed while it is visible (disableBottomOnIme).
     *  - LEFT/RIGHT sensors are shortened to end at the keyboard's top, so Back still works
     *    above it and the outer keys (q, p, a, l, z, m) are not covered.
     *    (Tap pass-through can't reach the IME window, so overlapping it isn't an option.)
     */
    fun setImeTop(topPx: Int?) {
        if (imeTopPx == topPx) return
        imeTopPx = topPx
        if (!started) return
        syncEdges(compiledRuleSetFlow.value, rebuildSides = false, rebuildBottom = false)
        resizeSideSensors()
    }

    /** Height for a LEFT/RIGHT sensor: full screen, or down to the keyboard's top. */
    private fun sideSensorHeight(screenHeight: Int): Int =
        imeTopPx?.coerceIn(0, screenHeight) ?: screenHeight

    private fun resizeSideSensors() {
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val h = sideSensorHeight(screenHeight)
        for (edge in listOf(Edge.LEFT, Edge.RIGHT)) {
            overlayManager.setHeight("sensor_${edge.name.lowercase()}", h)
        }
    }

    /** Whether the foreground activity is on the blocklist (all edges paused). */
    private var foregroundBlocked = false
    private var foregroundPkg: String? = null
    private var foregroundCls: String? = null
    private var blocklistSource: List<String>? = null
    private var blocklistCache: ForegroundBlocklist = ForegroundBlocklist.EMPTY

    /** Blocklist for the current config (rebuilt only when the list changes). */
    val blocklist: ForegroundBlocklist
        get() {
            val src = currentConfig.blockedActivities
            if (src != blocklistSource) {
                blocklistSource = src
                blocklistCache = ForegroundBlocklist(src)
            }
            return blocklistCache
        }

    /** Called by the service when a new *activity* comes to the front. */
    fun onForegroundActivityChanged(packageName: String, className: String) {
        foregroundPkg = packageName
        foregroundCls = className
        reevaluateForegroundBlock()
    }

    /**
     * Safety net: the service saw a different app's window become active without an activity
     * event we could classify. Only unblocks, never blocks.
     */
    fun onActiveAppChanged(packageName: String) {
        if (foregroundBlocked && packageName != foregroundPkg) {
            foregroundPkg = packageName
            foregroundCls = null
            reevaluateForegroundBlock()
        }
    }

    private fun reevaluateForegroundBlock() {
        val pkg = foregroundPkg
        val blocked = pkg != null && blocklist.matches(pkg, foregroundCls)
        if (blocked == foregroundBlocked) return
        foregroundBlocked = blocked
        if (started) syncEdges(compiledRuleSetFlow.value, rebuildSides = false, rebuildBottom = false)
    }

    private fun isSuppressed(edge: Edge): Boolean {
        if (foregroundBlocked) return true
        val imeTop = imeTopPx ?: return false
        return when (edge) {
            Edge.BOTTOM -> currentConfig.disableBottomOnIme
            // Keyboard covering (almost) the whole screen leaves no room above it.
            Edge.LEFT, Edge.RIGHT -> currentConfig.disableSidesOnIme || imeTop < MIN_SIDE_SENSOR_PX
        }
    }

    private fun shouldShow(edge: Edge, ruleSet: CompiledRuleSet): Boolean =
        ruleSet.hasRulesFor(edge) && !isSuppressed(edge)

    fun stop() {
        overlayManager.removeAll()
        detectors.clear()
        edgeLengths.clear()
        scope.cancel()
        started = false
        lastRuleSet = null
    }

    fun start() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            combine(configFlow, compiledRuleSetFlow) { config, ruleSet -> config to ruleSet }
                .collect { (newConfig, ruleSet) ->
                    val old = currentConfig
                    currentConfig = newConfig
                    val ruleSetChanged = ruleSet !== lastRuleSet
                    lastRuleSet = ruleSet
                    if (!started || ruleSetChanged) {
                        // New rules may change per-edge trigger modes, which detectors capture
                        // at creation time — rebuild everything.
                        started = true
                        rebuildOverlays(ruleSet)
                    } else {
                        applyConfigDiff(old, newConfig, ruleSet)
                    }
                }
        }
    }

    fun onForegroundAppChanged(packageName: String) {
        // Phase 2: 黑名单/白名单检测
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        rebuildOverlays(compiledRuleSetFlow.value)
    }

    private fun applyConfigDiff(old: GestureConfig, new: GestureConfig, ruleSet: CompiledRuleSet) {
        if (old.blockedActivities != new.blockedActivities) {
            val pkg = foregroundPkg
            foregroundBlocked = pkg != null && blocklist.matches(pkg, foregroundCls)
        }
        // If edge width changed, rebuild all side overlays
        val sideNeedsRebuild = old.edgeTriggerWidthDp != new.edgeTriggerWidthDp
        // If bottom height changed, rebuild bottom overlay
        val bottomNeedsRebuild = old.bottomTriggerHeightDp != new.bottomTriggerHeightDp
        syncEdges(ruleSet, sideNeedsRebuild, bottomNeedsRebuild)
    }

    /** Add/remove/rebuild edge sensors so they match the rule set and IME state. */
    private fun syncEdges(ruleSet: CompiledRuleSet, rebuildSides: Boolean, rebuildBottom: Boolean) {
        for (edge in Edge.entries) {
            val show = shouldShow(edge, ruleSet)
            val hadOverlay = detectors.containsKey(edge)
            val needsRebuild = when (edge) {
                Edge.LEFT, Edge.RIGHT -> rebuildSides
                Edge.BOTTOM -> rebuildBottom
            }
            if (hadOverlay && !show) {
                removeEdge(edge)
            } else if (!hadOverlay && show) {
                addEdgeOverlay(edge)
            } else if (show && needsRebuild) {
                removeEdge(edge)
                addEdgeOverlay(edge)
            }
        }
    }

    private fun rebuildOverlays(ruleSet: CompiledRuleSet) {
        overlayManager.removeAll()
        detectors.clear()
        edgeLengths.clear()
        for (edge in Edge.entries) {
            if (shouldShow(edge, ruleSet)) {
                addEdgeOverlay(edge)
            }
        }
    }

    private fun removeEdge(edge: Edge) {
        val tag = "sensor_${edge.name.lowercase()}"
        overlayManager.removeWindow(tag)
        detectors.remove(edge)
        edgeLengths.remove(edge)
    }

    private fun addEdgeOverlay(edge: Edge) {
        val resources = Resources.getSystem()
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        val edgeWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, currentConfig.edgeTriggerWidthDp, displayMetrics
        ).toInt()

        val bottomHeightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, currentConfig.bottomTriggerHeightDp, displayMetrics
        ).toInt()

        val tag = "sensor_${edge.name.lowercase()}"

        when (edge) {
            Edge.LEFT -> {
                val detector = createDetector(Edge.LEFT, screenHeight.toFloat())
                val window = OverlayWindowFactory.createEdgeSensor(
                    overlayManager.context, Edge.LEFT, edgeWidthPx, sideSensorHeight(screenHeight),
                    onTouchListener = this
                )
                detectors[Edge.LEFT] = detector
                edgeLengths[Edge.LEFT] = screenHeight.toFloat()
                overlayManager.addWindow(tag, window)
            }
            Edge.RIGHT -> {
                val detector = createDetector(Edge.RIGHT, screenHeight.toFloat())
                val window = OverlayWindowFactory.createEdgeSensor(
                    overlayManager.context, Edge.RIGHT, edgeWidthPx, sideSensorHeight(screenHeight),
                    onTouchListener = this
                )
                detectors[Edge.RIGHT] = detector
                edgeLengths[Edge.RIGHT] = screenHeight.toFloat()
                overlayManager.addWindow(tag, window)
            }
            Edge.BOTTOM -> {
                val detector = createDetector(Edge.BOTTOM, screenWidth.toFloat())
                val window = OverlayWindowFactory.createEdgeSensor(
                    overlayManager.context, Edge.BOTTOM, screenWidth, bottomHeightPx,
                    onTouchListener = this
                )
                detectors[Edge.BOTTOM] = detector
                edgeLengths[Edge.BOTTOM] = screenWidth.toFloat()
                overlayManager.addWindow(tag, window)
            }
        }
    }

    private fun createDetector(edge: Edge, sensorLength: Float): EdgeGestureDetector {
        val configCopy = currentConfig.copy(sensorLength = sensorLength)
        val touchSlop = ViewConfiguration.get(overlayManager.context).scaledTouchSlop
        val edgeTriggerMode = compiledRuleSetFlow.value.triggerModeFor(edge)
        return EdgeGestureDetector(
            edge = edge,
            config = configCopy,
            scaledTouchSlop = touchSlop,
            onGestureResult = { result -> handleGestureResult(result) },
            triggerMode = edgeTriggerMode,
            onReplayTap = if (edgeTriggerMode == TriggerMode.SWIPE) { x, y, duration ->
                replayTap(edge, x, y, duration)
            } else null,
        )
    }

    /**
     * Pass a non-swipe touch through to the app under the sensor: make the sensor window
     * untouchable, wait for the window update to land, inject the tap, then restore it.
     * Without the untouchable step the injected tap would hit our own sensor again.
     */
    private fun replayTap(edge: Edge, x: Float, y: Float, durationMs: Long) {
        val service = GestureAccessibilityService.getInstance() ?: return
        val tag = "sensor_${edge.name.lowercase()}"
        overlayManager.setTouchable(tag, false)
        scope.launch {
            delay(REPLAY_WINDOW_UPDATE_DELAY_MS)
            service.dispatchTap(x, y, durationMs.coerceAtLeast(MIN_TAP_DURATION_MS)) {
                scope.launch {
                    delay(REPLAY_WINDOW_UPDATE_DELAY_MS)
                    overlayManager.setTouchable(tag, true)
                }
            }
        }
    }

    /** Map of edge → current sensor length in pixels, updated when overlays are created. */
    private val edgeLengths = mutableMapOf<Edge, Float>()

    private fun handleGestureResult(result: GestureResult) {
        val actionNode = matchViaRuleSet(result) ?: return
        scope.launch { actionDispatcher.dispatch(actionNode) }
    }

    private fun matchViaRuleSet(result: GestureResult): ActionNode? {
        val (edge, gestureType, touchPx) = when (result) {
            is GestureResult.EdgeSwipe -> Triple(
                result.edge,
                GestureType.SWIPE,
                result.touchAlongEdgePx
            )
            is GestureResult.VerticalSwipe -> Triple(
                result.edge,
                GestureType.SWIPE,
                result.touchAlongEdgePx
            )
            is GestureResult.Tap -> Triple(
                result.edge,
                GestureType.SWIPE,
                result.touchAlongEdgePx
            )
        }
        val compiledRuleSet = compiledRuleSetFlow.value
        val edgeLength = edgeLengths[edge] ?: return null
        if (edgeLength <= 0f) return null
        val sectionRatio = (touchPx / edgeLength).coerceIn(0f, 1f)
        return compiledRuleSet.match(edge, gestureType, sectionRatio)
    }

    override fun onEdgeTouch(edge: Edge, event: MotionEvent): Boolean {
        return detectors[edge]?.onTouchEvent(event) ?: false
    }

    private companion object {
        /** Time for WindowManager to apply the FLAG_NOT_TOUCHABLE change before injecting. */
        const val REPLAY_WINDOW_UPDATE_DELAY_MS = 40L
        const val MIN_TAP_DURATION_MS = 40L
        /** Below this, a shortened side sensor is pointless; remove it instead. */
        const val MIN_SIDE_SENSOR_PX = 24
    }
}
