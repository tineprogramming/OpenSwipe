package com.openswipe.ui.util

import com.openswipe.model.ActionNode

/**
 * Shared action category definitions used by ActionPickerDialog and AddRuleDialog.
 */
fun actionCategories(actions: List<ActionNode> = ActionNode.allFixed()): List<Pair<String, List<ActionNode>>> = listOf(
    "Navigation" to actions.filter { it is ActionNode.Back || it is ActionNode.Home || it is ActionNode.Recents || it is ActionNode.SwitchLastApp },
    "System" to actions.filter { it is ActionNode.LockScreen || it is ActionNode.Screenshot || it is ActionNode.SplitScreen || it is ActionNode.PowerMenu },
    "Panels" to actions.filter { it is ActionNode.NotificationPanel || it is ActionNode.QuickSettings },
    "Media" to actions.filter { it is ActionNode.MediaPlayPause || it is ActionNode.MediaNext || it is ActionNode.MediaPrevious || it is ActionNode.VolumeUp || it is ActionNode.VolumeDown },
    "Hardware" to actions.filter { it is ActionNode.ToggleFlashlight },
    "Other" to actions.filter { it is ActionNode.NoAction },
)
