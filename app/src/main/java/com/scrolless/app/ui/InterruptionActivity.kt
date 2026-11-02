package com.scrolless.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrolless.app.core.InterruptionReason
import com.scrolless.app.core.SessionAndStreakManager
import com.scrolless.app.data.LocalStore
import com.scrolless.app.service.ScrollessAccessibilityService
import kotlinx.coroutines.delay

/**
 * Editorial interruption overlay inspired by playaperello's offseason card.
 * Bounces the user back out of the Reels/Shorts tab or enforces a 10-second mindful pause.
 */
class InterruptionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(applicationContext)
        val targetName = intent.getStringExtra("target_name") ?: "Reels / Shorts"
        val reasonName = intent.getStringExtra("reason_name") ?: InterruptionReason.MINDFUL_PAUSE_ON_ENTRY.name
        val canContinue = intent.getBooleanExtra("can_continue", false)
        val remainingSec = intent.getIntExtra("remaining_sec", 0)
        val reason = runCatching { InterruptionReason.valueOf(reasonName) }
            .getOrDefault(InterruptionReason.MINDFUL_PAUSE_ON_ENTRY)

        setContent {
            val isDark = store.isDarkTheme(defaultDark = true)
            val isEs = store.getLanguage() == "es"
            val state = store.loadState()

            PlayaTheme(darkTheme = isDark) {
                InterruptionScreen(
                    targetName = targetName,
                    reason = reason,
                    canContinue = canContinue,
                    remainingSec = remainingSec,
                    streakDays = state.streakDays,
                    interceptionsToday = state.interceptionsToday,
                    isEs = isEs,
                    onExitShortForm = {
                        ScrollessAccessibilityService.instance?.bounceBackFromShortForm()
                        finish()
                    },
                    onContinueMindfully = {
                        val nowSec = System.currentTimeMillis() / 1000L
                        val updated = SessionAndStreakManager.grantMindfulContinue(store.loadState(), nowSec)
                        store.saveState(updated)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun InterruptionScreen(
    targetName: String,
    reason: InterruptionReason,
    canContinue: Boolean,
    remainingSec: Int,
    streakDays: Int,
    interceptionsToday: Int,
    isEs: Boolean,
    onExitShortForm: () -> Unit,
    onContinueMindfully: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    var countdown by remember { mutableIntStateOf(10) }

    LaunchedEffect(canContinue) {
        if (canContinue) {
            while (countdown > 0) {
                delay(1000L)
                countdown -= 1
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 40.dp)
        ) {
            Text(
                text = "Scrolless",
                style = MaterialTheme.typography.headlineLarge,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isEs) "PANTALLA DE INTERRUPCIÓN" else "INTERRUPTION SCREEN",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))
            HorizontalDivider(thickness = 1.dp, color = palette.borderColor)

            Column(
                modifier = Modifier.padding(vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val streakBadge = if (isEs) {
                    "${targetName.uppercase()} · RACHA DE $streakDays DÍAS"
                } else {
                    "${targetName.uppercase()} · $streakDays-DAY STREAK"
                }
                Text(
                    text = streakBadge,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )

                val headline = when (reason) {
                    InterruptionReason.MINDFUL_PAUSE_ON_ENTRY ->
                        if (isEs) "Respirá antes de scrollear" else "Breathe before scrolling"
                    InterruptionReason.DAILY_LIMIT_EXHAUSTED ->
                        if (isEs) "Presupuesto diario completado" else "Daily tab budget reached"
                    InterruptionReason.SURGICAL_ZERO_TOLERANCE ->
                        if (isEs) "Sección de scroll bloqueada" else "Short-form section blocked"
                }

                val body = when (reason) {
                    InterruptionReason.MINDFUL_PAUSE_ON_ENTRY ->
                        if (isEs) {
                            "Entraste a $targetName. Tus mensajes y el resto de la app siguen libres, pero acá corre tu presupuesto diario (${formatMinSec(remainingSec)} disponibles)."
                        } else {
                            "You entered $targetName. Your DMs and the rest of the app remain unrestricted, but your daily tab timer runs here (${formatMinSec(remainingSec)} left)."
                        }
                    InterruptionReason.DAILY_LIMIT_EXHAUSTED ->
                        if (isEs) {
                            "Ya usaste tu tiempo de hoy en pestañas de scroll corto. Volvé atrás para mantener tu racha intacta y seguir usando el resto de la aplicación."
                        } else {
                            "You have used today's short-form tab budget. Step back to keep your streak intact while continuing to use the rest of the app."
                        }
                    InterruptionReason.SURGICAL_ZERO_TOLERANCE ->
                        if (isEs) {
                            "Tenés activo el Bloqueo Quirúrgico Total. Hoy ya frenaste $interceptionsToday impulsos automáticos."
                        } else {
                            "Surgical Zero-Scroll mode is active. You have already stopped $interceptionsToday automatic impulses today."
                        }
                }

                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                PillButton(
                    label = if (isEs) "← Salir del scroll y cuidar mi racha" else "← Exit short-form & protect streak",
                    onClick = onExitShortForm
                )

                if (canContinue) {
                    val waitText = if (countdown > 0) {
                        if (isEs) "Respirá ${countdown}s por si querés entrar igual…"
                        else "Breathe ${countdown}s if you still wish to enter…"
                    } else {
                        if (isEs) "Entrar 3 minutos conscientes →" else "Enter for 3 mindful minutes →"
                    }
                    Text(
                        text = waitText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (countdown == 0) palette.textPrimary else palette.textSecondary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable(enabled = countdown == 0) { onContinueMindfully() }
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
        }
    }
}
