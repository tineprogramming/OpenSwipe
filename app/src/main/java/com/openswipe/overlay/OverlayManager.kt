package com.openswipe.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams

class OverlayManager(
    val context: Context,
    private val windowManager: WindowManager,
) {
    private val windows = mutableMapOf<String, OverlayWindow>()

    fun addWindow(tag: String, window: OverlayWindow) {
        if (windows.containsKey(tag)) return
        try {
            windowManager.addView(window.view, window.params)
            windows[tag] = window
        } catch (e: Exception) {
            // 窗口添加失败（服务断开、权限不足等）
        }
    }

    fun removeWindow(tag: String) {
        windows.remove(tag)?.let { window ->
            try {
                if (window.view.windowToken != null) {
                    windowManager.removeView(window.view)
                }
            } catch (_: Exception) {}
        }
    }

    fun updateWindow(tag: String) {
        windows[tag]?.let { window ->
            try {
                windowManager.updateViewLayout(window.view, window.params)
            } catch (_: Exception) {}
        }
    }

    /** Makes a sensor window ignore touches (so injected taps reach the app below) or not. */
    fun setTouchable(tag: String, touchable: Boolean) {
        val window = windows[tag] ?: return
        val flag = LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (touchable) window.params.flags and flag.inv() else window.params.flags or flag
        if (newFlags == window.params.flags) return
        window.params.flags = newFlags
        updateWindow(tag)
    }

    /** Changes a sensor window's height in place (no-op if unchanged or missing). */
    fun setHeight(tag: String, heightPx: Int) {
        val window = windows[tag] ?: return
        if (window.params.height == heightPx) return
        window.params.height = heightPx
        updateWindow(tag)
    }

    fun removeAll() {
        windows.keys.toList().forEach { removeWindow(it) }
    }
}

data class OverlayWindow(
    val view: View,
    val params: LayoutParams,
)

enum class Edge { LEFT, RIGHT, BOTTOM }
