package com.kamrenzirger.synctoandroiddata.util
import android.content.Context
import android.content.SharedPreferences
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
    companion object {
        const val KEY_START_ON_BOOT = "start_on_boot"
        const val KEY_SETUP_COMPLETED = "setup_completed"
        const val KEY_SHOW_TOASTS = "show_toasts"
        const val KEY_ENABLE_LOGGING = "enable_logging"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        const val KEY_LATEST_VERSION = "latest_version"
        const val KEY_UPDATE_AVAILABLE = "update_available"
    }
    var setupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()
    var showToasts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TOASTS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TOASTS, value).apply()
    var startOnBoot: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()
    var enableLogging: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_LOGGING, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_LOGGING, value).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    var latestVersion: String?
        get() = prefs.getString(KEY_LATEST_VERSION, null)
        set(value) = prefs.edit().putString(KEY_LATEST_VERSION, value).apply()

    var isUpdateAvailable: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_AVAILABLE, false)
        set(value) = prefs.edit().putBoolean(KEY_UPDATE_AVAILABLE, value).apply()
}
