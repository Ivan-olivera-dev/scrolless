package com.scrolless.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.data.LocalStore
import com.scrolless.app.service.ScrollessAccessibilityService

class MainActivity : ComponentActivity() {

    private lateinit var store: LocalStore
    private var uiState by mutableStateOf(com.scrolless.app.core.SessionAndStreakState())
    private var isServiceActive by mutableStateOf(false)
    private var isDark by mutableStateOf(false)
    private var lang by mutableStateOf("es")
    private var inspectorEnabled by mutableStateOf(false)
    private var inspectedIds by mutableStateOf<List<String>>(emptyList())

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
                    inspectorEnabled = inspectorEnabled,
                    inspectedIds = inspectedIds,
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
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onToggleInspector = {
                        inspectorEnabled = !inspectorEnabled
                        store.setInspectorEnabled(inspectorEnabled)
                        inspectedIds = store.getLastInspectedViewIds()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        uiState = store.loadState()
        isServiceActive = ScrollessAccessibilityService.isRunning
        isDark = store.isDarkTheme(defaultDark = false)
        lang = store.getLanguage()
        inspectorEnabled = store.isInspectorEnabled()
        inspectedIds = store.getLastInspectedViewIds()
    }
}
