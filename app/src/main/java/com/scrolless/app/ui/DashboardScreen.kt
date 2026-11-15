package com.scrolless.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrolless.app.core.AppBlockScope
import com.scrolless.app.core.ProtectionConfig
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.RuntimeState
import com.scrolless.app.core.SessionController
import com.scrolless.app.core.ShortFormTarget
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DashboardScreen(
    config: ProtectionConfig,
    runtime: RuntimeState,
    isServiceActive: Boolean,
    lang: String,
    isDark: Boolean,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectMode: (ProtectionMode) -> Unit,
    onCustomMinutesApply: (Int) -> Unit,
    onToggleTarget: (ShortFormTarget) -> Unit,
    onSetScope: (ShortFormTarget, AppBlockScope) -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    val isEs = lang == "es"
    val accentColor = when (config.mode) {
        ProtectionMode.MINDFUL_BUDGET -> palette.success
        ProtectionMode.STRICT_BUDGET -> palette.warning
        ProtectionMode.SURGICAL_BLOCK -> palette.danger
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(palette.bgColor),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 44.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Minimalist Architectural Top Bar
            TopBar(
                isServiceActive = isServiceActive,
                isEs = isEs,
                isDark = isDark,
                accentColor = accentColor,
                onToggleLang = onToggleLang,
                onToggleTheme = onToggleTheme
            )

            // 2. Service Activation Banner (Only prominent when Accessibility is off)
            if (!isServiceActive) {
                ServiceOnboardingBanner(
                    isEs = isEs,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }

            // 3. Hero Card: Architectural Radial Time Dial + Daily Budget
            TimeBudgetHeroCard(
                config = config,
                runtime = runtime,
                isEs = isEs,
                accentColor = accentColor
            )

            // 4. Flat Bento Grid: Streak Monument + Impulses & Recovered Hours
            StreakAndMetricsBento(
                state = runtime,
                isEs = isEs
            )

            // 5. Tactile Intervention Mode Selector (3 Architectural Cards + Custom Minutes)
            SectionHeader(
                title = if (isEs) "PROTOCOLO DE INTERVENCIÓN" else "INTERVENTION PROTOCOL"
            )
            val isCustomSelected = !(config.mode == ProtectionMode.MINDFUL_BUDGET && config.dailyLimitSeconds == 15 * 60) &&
                !(config.mode == ProtectionMode.STRICT_BUDGET && config.dailyLimitSeconds == 5 * 60) &&
                !(config.mode == ProtectionMode.SURGICAL_BLOCK || config.dailyLimitSeconds == 0)

            ModeSelectorStack(
                state = config,
                isEs = isEs,
                onSelectMode = onSelectMode
            )
            CustomMinutesCard(
                currentMinutes = config.dailyLimitSeconds / 60,
                isCustomSelected = isCustomSelected,
                isEs = isEs,
                onApplyMinutes = onCustomMinutesApply
            )

            // 6. Surgical Target Matrix (Per-Platform Scope)
            SectionHeader(
                title = if (isEs) "COBERTURA QUIRÚRGICA ACTIVA" else "ACTIVE SURGICAL TARGETS"
            )
            SurgicalTargetsPanel(
                enabledTargets = config.enabledTargets,
                fullAppTargets = config.fullAppTargets,
                isEs = isEs,
                onToggleTarget = onToggleTarget,
                onSetScope = onSetScope
            )
        }
    }
}

@Composable
private fun TopBar(
    isServiceActive: Boolean,
    isEs: Boolean,
    isDark: Boolean,
    accentColor: Color,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScrollessLogoMark(size = 28.dp)
            Text(
                text = "scrolless",
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp,
                color = palette.textPrimary
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.surfaceColor)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isServiceActive) accentColor else palette.danger)
                    )
                    Text(
                        text = if (isServiceActive) {
                            if (isEs) "ACTIVO" else "ACTIVE"
                        } else {
                            if (isEs) "PAUSADO" else "PAUSED"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = palette.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.surfaceColor)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(999.dp))
                    .clickable { onToggleLang() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEs) "ES" else "EN",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textPrimary
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceColor)
                    .border(1.dp, palette.borderColor, CircleShape)
                    .clickable { onToggleTheme() },
                contentAlignment = Alignment.Center
            ) {
                ThemeGeometricIcon(
                    isDark = isDark,
                    tint = palette.textPrimary
                )
            }
        }
    }
}

@Composable
private fun ServiceOnboardingBanner(
    isEs: Boolean,
    onOpenAccessibilitySettings: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceColor)
            .border(1.dp, palette.danger.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(palette.danger)
            )
            Text(
                text = if (isEs) "REQUIERE SERVICIO DE ACCESIBILIDAD" else "ACCESSIBILITY SERVICE REQUIRED",
                style = MaterialTheme.typography.labelMedium,
                color = palette.danger
            )
        }
        Text(
            text = if (isEs) {
                "Para distinguir entre un mensaje directo y la pestaña de Reels o Shorts sin acceso root, activá Scrolless en Accesibilidad."
            } else {
                "To distinguish between a direct message and the Reels or Shorts tab without root access, enable Scrolless in Accessibility."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.textPrimary)
                .clickable { onOpenAccessibilitySettings() }
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Text(
                text = if (isEs) "Vincular servicio ahora" else "Bind service now",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = palette.bgColor
            )
        }
    }
}

@Composable
private fun TimeBudgetHeroCard(
    config: ProtectionConfig,
    runtime: RuntimeState,
    isEs: Boolean,
    accentColor: Color
) {
    val palette = LocalPlayaPalette.current
    val usageRatio = SessionController.usageRatio(config, runtime)
    val remainingSeconds = SessionController.remainingSeconds(config, runtime)
    val animatedProgress by animateFloatAsState(
        targetValue = usageRatio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "dialProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.surfaceColor)
            .border(1.dp, palette.borderColor, RoundedCornerShape(24.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (config.mode == ProtectionMode.SURGICAL_BLOCK) {
                        if (isEs) "MODO BLOQUEO TOTAL" else "ZERO SCROLL MODE"
                    } else {
                        if (isEs) "PRESUPUESTO EN PESTAÑA HOY" else "TODAY'S TAB ALLOWANCE"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (config.mode == ProtectionMode.SURGICAL_BLOCK) palette.danger else palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatMinSec(remainingSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    color = palette.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = if (config.mode == ProtectionMode.SURGICAL_BLOCK) {
                        if (isEs) "0s permitidos en Reels, Shorts y TikTok" else "0s allowed in Reels, Shorts & TikTok"
                    } else {
                        if (isEs) {
                            "${formatMinSec(runtime.activeSecondsToday)} consumidos de ${formatMinSec(config.dailyLimitSeconds)}"
                        } else {
                            "${formatMinSec(runtime.activeSecondsToday)} used of ${formatMinSec(config.dailyLimitSeconds)}"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Custom Architectural Radial Dial
            Box(
                modifier = Modifier.size(92.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 4.5.dp.toPx()
                    val radius = (size.minDimension - strokeWidth * 2) / 2f
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)

                    // Subtle 24 architectural tick marks around perimeter
                    val tickCount = 24
                    for (i in 0 until tickCount) {
                        val angleRad = Math.toRadians((i * (360.0 / tickCount)) - 90.0)
                        val innerR = radius + 3.dp.toPx()
                        val outerR = radius + 6.5.dp.toPx()
                        val start = Offset(
                            x = centerOffset.x + (innerR * cos(angleRad)).toFloat(),
                            y = centerOffset.y + (innerR * sin(angleRad)).toFloat()
                        )
                        val end = Offset(
                            x = centerOffset.x + (outerR * cos(angleRad)).toFloat(),
                            y = centerOffset.y + (outerR * sin(angleRad)).toFloat()
                        )
                        drawLine(
                            color = palette.borderColor,
                            start = start,
                            end = end,
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Track circle
                    drawCircle(
                        color = palette.borderColor,
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = strokeWidth)
                    )

                    // Active arc
                    val sweep = if (config.mode == ProtectionMode.SURGICAL_BLOCK) {
                        360f
                    } else {
                        (1f - animatedProgress) * 360f
                    }
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val pctRemaining = if (config.mode == ProtectionMode.SURGICAL_BLOCK) {
                        0
                    } else {
                        ((1f - usageRatio) * 100).toInt().coerceIn(0, 100)
                    }
                    Text(
                        text = if (config.mode == ProtectionMode.SURGICAL_BLOCK) "0%" else "$pctRemaining%",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (isEs) "libre" else "left",
                        fontSize = 10.sp,
                        color = palette.textSecondary
                    )
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = palette.borderColor)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEs) "El reloj se pausa al salir a DMs o videos largos" else "Timer pauses immediately in DMs or long videos",
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}

@Composable
private fun StreakAndMetricsBento(
    state: RuntimeState,
    isEs: Boolean
) {
    val palette = LocalPlayaPalette.current
    val daysLabels = if (isEs) {
        listOf("L", "M", "X", "J", "V", "S", "D")
    } else {
        listOf("M", "T", "W", "T", "F", "S", "S")
    }
    val completedInCycle = (state.streakDays % 7).let { if (it == 0 && state.streakDays > 0) 7 else it }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Full-width Streak Rhythm Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surfaceColor)
                .border(1.dp, palette.borderColor, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isEs) "CONSTANCIA DIARIA" else "DAILY CONSISTENCY",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isEs) {
                            if (state.streakDays == 1) "1 día" else "${state.streakDays} días"
                        } else {
                            if (state.streakDays == 1) "1 day" else "${state.streakDays} days"
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isEs) "cumpliendo tu margen diario" else "meeting your daily budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Shield tokens (compact pill with ${state.streakShields}/3)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.bgColor)
                        .border(1.dp, palette.borderColor, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    for (i in 1..3) {
                        ShieldGeometricIcon(
                            filled = i <= state.streakShields,
                            activeColor = palette.success,
                            inactiveColor = palette.borderColor
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${state.streakShields}/3",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = palette.textSecondary
                    )
                }
            }

            // 7-day tactile rhythm bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                daysLabels.forEachIndexed { index, label ->
                    val isFilled = index < completedInCycle
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFilled) palette.success.copy(alpha = 0.16f) else palette.bgColor)
                            .border(
                                width = 1.dp,
                                color = if (isFilled) palette.success else palette.borderColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) palette.success else palette.borderColor)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                            color = if (isFilled) palette.textPrimary else palette.textSecondary
                        )
                    }
                }
            }
        }

        // 2-Column Bento Row: Impulses Stopped & measured short-form time (real history, no estimates)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                eyebrow = if (isEs) "IMPULSOS HOY" else "IMPULSES TODAY",
                bigValue = "${state.interceptionsToday}",
                caption = if (isEs) "aperturas automáticas cortadas hoy" else "automatic opens caught today",
                accentDotColor = palette.warning
            )
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                eyebrow = if (isEs) "EN REELS · 7 DÍAS" else "SHORT-FORM · 7 DAYS",
                bigValue = "${SessionController.secondsLast7Days(state) / 60} min",
                caption = if (isEs) "tiempo real medido en formato corto" else "measured time in short-form feeds",
                accentDotColor = palette.success
            )
        }
    }
}

@Composable
private fun BentoMetricTile(
    modifier: Modifier = Modifier,
    eyebrow: String,
    bigValue: String,
    caption: String,
    accentDotColor: Color
) {
    val palette = LocalPlayaPalette.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceColor)
            .border(1.dp, palette.borderColor, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = eyebrow,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentDotColor)
                )
            }
            Text(
                text = bigValue,
                style = MaterialTheme.typography.headlineLarge,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModeSelectorStack(
    state: ProtectionConfig,
    isEs: Boolean,
    onSelectMode: (ProtectionMode) -> Unit
) {
    val palette = LocalPlayaPalette.current
    val is15mSelected = state.mode == ProtectionMode.MINDFUL_BUDGET && state.dailyLimitSeconds == 15 * 60
    val is5mSelected = state.mode == ProtectionMode.STRICT_BUDGET && state.dailyLimitSeconds == 5 * 60
    val is0mSelected = state.mode == ProtectionMode.SURGICAL_BLOCK || state.dailyLimitSeconds == 0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModeOptionCard(
            title = if (isEs) "Pausa Consciente · 15 min" else "Mindful Budget · 15 min",
            subtitle = if (isEs) {
                "Respiración de 10s antes de entrar y hasta 15 min diarios solo en pestaña."
            } else {
                "10s breathing pause before entry and 15 min daily tab budget."
            },
            badge = "15M",
            accentColor = palette.success,
            selected = is15mSelected,
            onClick = { onSelectMode(ProtectionMode.MINDFUL_BUDGET) }
        )
        ModeOptionCard(
            title = if (isEs) "Margen Estricto · 5 min" else "Strict Budget · 5 min",
            subtitle = if (isEs) {
                "Para días de foco profundo: solo 5 minutos acumulados en Reels o Shorts."
            } else {
                "For deep focus days: maximum 5 cumulative minutes in Reels or Shorts."
            },
            badge = "5M",
            accentColor = palette.warning,
            selected = is5mSelected,
            onClick = { onSelectMode(ProtectionMode.STRICT_BUDGET) }
        )
        ModeOptionCard(
            title = if (isEs) "Bloqueo Total · 0 min" else "Zero Scroll · 0 min",
            subtitle = if (isEs) {
                "Corta cualquier Reel, Short o TikTok en el primer segundo sin desactivar tus chats."
            } else {
                "Intercepts any Reel, Short, or TikTok at second zero while keeping chats open."
            },
            badge = "0M",
            accentColor = palette.danger,
            selected = is0mSelected,
            onClick = { onSelectMode(ProtectionMode.SURGICAL_BLOCK) }
        )
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    subtitle: String,
    badge: String,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) palette.surfaceSubtle else palette.surfaceColor)
            .border(
                width = 1.dp,
                color = if (selected) accentColor else palette.borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) accentColor else palette.borderColor)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) accentColor.copy(alpha = 0.2f) else palette.bgColor)
                .border(
                    width = 1.dp,
                    color = if (selected) accentColor else palette.borderColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accentColor else palette.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CustomMinutesCard(
    currentMinutes: Int,
    isCustomSelected: Boolean,
    isEs: Boolean,
    onApplyMinutes: (Int) -> Unit
) {
    val palette = LocalPlayaPalette.current
    var inputText by remember(currentMinutes) { mutableStateOf(currentMinutes.toString()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceColor)
            .border(
                1.dp,
                if (isCustomSelected) palette.success else palette.borderColor,
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = if (isEs) "Límite personalizado" else "Custom daily limit",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isEs) "Minutos al día (0 = bloqueo total)" else "Minutes/day (0 = zero-scroll)",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .width(68.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.bgColor)
                .border(
                    1.dp,
                    if (isCustomSelected) palette.success else palette.borderColor,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() }.take(3)
                    inputText = filtered
                },
                singleLine = true,
                cursorBrush = SolidColor(palette.textPrimary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = palette.textPrimary
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val mins = inputText.toIntOrNull()
                        if (mins != null && mins in 0..300) {
                            onApplyMinutes(mins)
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.textPrimary)
                .clickable {
                    val mins = inputText.toIntOrNull()
                    if (mins != null && mins in 0..300) {
                        onApplyMinutes(mins)
                    }
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isEs) "Aplicar" else "Apply",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = palette.bgColor
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SurgicalTargetsPanel(
    enabledTargets: Set<ShortFormTarget>,
    fullAppTargets: Set<ShortFormTarget>,
    isEs: Boolean,
    onToggleTarget: (ShortFormTarget) -> Unit,
    onSetScope: (ShortFormTarget, AppBlockScope) -> Unit
) {
    val igEnabled = ShortFormTarget.INSTAGRAM_REELS in enabledTargets
    val igFull = ShortFormTarget.INSTAGRAM_REELS in fullAppTargets
    val ytEnabled = ShortFormTarget.YOUTUBE_SHORTS in enabledTargets
    val ytFull = ShortFormTarget.YOUTUBE_SHORTS in fullAppTargets
    val palette = LocalPlayaPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceColor)
            .border(1.dp, palette.borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        SurgicalTargetRow(
            appTitle = if (igEnabled && igFull) "Instagram" else "Instagram Reels",
            allowedScope = when {
                igEnabled && igFull ->
                    if (isEs) "Bloquea Instagram entero, también DMs e historias" else "Blocks all of Instagram, including DMs and stories"
                else -> if (isEs) "DMs, historias y feed libres" else "DMs, stories & feed unrestricted"
            },
            enabled = igEnabled,
            onToggle = { onToggleTarget(ShortFormTarget.INSTAGRAM_REELS) }
        )
        if (igEnabled) {
            ScopeSegmentedControl(
                surgicalLabel = if (isEs) "Solo Reels" else "Reels only",
                fullAppLabel = if (isEs) "App completa" else "Whole app",
                fullApp = igFull,
                onSelect = { scope -> onSetScope(ShortFormTarget.INSTAGRAM_REELS, scope) }
            )
        }
        HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
        SurgicalTargetRow(
            appTitle = if (ytEnabled && ytFull) "YouTube" else "YouTube Shorts",
            allowedScope = when {
                ytEnabled && ytFull ->
                    if (isEs) "Bloquea YouTube entero, también videos largos" else "Blocks all of YouTube, including long videos"
                else -> if (isEs) "Videos largos y búsquedas libres" else "Long-form videos & search unrestricted"
            },
            enabled = ytEnabled,
            onToggle = { onToggleTarget(ShortFormTarget.YOUTUBE_SHORTS) }
        )
        if (ytEnabled) {
            ScopeSegmentedControl(
                surgicalLabel = if (isEs) "Solo Shorts" else "Shorts only",
                fullAppLabel = if (isEs) "App completa" else "Whole app",
                fullApp = ytFull,
                onSelect = { scope -> onSetScope(ShortFormTarget.YOUTUBE_SHORTS, scope) }
            )
        }
        HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
        SurgicalTargetRow(
            appTitle = "TikTok",
            allowedScope = if (isEs) "Intervención en toda la aplicación" else "Full application short-form scope",
            enabled = ShortFormTarget.TIKTOK in enabledTargets,
            onToggle = { onToggleTarget(ShortFormTarget.TIKTOK) }
        )
    }
}

@Composable
private fun SurgicalTargetRow(
    appTitle: String,
    allowedScope: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    val thumbOffset by animateDpAsState(
        targetValue = if (enabled) 23.dp else 3.dp,
        animationSpec = tween(durationMillis = 200),
        label = "switchThumb"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = appTitle,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = allowedScope,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (enabled) palette.success else palette.bgColor)
                .border(
                    width = 1.dp,
                    color = if (enabled) palette.success else palette.borderColor,
                    shape = RoundedCornerShape(999.dp)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(start = thumbOffset)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (enabled) palette.bgColor else palette.textSecondary)
            )
        }
    }
}

/** Compact two-segment scope selector: surgical (only the short-form tab) vs. the whole app. */
@Composable
private fun ScopeSegmentedControl(
    surgicalLabel: String,
    fullAppLabel: String,
    fullApp: Boolean,
    onSelect: (AppBlockScope) -> Unit
) {
    val palette = LocalPlayaPalette.current
    Row(
        modifier = Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bgColor)
            .border(1.dp, palette.borderColor, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ScopeSegment(
            modifier = Modifier.weight(1f),
            label = surgicalLabel,
            selected = !fullApp,
            onClick = { onSelect(AppBlockScope.SURGICAL) }
        )
        ScopeSegment(
            modifier = Modifier.weight(1f),
            label = fullAppLabel,
            selected = fullApp,
            onClick = { onSelect(AppBlockScope.FULL_APP) }
        )
    }
}

@Composable
private fun ScopeSegment(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) palette.textPrimary else Color.Transparent)
            .clickable(enabled = !selected) { onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = if (selected) palette.bgColor else palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    val palette = LocalPlayaPalette.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = palette.textSecondary,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
private fun ThemeGeometricIcon(isDark: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        if (isDark) {
            // Minimal geometric sun
            drawCircle(
                color = tint,
                radius = 3.5.dp.toPx(),
                center = center,
                style = Stroke(width = 1.4.dp.toPx())
            )
            for (i in 0 until 8) {
                val angle = Math.toRadians(i * 45.0)
                val r1 = 5.5.dp.toPx()
                val r2 = 7.5.dp.toPx()
                drawLine(
                    color = tint,
                    start = Offset(center.x + (r1 * cos(angle)).toFloat(), center.y + (r1 * sin(angle)).toFloat()),
                    end = Offset(center.x + (r2 * cos(angle)).toFloat(), center.y + (r2 * sin(angle)).toFloat()),
                    strokeWidth = 1.3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        } else {
            // Minimal geometric crescent/circle eclipse
            drawCircle(
                color = tint,
                radius = 6.dp.toPx(),
                center = center,
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawCircle(
                color = tint,
                radius = 2.2.dp.toPx(),
                center = Offset(center.x + 2.dp.toPx(), center.y - 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun ShieldGeometricIcon(
    filled: Boolean,
    activeColor: Color,
    inactiveColor: Color
) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.9f, h * 0.25f)
            lineTo(w * 0.82f, h * 0.68f)
            lineTo(w * 0.5f, h * 0.95f)
            lineTo(w * 0.18f, h * 0.68f)
            lineTo(w * 0.1f, h * 0.25f)
            close()
        }
        if (filled) {
            drawPath(path = path, color = activeColor)
        } else {
            drawPath(path = path, color = inactiveColor, style = Stroke(width = 1.2.dp.toPx()))
        }
    }
}

fun formatMinSec(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%dm %02ds".format(mins, secs)
}
