package com.scrolless.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrolless.app.core.InterruptionReason
import com.scrolless.app.core.SessionAndStreakManager
import com.scrolless.app.data.LocalStore
import com.scrolless.app.service.ScrollessAccessibilityService
import kotlinx.coroutines.delay

/**
 * Calm, architectural interruption experience with an animated breathing dial.
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
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
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

    val infiniteTransition = rememberInfiniteTransition(label = "breathingRing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val ringColor = when (reason) {
        InterruptionReason.MINDFUL_PAUSE_ON_ENTRY -> palette.success
        InterruptionReason.DAILY_LIMIT_EXHAUSTED -> palette.warning
        InterruptionReason.SURGICAL_ZERO_TOLERANCE -> palette.danger
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(palette.bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 44.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Scrolless Architectural Brand Mark
            ScrollessLogoMark(size = 28.dp)

            // Pill Tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.surfaceColor)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ringColor)
                    )
                    Text(
                        text = targetName.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary
                    )
                }
            }

            // Animated Breathing Geometric Dial
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f - 8.dp.toPx()

                    // Soft breathing inner circle
                    drawCircle(
                        color = ringColor.copy(alpha = 0.12f),
                        radius = maxRadius * breathScale,
                        center = centerOffset
                    )

                    // Outer architectural ring
                    drawCircle(
                        color = palette.borderColor,
                        radius = maxRadius,
                        center = centerOffset,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Countdown progress arc
                    val sweep = if (canContinue) {
                        ((10 - countdown) / 10f) * 360f
                    } else {
                        360f
                    }
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - maxRadius, centerOffset.y - maxRadius),
                        size = Size(maxRadius * 2f, maxRadius * 2f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (canContinue && countdown > 0) "${countdown}s" else "0s",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1.5).sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (canContinue) {
                            if (isEs) "PAUSA CONSCIENTE" else "MINDFUL PAUSE"
                        } else {
                            if (isEs) "ESCUDO ACTIVO" else "SHIELD ACTIVE"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                        color = palette.textSecondary
                    )
                }
            }

            // Editorial Headline & Explanation
            val headline = when (reason) {
                InterruptionReason.MINDFUL_PAUSE_ON_ENTRY ->
                    if (isEs) "¿Entraste por elección o por inercia?" else "By choice or by habit?"
                InterruptionReason.DAILY_LIMIT_EXHAUSTED ->
                    if (isEs) "Presupuesto diario completado" else "Daily tab budget reached"
                InterruptionReason.SURGICAL_ZERO_TOLERANCE ->
                    if (isEs) "Scroll vertical interceptado" else "Short-form feed intercepted"
            }

            val subtitle = when (reason) {
                InterruptionReason.MINDFUL_PAUSE_ON_ENTRY ->
                    if (isEs) {
                        "Tus mensajes directos y el resto de la app siguen libres. Si entrás a $targetName, correrá tu margen diario."
                    } else {
                        "Your direct messages and the rest of the app remain open. Entering $targetName starts your daily tab timer."
                    }
                InterruptionReason.DAILY_LIMIT_EXHAUSTED ->
                    if (isEs) {
                        "Ya consumiste tu margen de hoy en pestañas de formato corto. Volvé atrás para proteger tu racha."
                    } else {
                        "You have used today's short-form tab allowance. Step back to keep your streak intact."
                    }
                InterruptionReason.SURGICAL_ZERO_TOLERANCE ->
                    if (isEs) {
                        "El protocolo de Bloqueo Quirúrgico está activo. Hoy ya cortaste $interceptionsToday aperturas automáticas."
                    } else {
                        "Zero-Scroll protocol is active. You have already stopped $interceptionsToday automatic opens today."
                    }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // 2-Column Context Strip (Streak + Remaining Budget)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceColor)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(16.dp))
                    .padding(vertical = 14.dp, horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEs) "RACHA ACTUAL" else "CURRENT STREAK",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isEs) {
                            if (streakDays == 1) "1 día" else "$streakDays días"
                        } else {
                            if (streakDays == 1) "1 day" else "$streakDays days"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (isEs) "MARGEN HOY" else "LEFT TODAY",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatMinSec(remainingSec),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ringColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Solid Action Button: Step Back
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.textPrimary)
                    .clickable { onExitShortForm() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEs) "Volver atrás y proteger mi racha" else "Step back & protect my streak",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = palette.bgColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Secondary Mindful Unlock (only if budget remains and countdown finished)
            if (canContinue) {
                val unlockLabel = if (countdown > 0) {
                    if (isEs) "Respirá ${countdown}s para desbloquear acceso temporal"
                    else "Breathe ${countdown}s to unlock temporary access"
                } else {
                    if (isEs) "Continuar 3 minutos conscientes"
                    else "Continue for 3 mindful minutes"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = 1.dp,
                            color = if (countdown == 0) palette.borderColor else palette.borderColor.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = countdown == 0) { onContinueMindfully() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unlockLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (countdown == 0) palette.textPrimary else palette.textSecondary.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
