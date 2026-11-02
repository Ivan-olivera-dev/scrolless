package com.scrolless.app.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.SessionAndStreakManager
import com.scrolless.app.core.SessionAndStreakState

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
    var previewLiveWhenDisconnected by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgColor),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp, vertical = 40.dp)
        ) {
            // Top-right controls matching .top-controls in playaperello
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEs) "EN" else "ES",
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onToggleLang() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = if (isDark) "☀" else "☾",
                    color = palette.textSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable { onToggleTheme() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Header matching .header h1 & p
            Text(
                text = "Scrolless",
                style = MaterialTheme.typography.headlineLarge,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isEs) "ESCUDOS QUIRÚRGICOS DE ATENCIÓN" else "SURGICAL ATTENTION SHIELD",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (!isServiceActive && !previewLiveWhenDisconnected) {
                // Offseason-card equivalent when Accessibility Service is not yet active
                HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
                Column(
                    modifier = Modifier.padding(vertical = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (isEs) "SERVICIO DESCONECTADO · SIN PERMISOS DE RED" else "SERVICE INACTIVE · ZERO NETWORK PERMISSIONS",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = palette.textSecondary
                    )
                    Text(
                        text = if (isEs) "Activá el bloqueo quirúrgico" else "Enable surgical protection",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (isEs) {
                            "Scrolless detecta únicamente la pestaña de Reels, Shorts y TikTok sin tocar tus mensajes ni videos largos. El manifiesto no declara permiso de Internet: nada sale de tu teléfono."
                        } else {
                            "Scrolless detects only Reels, Shorts, and TikTok tabs without touching your DMs or long videos. No Internet permission is declared in the manifest."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    PillButton(
                        label = if (isEs) "Activar en Accesibilidad →" else "Enable in Accessibility →",
                        onClick = onOpenAccessibilitySettings
                    )
                    Text(
                        text = if (isEs) "Ver panel de métricas →" else "Preview metrics dashboard →",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        modifier = Modifier.clickable { previewLiveWhenDisconnected = true }
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
            } else {
                if (!isServiceActive) {
                    Text(
                        text = if (isEs) "← Volver a aviso de activación" else "← Back to activation notice",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .clickable { previewLiveWhenDisconnected = false }
                    )
                }

                // Flag-prediction equivalent: current protection state & semantic dot
                val dotColor = when (state.mode) {
                    ProtectionMode.MINDFUL_BUDGET -> palette.success
                    ProtectionMode.STRICT_BUDGET -> palette.warning
                    ProtectionMode.SURGICAL_BLOCK -> palette.danger
                }
                val modeTitle = when (state.mode) {
                    ProtectionMode.MINDFUL_BUDGET -> if (isEs) "Pausa + 15m" else "Mindful 15m"
                    ProtectionMode.STRICT_BUDGET -> if (isEs) "Estricto 5m" else "Strict 5m"
                    ProtectionMode.SURGICAL_BLOCK -> if (isEs) "Bloqueo Total" else "Zero Scroll"
                }
                val modeReason = when (state.mode) {
                    ProtectionMode.MINDFUL_BUDGET -> if (isEs) "Cuenta solo dentro de la pestaña de Reels/Shorts" else "Counts only while inside Reels/Shorts tab"
                    ProtectionMode.STRICT_BUDGET -> if (isEs) "Margen reducido de 5 min diarios en pestaña" else "Tight 5 min daily tab allowance"
                    ProtectionMode.SURGICAL_BLOCK -> if (isEs) "Interrupción inmediata al abrir cualquier Reel o Short" else "Immediate interruption on any Reel or Short"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isEs) "Nivel de escudo" else "Shield status",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Light,
                            color = palette.textSecondary
                        )
                        Text(
                            text = modeReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary.copy(alpha = 0.8f)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = modeTitle,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = palette.textPrimary
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
                Spacer(modifier = Modifier.height(10.dp))

                // Metrics list matching .metrics-list and .metric-row
                MetricRow(
                    label = if (isEs) "Racha actual" else "Current streak",
                    value = if (isEs) "${state.streakDays} días (${state.streakShields} escudos)" else "${state.streakDays} days (${state.streakShields} shields)"
                )
                MetricRow(
                    label = if (isEs) "Tiempo en pestaña hoy" else "Tab time today",
                    value = if (state.mode == ProtectionMode.SURGICAL_BLOCK) {
                        if (isEs) "0m 00s (Bloqueado)" else "0m 00s (Blocked)"
                    } else {
                        "${formatMinSec(state.activeReelSecondsToday)} / ${formatMinSec(state.dailyLimitSeconds)}"
                    },
                    valueColor = when {
                        state.usageRatio < 0.5f -> palette.success
                        state.usageRatio < 0.9f -> palette.warning
                        else -> palette.danger
                    }
                )
                MetricRow(
                    label = if (isEs) "Impulsos frenados hoy" else "Impulses stopped today",
                    value = "${state.interceptionsToday}"
                )
                MetricRow(
                    label = if (isEs) "Tiempo recuperado (7d)" else "Recovered time (7d)",
                    value = "${SessionAndStreakManager.estimateSavedHours(state.weeklyInterceptions)} h"
                )
                MetricRow(
                    label = if (isEs) "Secciones vigiladas" else "Surgical targets",
                    value = "Reels · Shorts · TikTok"
                )

                Spacer(modifier = Modifier.height(44.dp))

                // Mode selector matching .social-votes and .vote-btn circles
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isEs) "MODO DE INTERVENCIÓN" else "INTERVENTION MODE",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModeCircleButton(
                            dotColor = palette.success,
                            selected = state.mode == ProtectionMode.MINDFUL_BUDGET,
                            onClick = { onSelectMode(ProtectionMode.MINDFUL_BUDGET) }
                        )
                        ModeCircleButton(
                            dotColor = palette.warning,
                            selected = state.mode == ProtectionMode.STRICT_BUDGET,
                            onClick = { onSelectMode(ProtectionMode.STRICT_BUDGET) }
                        )
                        ModeCircleButton(
                            dotColor = palette.danger,
                            selected = state.mode == ProtectionMode.SURGICAL_BLOCK,
                            onClick = { onSelectMode(ProtectionMode.SURGICAL_BLOCK) }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(color = palette.success, text = "15m")
                        LegendItem(color = palette.warning, text = "5m")
                        LegendItem(color = palette.danger, text = "0m")
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
            HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
            Spacer(modifier = Modifier.height(20.dp))

            // Open Source Verification & View ID Inspector footer
            Text(
                text = if (isEs) {
                    "OPEN SOURCE · 0 PERMISOS DE RED · BUILD REPRODUCIBLE"
                } else {
                    "OPEN SOURCE · 0 NETWORK PERMISSIONS · REPRODUCIBLE BUILD"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = palette.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (inspectorEnabled) {
                    if (isEs) "Ocultar inspector de View IDs ↑" else "Hide View ID inspector ↑"
                } else {
                    if (isEs) "Abrir inspector de View IDs para colaboradores →" else "Open View ID inspector for contributors →"
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.clickable { onToggleInspector() }
            )
            if (inspectorEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                val idsText = if (inspectedIds.isEmpty()) {
                    if (isEs) "Abrí Instagram o YouTube con el servicio activo para capturar los IDs del nodo actual."
                    else "Open Instagram or YouTube with the service enabled to inspect live view IDs."
                } else {
                    inspectedIds.joinToString("\n")
                }
                Text(
                    text = idsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: Color = LocalPlayaPalette.current.textPrimary
) {
    val palette = LocalPlayaPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor
            )
        }
        HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
    }
}

@Composable
private fun ModeCircleButton(
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPlayaPalette.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = if (selected) palette.textPrimary else palette.borderColor,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 15.dp else 12.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    val palette = LocalPlayaPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "•", color = color, fontSize = 18.sp)
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    }
}

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val palette = LocalPlayaPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(
                width = 1.dp,
                color = if (enabled) palette.borderColor else palette.borderColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = if (enabled) palette.textPrimary else palette.textSecondary.copy(alpha = 0.5f)
        )
    }
}

fun formatMinSec(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%dm %02ds".format(mins, secs)
}
