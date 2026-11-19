package com.openswipe.model

sealed interface ActionNode {
    val id: String
    val label: String
    val minApi: Int

    // ═══ 系统导航 ═══
    data object Back : ActionNode {
        override val id = "back"
        override val label = "Back"
        override val minApi = 16
    }
    data object Home : ActionNode {
        override val id = "home"
        override val label = "Home"
        override val minApi = 16
    }
    data object Recents : ActionNode {
        override val id = "recents"
        override val label = "Recents"
        override val minApi = 16
    }
    data object SwitchLastApp : ActionNode {
        override val id = "switch_last_app"
        override val label = "Last app"
        override val minApi = 16
    }

    // ═══ 系统控制 ═══
    data object LockScreen : ActionNode {
        override val id = "lock_screen"
        override val label = "Lock screen"
        override val minApi = 28
    }
    data object Screenshot : ActionNode {
        override val id = "screenshot"
        override val label = "Screenshot"
        override val minApi = 28
    }
    data object SplitScreen : ActionNode {
        override val id = "split_screen"
        override val label = "Split screen"
        override val minApi = 24
    }
    data object PowerMenu : ActionNode {
        override val id = "power_menu"
        override val label = "Power menu"
        override val minApi = 21
    }

    // ═══ 面板 ═══
    data object NotificationPanel : ActionNode {
        override val id = "notification_panel"
        override val label = "Notifications"
        override val minApi = 16
    }
    data object QuickSettings : ActionNode {
        override val id = "quick_settings"
        override val label = "Quick settings"
        override val minApi = 17
    }

    // ═══ 媒体控制 ═══
    data object MediaPlayPause : ActionNode {
        override val id = "media_play_pause"
        override val label = "Play/Pause"
        override val minApi = 16
    }
    data object MediaNext : ActionNode {
        override val id = "media_next"
        override val label = "Next track"
        override val minApi = 16
    }
    data object MediaPrevious : ActionNode {
        override val id = "media_previous"
        override val label = "Previous track"
        override val minApi = 16
    }
    data object VolumeUp : ActionNode {
        override val id = "volume_up"
        override val label = "Volume +"
        override val minApi = 16
    }
    data object VolumeDown : ActionNode {
        override val id = "volume_down"
        override val label = "Volume -"
        override val minApi = 16
    }

    // ═══ 硬件 ═══
    data object ToggleFlashlight : ActionNode {
        override val id = "toggle_flashlight"
        override val label = "Flashlight"
        override val minApi = 23
    }

    // ═══ 应用启动 ═══
    data class LaunchApp(
        val packageName: String,
        val appName: String
    ) : ActionNode {
        override val id = "launch_app:$packageName"
        override val label = appName
        override val minApi = 16
    }

    // ═══ 空操作 ═══
    data object NoAction : ActionNode {
        override val id = "no_action"
        override val label = "No action"
        override val minApi = 16
    }

    companion object {
        val allFixed: List<ActionNode> by lazy {
            listOf(
                Back, Home, Recents, SwitchLastApp,
                LockScreen, Screenshot, SplitScreen, PowerMenu,
                NotificationPanel, QuickSettings,
                MediaPlayPause, MediaNext, MediaPrevious, VolumeUp, VolumeDown,
                ToggleFlashlight,
                NoAction,
            )
        }

        /** Keep the function overload for source compatibility. */
        fun allFixed(): List<ActionNode> = allFixed

        private val fixedById: Map<String, ActionNode> by lazy {
            allFixed.associateBy { it.id }
        }

        fun fromId(id: String): ActionNode? {
            if (id.startsWith("launch_app:")) {
                val pkg = id.removePrefix("launch_app:")
                return LaunchApp(pkg, pkg)
            }
            return fixedById[id]
        }
    }
}
