package com.scrolless.app.data

import android.content.Context
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.SessionAndStreakManager
import com.scrolless.app.core.SessionAndStreakState
import com.scrolless.app.core.ShortFormTarget
import java.time.LocalDate

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("scrolless_local_store", Context.MODE_PRIVATE)

    fun loadState(): SessionAndStreakState {
        val todayIso = LocalDate.now().toString()
        val lastDate = prefs.getString("last_date_iso", todayIso) ?: todayIso

        val modeName = prefs.getString("protection_mode", ProtectionMode.MINDFUL_BUDGET.name)
            ?: ProtectionMode.MINDFUL_BUDGET.name
        val mode = runCatching { ProtectionMode.valueOf(modeName) }
            .getOrDefault(ProtectionMode.MINDFUL_BUDGET)

        val rawTargets = prefs.getString("enabled_targets", null)
        val enabledTargets: Set<ShortFormTarget> = if (rawTargets == null) {
            ShortFormTarget.entries.toSet()
        } else if (rawTargets.isBlank()) {
            emptySet()
        } else {
            rawTargets.split(",")
                .mapNotNull { name -> runCatching { ShortFormTarget.valueOf(name.trim()) }.getOrNull() }
                .toSet()
        }

        var state = SessionAndStreakState(
            mode = mode,
            dailyLimitSeconds = prefs.getInt("daily_limit_sec", mode.defaultDailyLimitSeconds),
            activeReelSecondsToday = prefs.getInt("active_reel_sec_today", 0),
            interceptionsToday = prefs.getInt("interceptions_today", 0),
            weeklyInterceptions = prefs.getInt("weekly_interceptions", 14),
            streakDays = prefs.getInt("streak_days", 4),
            streakShields = prefs.getInt("streak_shields", 1),
            mindfulAllowanceUntilEpochSec = prefs.getLong("mindful_until_epoch", 0L),
            enabledTargets = enabledTargets
        )

        if (lastDate != todayIso) {
            state = SessionAndStreakManager.onDayRollover(state)
            prefs.edit().putString("last_date_iso", todayIso).apply()
            saveState(state)
        }
        return state
    }

    fun saveState(state: SessionAndStreakState) {
        val targetsString = state.enabledTargets.joinToString(",") { it.name }
        prefs.edit()
            .putString("protection_mode", state.mode.name)
            .putInt("daily_limit_sec", state.dailyLimitSeconds)
            .putInt("active_reel_sec_today", state.activeReelSecondsToday)
            .putInt("interceptions_today", state.interceptionsToday)
            .putInt("weekly_interceptions", state.weeklyInterceptions)
            .putInt("streak_days", state.streakDays)
            .putInt("streak_shields", state.streakShields)
            .putLong("mindful_until_epoch", state.mindfulAllowanceUntilEpochSec)
            .putString("enabled_targets", targetsString)
            .putString("last_date_iso", LocalDate.now().toString())
            .apply()
    }

    fun isDarkTheme(defaultDark: Boolean): Boolean = prefs.getBoolean("dark_theme", defaultDark)
    fun setDarkTheme(isDark: Boolean) = prefs.edit().putBoolean("dark_theme", isDark).apply()

    fun getLanguage(): String = prefs.getString("ui_lang", "es") ?: "es"
    fun setLanguage(lang: String) = prefs.edit().putString("ui_lang", lang).apply()

    fun isInspectorEnabled(): Boolean = prefs.getBoolean("inspector_enabled", false)
    fun setInspectorEnabled(enabled: Boolean) = prefs.edit().putBoolean("inspector_enabled", enabled).apply()

    fun getLastInspectedViewIds(): List<String> {
        val raw = prefs.getString("last_inspected_ids", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }
    }

    fun recordInspectedViewIds(ids: Collection<String>) {
        val trimmed = ids.take(8).joinToString("|")
        prefs.edit().putString("last_inspected_ids", trimmed).apply()
    }
}
