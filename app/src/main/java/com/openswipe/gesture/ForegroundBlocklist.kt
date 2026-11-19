package com.openswipe.gesture

/**
 * Apps / activities in front of which all edge gestures are paused (touches pass through).
 *
 * Entry formats:
 *  - `com.pkg/com.pkg.SomeActivity` — one activity
 *  - `com.pkg/.SomeActivity`        — same, class relative to the package
 *  - `com.pkg` or `com.pkg/ *`      — every activity of that app
 */
class ForegroundBlocklist(entries: Collection<String>) {

    private val packages = mutableSetOf<String>()
    private val activities = mutableSetOf<String>()

    init {
        for (raw in entries) {
            val e = raw.trim()
            if (e.isEmpty() || e.startsWith("#")) continue
            val slash = e.indexOf('/')
            if (slash < 0) {
                packages += e
                continue
            }
            val pkg = e.substring(0, slash).trim()
            var cls = e.substring(slash + 1).trim()
            if (pkg.isEmpty()) continue
            when {
                cls.isEmpty() || cls == "*" -> packages += pkg
                else -> {
                    if (cls.startsWith(".")) cls = pkg + cls
                    activities += "$pkg/$cls"
                }
            }
        }
    }

    val isEmpty: Boolean get() = packages.isEmpty() && activities.isEmpty()

    fun matches(packageName: String, className: String?): Boolean =
        packageName in packages || (className != null && "$packageName/$className" in activities)

    /** Whether this exact activity is listed (so it is certainly an activity class). */
    fun listsActivity(packageName: String, className: String): Boolean =
        "$packageName/$className" in activities

    companion object {
        val EMPTY = ForegroundBlocklist(emptyList())

        /** Parses the comma/newline separated list baked into BuildConfig. */
        fun parseList(text: String): List<String> =
            text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
