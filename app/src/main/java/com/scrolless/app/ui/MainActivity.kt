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
import com.scrolless.app.core.AppBlockScope
import com.scrolless.app.core.ProtectionConfig
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.RuntimeState
import com.scrolless.app.core.ShortFormTarget
import com.scrolless.app.data.LocalStore
import com.scrolless.app.service.ScrollessAccessibilityService

/**
 * Dashboard host. Writes configuration only (never runtime counters, which belong to the service),
 * and always edits the freshest stored config rather than a possibly stale UI copy.
 */
class MainActivity : ComponentActivity() {

    private lateinit var store: LocalStore
    private var config by mutableStateOf(ProtectionConfig())
    private var runtime by mutableStateOf(RuntimeState())
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
                    config = config,
                    runtime = runtime,
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
                        updateConfig { it.copy(mode = mode, dailyLimitSeconds = mode.defaultDailyLimitSeconds) }
                    },
                    onCustomMinutesApply = { minutes: Int ->
                        updateConfig { current ->
                            val newMode = when {
                                minutes == 0 -> ProtectionMode.SURGICAL_BLOCK
                                current.mode == ProtectionMode.SURGICAL_BLOCK -> ProtectionMode.MINDFUL_BUDGET
                                else -> current.mode
                            }
                            current.copy(mode = newMode, dailyLimitSeconds = minutes * 60)
                        }
                    },
                    onToggleTarget = { target: ShortFormTarget ->
                        updateConfig { current ->
                            val next = if (target in current.enabledTargets) {
                                current.enabledTargets - target
                            } else {
                                current.enabledTargets + target
                            }
                            current.copy(enabledTargets = next)
                        }
                    },
                    onSetScope = { target: ShortFormTarget, scope: AppBlockScope ->
                        updateConfig { current ->
                            val next = if (scope == AppBlockScope.FULL_APP) {
                                current.fullAppTargets + target
                            } else {
                                current.fullAppTargets - target
                            }
                            current.copy(fullAppTargets = next)
                        }
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }

    private fun updateConfig(transform: (ProtectionConfig) -> ProtectionConfig) {
        val updated = transform(store.loadConfig())
        store.saveConfig(updated)
        config = updated
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
        config = store.loadConfig()
        runtime = store.loadRuntimeForToday(config)
        isServiceActive = ScrollessAccessibilityService.isRunning
        isDark = store.isDarkTheme(defaultDark = false)
        lang = store.getLanguage()
    }
}
