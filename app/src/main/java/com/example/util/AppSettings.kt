package com.example.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.BrandOrange
import com.example.ui.theme.GeistFontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Fixed design tokens: monochrome base + More2Day brand orange (from logo) as accent
val AppAccentColor = BrandOrange
val LocalAccentColor = compositionLocalOf { AppAccentColor }
val LocalHeadingFontFamily = compositionLocalOf { GeistFontFamily }
val LocalBodyFontFamily = compositionLocalOf { GeistFontFamily }

enum class AccentColor(val key: String, val displayName: String, val color: Color) {
    MONOCHROME("MONOCHROME", "More2Day", BrandOrange),
    INDIGO("INDIGO", "More2Day", BrandOrange)
}

enum class AppLanguage(val key: String, val displayName: String) {
    DE("DE", "Deutsch"),
    EN("EN", "English")
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.DE }

class AppSettings private constructor(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("more2day_settings", Context.MODE_PRIVATE)

    init {
        // Ensure the initial block list is completely empty as requested by user
        val hasMigratedV2 = prefs.getBoolean(KEY_BLOCKED_PACKAGES_INITIALIZED_V2, false)
        if (!hasMigratedV2) {
            val oldDefault = setOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.google.android.youtube",
                "com.reddit.frontpage",
                "com.twitter.android"
            )
            val stored = prefs.getStringSet(KEY_BLOCKED_PACKAGES, null)
            if (stored == null || stored == oldDefault) {
                prefs.edit()
                    .putStringSet(KEY_BLOCKED_PACKAGES, emptySet())
                    .putBoolean(KEY_BLOCKED_PACKAGES_INITIALIZED_V2, true)
                    .apply()
            } else {
                prefs.edit().putBoolean(KEY_BLOCKED_PACKAGES_INITIALIZED_V2, true).apply()
            }
        }
    }

    // Fixed design token compatibility flows
    val selectedAccent: StateFlow<AccentColor> = MutableStateFlow(AccentColor.MONOCHROME)
    val selectedLanguage: StateFlow<AppLanguage> = MutableStateFlow(AppLanguage.DE)

    private val _routinesResetInfoDismissed = MutableStateFlow(
        prefs.getBoolean(KEY_ROUTINES_RESET_INFO_DISMISSED, false)
    )
    val routinesResetInfoDismissed: StateFlow<Boolean> = _routinesResetInfoDismissed.asStateFlow()

    fun dismissRoutinesResetInfo() {
        prefs.edit().putBoolean(KEY_ROUTINES_RESET_INFO_DISMISSED, true).apply()
        _routinesResetInfoDismissed.value = true
    }

    // Daily General Reminder
    private val _generalReminderEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_GENERAL_REMINDER_ENABLED, false)
    )
    val generalReminderEnabled: StateFlow<Boolean> = _generalReminderEnabled.asStateFlow()

    private val _generalReminderHour = MutableStateFlow(
        prefs.getInt(KEY_GENERAL_REMINDER_HOUR, 20)
    )
    val generalReminderHour: StateFlow<Int> = _generalReminderHour.asStateFlow()

    private val _generalReminderMinute = MutableStateFlow(
        prefs.getInt(KEY_GENERAL_REMINDER_MINUTE, 0)
    )
    val generalReminderMinute: StateFlow<Int> = _generalReminderMinute.asStateFlow()

    // Blocked Consumer App Packages (Initially empty - user selects their own apps)
    private val defaultBlockedPackages = emptySet<String>()

    private val _blockedAppPackages = MutableStateFlow<Set<String>>(
        prefs.getStringSet(KEY_BLOCKED_PACKAGES, defaultBlockedPackages)?.toSet() ?: defaultBlockedPackages
    )
    val blockedAppPackages: StateFlow<Set<String>> = _blockedAppPackages.asStateFlow()

    // Block Rule Mode ("ALL_HABITS" or "DEADLINE")
    private val _blockRuleMode = MutableStateFlow(
        prefs.getString(KEY_BLOCK_RULE_MODE, "ALL_HABITS") ?: "ALL_HABITS"
    )
    val blockRuleMode: StateFlow<String> = _blockRuleMode.asStateFlow()

    // Deadline Time for Blocking (e.g. 20:30)
    private val _blockDeadlineHour = MutableStateFlow(
        prefs.getInt(KEY_BLOCK_DEADLINE_HOUR, 20)
    )
    val blockDeadlineHour: StateFlow<Int> = _blockDeadlineHour.asStateFlow()

    private val _blockDeadlineMinute = MutableStateFlow(
        prefs.getInt(KEY_BLOCK_DEADLINE_MINUTE, 30)
    )
    val blockDeadlineMinute: StateFlow<Int> = _blockDeadlineMinute.asStateFlow()

    fun setGeneralReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GENERAL_REMINDER_ENABLED, enabled).apply()
        _generalReminderEnabled.value = enabled
    }

    fun setGeneralReminderTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_GENERAL_REMINDER_HOUR, hour)
            .putInt(KEY_GENERAL_REMINDER_MINUTE, minute)
            .apply()
        _generalReminderHour.value = hour
        _generalReminderMinute.value = minute
    }

    fun setBlockedAppPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, packages).apply()
        _blockedAppPackages.value = packages
    }

    fun toggleBlockedPackage(packageName: String) {
        val current = _blockedAppPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setBlockedAppPackages(current)
    }

    fun setBlockRuleMode(mode: String) {
        prefs.edit().putString(KEY_BLOCK_RULE_MODE, mode).apply()
        _blockRuleMode.value = mode
    }

    fun setBlockDeadlineTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_BLOCK_DEADLINE_HOUR, hour)
            .putInt(KEY_BLOCK_DEADLINE_MINUTE, minute)
            .apply()
        _blockDeadlineHour.value = hour
        _blockDeadlineMinute.value = minute
    }

    // Keep compatibility stubs so existing references and tests don't crash
    private val _vibrationFeedbackEnabled = MutableStateFlow(false)
    val vibrationFeedbackEnabled: StateFlow<Boolean> = _vibrationFeedbackEnabled.asStateFlow()
    fun setVibrationFeedbackEnabled(enabled: Boolean) {
        _vibrationFeedbackEnabled.value = enabled
    }

    private val _completionAnimationEnabled = MutableStateFlow(true)
    val completionAnimationEnabled: StateFlow<Boolean> = _completionAnimationEnabled.asStateFlow()
    fun setCompletionAnimationEnabled(enabled: Boolean) {
        _completionAnimationEnabled.value = enabled
    }

    // Explicit Local Diagnostics Mode (Off by default)
    private val _diagnosticsModeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_DIAGNOSTICS_MODE_ENABLED, false)
    )
    val diagnosticsModeEnabled: StateFlow<Boolean> = _diagnosticsModeEnabled.asStateFlow()

    fun setDiagnosticsModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIAGNOSTICS_MODE_ENABLED, enabled).apply()
        _diagnosticsModeEnabled.value = enabled
    }

    companion object {
        private const val KEY_ROUTINES_RESET_INFO_DISMISSED = "routines_reset_info_dismissed"
        private const val KEY_GENERAL_REMINDER_ENABLED = "general_reminder_enabled"
        private const val KEY_GENERAL_REMINDER_HOUR = "general_reminder_hour"
        private const val KEY_GENERAL_REMINDER_MINUTE = "general_reminder_minute"
        private const val KEY_BLOCKED_PACKAGES = "blocked_app_packages"
        private const val KEY_BLOCKED_PACKAGES_INITIALIZED_V2 = "blocked_packages_v2_empty_default"
        private const val KEY_BLOCK_RULE_MODE = "block_rule_mode"
        private const val KEY_BLOCK_DEADLINE_HOUR = "block_deadline_hour"
        private const val KEY_BLOCK_DEADLINE_MINUTE = "block_deadline_minute"
        private const val KEY_DIAGNOSTICS_MODE_ENABLED = "diagnostics_mode_enabled"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
        }
    }
}
