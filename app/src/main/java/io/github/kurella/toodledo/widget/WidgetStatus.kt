package io.github.kurella.toodledo.widget

import android.content.SharedPreferences

/**
 * Represents the current data state of the widget.
 *
 * The widget always displays the status line based on this state.
 * On error states, the previous task list is preserved — only the
 * status line changes. If no successful fetch has occurred yet
 * (e.g. first fetch after install fails), the list remains empty.
 */
enum class WidgetStatus {
    /** After install or logout. No successful API call has been made yet. */
    INITIAL,

    /** Last fetch was successful. Shows task list or "no tasks due". */
    LOADED,

    /** Network unavailable during last fetch attempt. Previous list preserved. */
    OFFLINE,

    /** API returned an unexpected or invalid response. Previous list preserved. */
    API_ERROR,

    /**
     * Authentication failed: expired refresh token or rate limit.
     * Previous list preserved. Tap opens Settings for re-login.
     */
    LOGGED_OUT
}

private const val PREF_WIDGET_STATUS = "widget_status"

fun readStatus(prefs: SharedPreferences): WidgetStatus = try {
    WidgetStatus.valueOf(prefs.getString(PREF_WIDGET_STATUS, WidgetStatus.INITIAL.name)!!)
} catch (_: IllegalArgumentException) {
    WidgetStatus.INITIAL
}

fun writeStatus(prefs: SharedPreferences, status: WidgetStatus) {
    prefs.edit().putString(PREF_WIDGET_STATUS, status.name).apply()
}
