package com.scrolless.app.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.ShortFormTarget
import com.scrolless.app.data.LocalStore
import com.scrolless.app.service.ScrollessAccessibilityService

class MainActivity : ComponentActivity() {

    private lateinit var store: LocalStore
    private var uiState by mutableStateOf(com.scrolless.app.core.SessionAndStreakState())
    private var isServiceActive by mutableStateOf(false)
    private var isDark by mutableStateOf(false)
    private var lang by mutableStateOf("es")

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStore(applicationContext)
        refreshState()

        setContent {
            PlayaTheme(darkTheme = isDark) {
                DashboardScreen(
                    state = uiState,
                    isServiceActive = isServiceActive,
                    lang = lang,
                    isDark = isDark,
                    onToggleLang = {
                        lang = if (lang == "es") "en" else "es"
                        store.setLanguage(lang)
                    },
                    onToggleTheme = {
                        isDark = !isDark
                        store.setDarkTheme(isDark)
                    },
                    onSelectMode = { mode: ProtectionMode ->
                        val updated = uiState.copy(
                            mode = mode,
                            dailyLimitSeconds = mode.defaultDailyLimitSeconds
                        )
                        store.saveState(updated)
                        uiState = updated
                    },
                    onCustomMinutesApply = { minutes: Int ->
                        val newLimitSec = minutes * 60
                        val newMode = if (minutes == 0) {
                            ProtectionMode.SURGICAL_BLOCK
                        } else if (uiState.mode == ProtectionMode.SURGICAL_BLOCK) {
                            ProtectionMode.MINDFUL_BUDGET
                        } else {
                            uiState.mode
                        }
                        val updated = uiState.copy(
                            mode = newMode,
                            dailyLimitSeconds = newLimitSec
                        )
                        store.saveState(updated)
                        uiState = updated
                    },
                    onToggleTarget = { target: ShortFormTarget ->
                        val currentTargets = uiState.enabledTargets
                        val nextTargets = if (target in currentTargets) {
                            currentTargets - target
                        } else {
                            currentTargets + target
                        }
                        val updated = uiState.copy(enabledTargets = nextTargets)
                        store.saveState(updated)
                        uiState = updated
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        store.registerListener(prefsListener)
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onStop() {
        store.unregisterListener(prefsListener)
        super.onStop()
    }

    private fun refreshState() {
        uiState = store.loadState()
        isServiceActive = ScrollessAccessibilityService.isRunning
        isDark = store.isDarkTheme(defaultDark = false)
        lang = store.getLanguage()
    }
}
