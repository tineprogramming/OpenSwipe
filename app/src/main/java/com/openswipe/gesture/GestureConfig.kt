package com.openswipe.gesture

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey

data class GestureConfig(
    // 触发区域
    val edgeTriggerWidthDp: Float = 20f,
    val bottomTriggerHeightDp: Float = com.openswipe.BuildConfig.DEFAULT_BOTTOM_HEIGHT_DP,
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true,
    val bottomEnabled: Boolean = true,

    // 灵敏度
    val dampingFactor: Float = 2.0f,
    val peakThreshold: Float = 200f,
    val minSwipeThresholdPx: Float = 30f,

    // 分区
    val sectionCount: Int = 1,
    val sensorLength: Float = 0f,

    // 反馈
    val hapticEnabled: Boolean = true,

    // 长按
    val holdTimeMs: Long = 500L,

    // Keyboard (IME) avoidance: remove edge sensors while the soft keyboard is showing
    val disableBottomOnIme: Boolean = true,
    val disableSidesOnIme: Boolean = false,
) {
    companion object {
        val KEY_LEFT_ENABLED = booleanPreferencesKey("edge_left_enabled")
        val KEY_RIGHT_ENABLED = booleanPreferencesKey("edge_right_enabled")
        val KEY_BOTTOM_ENABLED = booleanPreferencesKey("edge_bottom_enabled")
        val KEY_EDGE_TRIGGER_WIDTH = floatPreferencesKey("edge_trigger_width_dp")
        val KEY_BOTTOM_TRIGGER_HEIGHT = floatPreferencesKey("bottom_trigger_height_dp")
        val KEY_DISABLE_BOTTOM_ON_IME = booleanPreferencesKey("disable_bottom_on_ime")
        val KEY_DISABLE_SIDES_ON_IME = booleanPreferencesKey("disable_sides_on_ime")
    }
}
