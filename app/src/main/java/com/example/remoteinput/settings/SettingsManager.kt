package com.example.remoteinput.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ghostboard_settings", Context.MODE_PRIVATE)

    var themeName: String
        get() = prefs.getString(KEY_THEME, "Midnight") ?: "Midnight"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var trackpadOnLeft: Boolean
        get() = prefs.getBoolean(KEY_TRACKPAD_LEFT, false)
        set(value) = prefs.edit().putBoolean(KEY_TRACKPAD_LEFT, value).apply()

    var useBuiltInKeyboard: Boolean
        get() = prefs.getBoolean(KEY_BUILTIN_KB, false)
        set(value) = prefs.edit().putBoolean(KEY_BUILTIN_KB, value).apply()

    val currentTheme: AppTheme
        get() = AppTheme.fromName(themeName)

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_TRACKPAD_LEFT = "trackpad_on_left"
        private const val KEY_BUILTIN_KB = "use_builtin_keyboard"
    }
}
