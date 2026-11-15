package com.scrolless.app.data

import android.content.Context
import android.content.SharedPreferences
import com.scrolless.app.core.ProtectionConfig
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.RuntimeCodec
import com.scrolless.app.core.RuntimeState
import com.scrolless.app.core.SessionController
import com.scrolless.app.core.ShortFormTarget
import java.time.LocalDate

/**
 * Local-only persistence. Config keys are written only by MainActivity; runtime keys only by the
 * accessibility service (or, when the service is not running, by InterruptionActivity's fallback).
 */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("scrolless_local_store", Context.MODE_PRIVATE)

    companion object {
        const val SCHEMA_VERSION = 3

        private const val KEY_SCHEMA = "store_schema_version"

        // Config
        const val KEY_MODE = "protection_mode"
        const val KEY_DAILY_LIMIT = "daily_limit_sec"
        const val KEY_ENABLED_TARGETS = "enabled_targets"
        const val KEY_FULL_APP_TARGETS = "full_app_targets"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_UI_LANG = "ui_lang"

        // Runtime
        const val KEY_TODAY_SECONDS = "active_reel_sec_today"
        const val KEY_TODAY_INTERCEPTIONS = "interceptions_today"
        const val KEY_HISTORY = "history_7d"
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_STREAK_SHIELDS = "streak_shields"
        const val KEY_PASS_TARGET = "pass_target"
        const val KEY_PASS_UNTIL_MS = "pass_until_ms"
        const val KEY_LAST_DATE = "last_date_iso"

        val CONFIG_KEYS = setOf(KEY_MODE, KEY_DAILY_LIMIT, KEY_ENABLED_TARGETS, KEY_FULL_APP_TARGETS)

        private val OBSOLETE_V3_KEYS = listOf(
            "is_currently_in_reel_tab",
            "current_target",
            "mindful_allowance_target",
            "mindful_until_epoch",
            "weekly_interceptions",
            "inspector_enabled",
            "last_inspected_ids"
        )
    }

    init {
        migrate()
    }

    private fun migrate() {
        val version = prefs.getInt(KEY_SCHEMA, 1)
        if (version >= SCHEMA_VERSION) return
        val editor = prefs.edit()
        if (version < 2) {
            // v1 shipped mocked numbers: reset every counter.
            editor.putInt(KEY_STREAK_DAYS, 0)
                .putInt(KEY_STREAK_SHIELDS, 0)
                .putInt(KEY_TODAY_INTERCEPTIONS, 0)
                .putInt(KEY_TODAY_SECONDS, 0)
        }
        // v3: split config/runtime; drop sticky session keys and the never-reset weekly counter.
        OBSOLETE_V3_KEYS.forEach { editor.remove(it) }
        editor.putInt(KEY_SCHEMA, SCHEMA_VERSION).apply()
    }

    fun loadConfig(): ProtectionConfig {
        val mode = prefs.getString(KEY_MODE, null)
            ?.let { name -> runCatching { ProtectionMode.valueOf(name) }.getOrNull() }
            ?: ProtectionMode.MINDFUL_BUDGET
        return ProtectionConfig(
            mode = mode,
            dailyLimitSeconds = prefs.getInt(KEY_DAILY_LIMIT, mode.defaultDailyLimitSeconds),
            enabledTargets = RuntimeCodec.decodeTargets(prefs.getString(KEY_ENABLED_TARGETS, null), ShortFormTarget.entries.toSet()),
            fullAppTargets = RuntimeCodec.decodeTargets(prefs.getString(KEY_FULL_APP_TARGETS, null), emptySet())
                - ShortFormTarget.TIKTOK
        )
    }

    fun saveConfig(config: ProtectionConfig) {
        prefs.edit()
            .putString(KEY_MODE, config.mode.name)
            .putInt(KEY_DAILY_LIMIT, config.dailyLimitSeconds)
            .putString(KEY_ENABLED_TARGETS, RuntimeCodec.encodeTargets(config.enabledTargets))
            .putString(KEY_FULL_APP_TARGETS, RuntimeCodec.encodeTargets(config.fullAppTargets - ShortFormTarget.TIKTOK))
            .apply()
    }

    /** Raw persisted runtime; callers apply [SessionController.rollover] for the current day. */
    fun loadRuntime(): RuntimeState = RuntimeState(
        dayIso = prefs.getString(KEY_LAST_DATE, "") ?: "",
        activeSecondsToday = prefs.getInt(KEY_TODAY_SECONDS, 0),
        interceptionsToday = prefs.getInt(KEY_TODAY_INTERCEPTIONS, 0),
        history = RuntimeCodec.decodeHistory(prefs.getString(KEY_HISTORY, "")),
        streakDays = prefs.getInt(KEY_STREAK_DAYS, 0),
        streakShields = prefs.getInt(KEY_STREAK_SHIELDS, 0),
        passTarget = prefs.getString(KEY_PASS_TARGET, "")
            ?.takeIf { it.isNotBlank() }
            ?.let { name -> runCatching { ShortFormTarget.valueOf(name) }.getOrNull() },
        passUntilMs = prefs.getLong(KEY_PASS_UNTIL_MS, 0L)
    )

    /** Runtime for today: persisted state with the day rollover applied (not written back). */
    fun loadRuntimeForToday(config: ProtectionConfig = loadConfig()): RuntimeState =
        SessionController.rollover(loadRuntime(), config, LocalDate.now().toString())

    fun saveRuntime(state: RuntimeState) {
        prefs.edit()
            .putString(KEY_LAST_DATE, state.dayIso)
            .putInt(KEY_TODAY_SECONDS, state.activeSecondsToday)
            .putInt(KEY_TODAY_INTERCEPTIONS, state.interceptionsToday)
            .putString(KEY_HISTORY, RuntimeCodec.encodeHistory(state.history))
            .putInt(KEY_STREAK_DAYS, state.streakDays)
            .putInt(KEY_STREAK_SHIELDS, state.streakShields)
            .putString(KEY_PASS_TARGET, state.passTarget?.name ?: "")
            .putLong(KEY_PASS_UNTIL_MS, state.passUntilMs)
            .apply()
    }

    /** Fallback used by InterruptionActivity when the service instance is not available. */
    fun applyContinueFallback(target: ShortFormTarget, nowMs: Long) {
        val config = loadConfig()
        saveRuntime(SessionController.onContinue(loadRuntimeForToday(config), config, target, nowMs))
    }

    /** Fallback used by InterruptionActivity when the service instance is not available. */
    fun applyStepBackFallback() {
        saveRuntime(SessionController.onStepBack(loadRuntimeForToday()))
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isDarkTheme(defaultDark: Boolean): Boolean = prefs.getBoolean(KEY_DARK_THEME, defaultDark)
    fun setDarkTheme(isDark: Boolean) = prefs.edit().putBoolean(KEY_DARK_THEME, isDark).apply()

    fun getLanguage(): String = prefs.getString(KEY_UI_LANG, "es") ?: "es"
    fun setLanguage(lang: String) = prefs.edit().putString(KEY_UI_LANG, lang).apply()
}
