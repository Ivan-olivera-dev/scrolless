package com.scrolless.app.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.SessionAndStreakManager
import com.scrolless.app.core.SessionAndStreakState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DashboardScreen(
    state: SessionAndStreakState,
    isServiceActive: Boolean,
    lang: String,
    isDark: Boolean,
    inspectorEnabled: Boolean,
    inspectedIds: List<String>,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectMode: (ProtectionMode) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleInspector: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    val isEs = lang == "es"
    val accentColor = when (state.mode) {
        ProtectionMode.MINDFUL_BUDGET -> palette.success
        ProtectionMode.STRICT_BUDGET -> palette.warning
        ProtectionMode.SURGICAL_BLOCK -> palette.danger
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgColor),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 32.dp),
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
                state = state,
                isEs = isEs,
                accentColor = accentColor
            )

            // 4. Flat Bento Grid: Streak Monument + Impulses & Recovered Hours
            StreakAndMetricsBento(
                state = state,
                isEs = isEs
            )

            // 5. Tactile Intervention Mode Selector (3 Architectural Cards)
            SectionHeader(
                title = if (isEs) "PROTOCOLO DE INTERVENCIÓN" else "INTERVENTION PROTOCOL"
            )
            ModeSelectorStack(
                selectedMode = state.mode,
                isEs = isEs,
                onSelectMode = onSelectMode
            )

            // 6. Surgical Target Matrix (Per-Platform Scope)
            SectionHeader(
                title = if (isEs) "COBERTURA QUIRÚRGICA ACTIVA" else "ACTIVE SURGICAL TARGETS"
            )
            SurgicalTargetsPanel(isEs = isEs)

            // 7. Zero-Network Kernel Proof & Live Node Inspector
            SecurityAndInspectorFooter(
                isEs = isEs,
                inspectorEnabled = inspectorEnabled,
                inspectedIds = inspectedIds,
                onToggleInspector = onToggleInspector
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                        color = palette.textSecondary
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
    state: SessionAndStreakState,
    isEs: Boolean,
    accentColor: Color
) {
    val palette = LocalPlayaPalette.current
    val animatedProgress by animateFloatAsState(
        targetValue = state.usageRatio.coerceIn(0f, 1f),
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isEs) "PRESUPUESTO EN PESTAÑA HOY" else "TODAY'S TAB ALLOWANCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
                Text(
                    text = if (state.mode == ProtectionMode.SURGICAL_BLOCK) {
                        if (isEs) "Bloqueo Total" else "Zero Scroll"
                    } else {
                        formatMinSec(state.remainingSecondsToday)
                    },
                    style = MaterialTheme.typography.displayLarge,
                    color = palette.textPrimary
                )
                Text(
                    text = if (state.mode == ProtectionMode.SURGICAL_BLOCK) {
                        if (isEs) "0s permitidos en Reels, Shorts y TikTok" else "0s allowed in Reels, Shorts & TikTok"
                    } else {
                        if (isEs) {
                            "${formatMinSec(state.activeReelSecondsToday)} consumidos de ${formatMinSec(state.dailyLimitSeconds)}"
                        } else {
                            "${formatMinSec(state.activeReelSecondsToday)} used of ${formatMinSec(state.dailyLimitSeconds)}"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }

            // Custom Architectural Radial Dial
            Box(
                modifier = Modifier.size(108.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 5.dp.toPx()
                    val radius = (size.minDimension - strokeWidth * 2) / 2f
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)

                    // Subtle 24 architectural tick marks around perimeter
                    val tickCount = 24
                    for (i in 0 until tickCount) {
                        val angleRad = Math.toRadians((i * (360.0 / tickCount)) - 90.0)
                        val innerR = radius + 4.dp.toPx()
                        val outerR = radius + 8.dp.toPx()
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
                            strokeWidth = 1.2.dp.toPx(),
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
                    val sweep = if (state.mode == ProtectionMode.SURGICAL_BLOCK) {
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
                    val pctRemaining = if (state.mode == ProtectionMode.SURGICAL_BLOCK) {
                        0
                    } else {
                        ((1f - state.usageRatio) * 100).toInt().coerceIn(0, 100)
                    }
                    Text(
                        text = if (state.mode == ProtectionMode.SURGICAL_BLOCK) "0%" else "$pctRemaining%",
                        fontSize = 18.sp,
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
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
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
    state: SessionAndStreakState,
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isEs) "CONSTANCIA DIARIA" else "DAILY CONSISTENCY",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${state.streakDays}",
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                            color = palette.textPrimary
                        )
                        Text(
                            text = if (isEs) "días cumpliendo tu margen" else "days within your budget",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                }

                // Shield tokens (up to 3 geometric shields)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        text = if (isEs) "${state.streakShields} escudos" else "${state.streakShields} shields",
                        style = MaterialTheme.typography.bodySmall,
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

        // 2-Column Bento Row: Impulses Stopped & Recovered Time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                eyebrow = if (isEs) "IMPULSOS FRENADOS" else "IMPULSES STOPPED",
                bigValue = "${state.interceptionsToday}",
                caption = if (isEs) "aperturas automáticas cortadas hoy" else "automatic opens caught today",
                accentDotColor = palette.warning
            )
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                eyebrow = if (isEs) "TIEMPO RECUPERADO" else "TIME RECOVERED",
                bigValue = "${SessionAndStreakManager.estimateSavedHours(state.weeklyInterceptions)} h",
                caption = if (isEs) "estimado en los últimos 7 días" else "estimated over the last 7 days",
                accentDotColor = palette.success
            )
        }
    }
}

@Composable
private fun BentoMetricTile(
    modifier: Modifier,
    eyebrow: String,
    bigValue: String,
    caption: String,
    accentDotColor: Color
) {
    val palette = LocalPlayaPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceColor)
            .border(1.dp, palette.borderColor, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = palette.textSecondary
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
            color = palette.textPrimary
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun ModeSelectorStack(
    selectedMode: ProtectionMode,
    isEs: Boolean,
    onSelectMode: (ProtectionMode) -> Unit
) {
    val palette = LocalPlayaPalette.current
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
            selected = selectedMode == ProtectionMode.MINDFUL_BUDGET,
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
            selected = selectedMode == ProtectionMode.STRICT_BUDGET,
            onClick = { onSelectMode(ProtectionMode.STRICT_BUDGET) }
        )
        ModeOptionCard(
            title = if (isEs) "Bloqueo Quirúrgico Total · 0 min" else "Zero-Scroll Surgical Block · 0 min",
            subtitle = if (isEs) {
                "Corta cualquier Reel, Short o TikTok en el primer segundo sin desactivar tus chats."
            } else {
                "Intercepts any Reel, Short, or TikTok at second zero while keeping chats open."
            },
            badge = "0M",
            accentColor = palette.danger,
            selected = selectedMode == ProtectionMode.SURGICAL_BLOCK,
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
                color = palette.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
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
                color = if (selected) accentColor else palette.textSecondary
            )
        }
    }
}

@Composable
private fun SurgicalTargetsPanel(isEs: Boolean) {
    val palette = LocalPlayaPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceColor)
            .border(1.dp, palette.borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        SurgicalTargetRow(
            appTitle = "Instagram",
            allowedScope = if (isEs) "DMs, historias y feed libres" else "DMs, stories & feed unrestricted",
            blockedChip = "Reels Tab"
        )
        HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
        SurgicalTargetRow(
            appTitle = "YouTube",
            allowedScope = if (isEs) "Videos largos y búsquedas libres" else "Long-form videos & search unrestricted",
            blockedChip = "Shorts Player"
        )
        HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
        SurgicalTargetRow(
            appTitle = "TikTok",
            allowedScope = if (isEs) "Intervención en toda la aplicación" else "Full application short-form scope",
            blockedChip = "Full App"
        )
    }
}

@Composable
private fun SurgicalTargetRow(
    appTitle: String,
    allowedScope: String,
    blockedChip: String
) {
    val palette = LocalPlayaPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = appTitle,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = palette.textPrimary
            )
            Text(
                text = allowedScope,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(palette.bgColor)
                .border(1.dp, palette.borderColor, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = blockedChip,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = palette.textPrimary
            )
        }
    }
}

@Composable
private fun SecurityAndInspectorFooter(
    isEs: Boolean,
    inspectorEnabled: Boolean,
    inspectedIds: List<String>,
    onToggleInspector: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceColor)
            .border(1.dp, palette.borderColor, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEs) "GARANTÍA CERO-RED · OPEN SOURCE" else "ZERO-NETWORK GUARANTEE · OPEN SOURCE",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = palette.success
            )
            Text(
                text = "SHA-256",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = palette.textSecondary
            )
        }
        Text(
            text = if (isEs) {
                "Compilado sin permiso android.permission.INTERNET. Ningún byte puede abandonar tu dispositivo."
            } else {
                "Compiled without android.permission.INTERNET. Zero bytes can leave your device."
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
        Text(
            text = if (inspectorEnabled) {
                if (isEs) "Ocultar inspector de nodos en vivo" else "Hide live node inspector"
            } else {
                if (isEs) "Inspeccionar View IDs en vivo (colaboradores)" else "Inspect live View IDs (contributors)"
            },
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = palette.textPrimary,
            modifier = Modifier.clickable { onToggleInspector() }
        )

        if (inspectorEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.bgColor)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                val idsText = if (inspectedIds.isEmpty()) {
                    if (isEs) "Abrí Instagram o YouTube para capturar los resource-id activos."
                    else "Open Instagram or YouTube to capture active resource-id nodes."
                } else {
                    inspectedIds.joinToString("\n")
                }
                Text(
                    text = idsText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = palette.textSecondary
                )
            }
        }
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
