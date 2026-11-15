package com.scrolless.app.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

enum class ProtectionMode(val defaultDailyLimitSeconds: Int) {
    MINDFUL_BUDGET(15 * 60), // 15 min/day only on Reels/Shorts tab + mindful pause
    STRICT_BUDGET(5 * 60),   // 5 min/day only on Reels/Shorts tab + mindful pause
    SURGICAL_BLOCK(0)        // 0 sec allowed on Reels/Shorts/TikTok
}

enum class InterruptionReason {
    MINDFUL_PAUSE_ON_ENTRY,
    DAILY_LIMIT_EXHAUSTED,
    SURGICAL_ZERO_TOLERANCE
}

/** User configuration. Written only by the dashboard (MainActivity). */
data class ProtectionConfig(
    val mode: ProtectionMode = ProtectionMode.MINDFUL_BUDGET,
    val dailyLimitSeconds: Int = ProtectionMode.MINDFUL_BUDGET.defaultDailyLimitSeconds,
    val enabledTargets: Set<ShortFormTarget> = ShortFormTarget.entries.toSet(),
    val fullAppTargets: Set<ShortFormTarget> = emptySet()
) {
    fun scopeOf(target: ShortFormTarget): AppBlockScope =
        if (target == ShortFormTarget.TIKTOK || target in fullAppTargets) AppBlockScope.FULL_APP else AppBlockScope.SURGICAL
}

data class DayRecord(val dateIso: String, val seconds: Int, val interceptions: Int)

/** Runtime counters and session state. Written only by the accessibility service (or its fallback). */
data class RuntimeState(
    val dayIso: String = "",
    val activeTarget: ShortFormTarget? = null,
    val lastPositiveMs: Long = 0L,
    val lastTickMs: Long = 0L,
    val pendingMs: Long = 0L,
    val activeSecondsToday: Int = 0,
    val interceptionsToday: Int = 0,
    val history: List<DayRecord> = emptyList(),
    val streakDays: Int = 0,
    val streakShields: Int = 0,
    val passTarget: ShortFormTarget? = null,
    val passUntilMs: Long = 0L,
    val lastInterruptionTarget: ShortFormTarget? = null,
    val lastInterruptionMs: Long = 0L,
    val lastLaunchTarget: ShortFormTarget? = null,
    val lastLaunchMs: Long = 0L
)

sealed class Decision {
    data object Allow : Decision()
    data class Interrupt(
        val target: ShortFormTarget,
        val reason: InterruptionReason,
        val canContinue: Boolean,
        val remainingSecondsToday: Int,
        val shouldLaunch: Boolean
    ) : Decision()
}

data class Step(val state: RuntimeState, val decision: Decision)

/**
 * Pure session controller (no Android imports): tab-only time accounting, mindful passes,
 * interruption de-duplication and non-punitive daily streaks with shields.
 */
object SessionController {
    const val TRANSIENT_GRACE_MS = 1_500L
    const val STALE_POSITIVE_MS = 3_000L
    const val MAX_TICK_DELTA_MS = 2_000L
    const val IMPULSE_DEDUPE_MS = 10_000L
    const val LAUNCH_DEDUPE_MS = 1_500L
    const val MIN_PASS_SECONDS = 180L
    const val PASS_EXTRA_SECONDS = 60L
    const val MAX_SHIELDS = 3
    const val HISTORY_DAYS = 7
    private const val MAX_SKIPPED_DAYS = 366L

    fun remainingSeconds(config: ProtectionConfig, state: RuntimeState): Int =
        max(0, config.dailyLimitSeconds - state.activeSecondsToday)

    fun usageRatio(config: ProtectionConfig, state: RuntimeState): Float =
        if (config.dailyLimitSeconds <= 0) 1f else min(1f, state.activeSecondsToday.toFloat() / config.dailyLimitSeconds)

    private fun isZeroTolerance(config: ProtectionConfig) =
        config.mode == ProtectionMode.SURGICAL_BLOCK || config.dailyLimitSeconds <= 0

    private fun stop(state: RuntimeState) = state.copy(activeTarget = null)

    private fun clearPass(state: RuntimeState) = state.copy(passTarget = null, passUntilMs = 0L)

    private fun hasValidPass(state: RuntimeState, target: ShortFormTarget, nowMs: Long) =
        state.passTarget == target && nowMs < state.passUntilMs

    /** R1-R3: a detection of the active window. */
    fun onDetection(state: RuntimeState, config: ProtectionConfig, detection: Detection, nowMs: Long): Step {
        val target = detection.target
        if (target == null || target !in config.enabledTargets) {
            // R3: negative
            val active = state.activeTarget ?: return Step(state, Decision.Allow)
            val leftApp = detection.watchedTarget != active
            val graceOver = nowMs - state.lastPositiveMs >= TRANSIENT_GRACE_MS
            return Step(if (leftApp || graceOver) stop(state) else state, Decision.Allow)
        }

        // R1: still watching the active target
        if (state.activeTarget == target) {
            return Step(state.copy(lastPositiveMs = nowMs), Decision.Allow)
        }

        // R2: entering a target
        return when {
            isZeroTolerance(config) ->
                interrupt(state, config, target, InterruptionReason.SURGICAL_ZERO_TOLERANCE, false, nowMs, countImpulse = true)
            state.activeSecondsToday >= config.dailyLimitSeconds ->
                interrupt(state, config, target, InterruptionReason.DAILY_LIMIT_EXHAUSTED, false, nowMs, countImpulse = true)
            hasValidPass(state, target, nowMs) ->
                Step(state.copy(activeTarget = target, lastPositiveMs = nowMs, lastTickMs = nowMs), Decision.Allow)
            else ->
                interrupt(state, config, target, InterruptionReason.MINDFUL_PAUSE_ON_ENTRY, true, nowMs, countImpulse = true)
        }
    }

    /** R4: the 1 s poll tick. */
    fun tick(state: RuntimeState, config: ProtectionConfig, nowMs: Long, interactive: Boolean): Step {
        if (!interactive) {
            return Step(clearPass(stop(state)).copy(lastTickMs = nowMs), Decision.Allow)
        }
        val active = state.activeTarget ?: return Step(state.copy(lastTickMs = nowMs), Decision.Allow)
        if (active !in config.enabledTargets || nowMs - state.lastPositiveMs >= STALE_POSITIVE_MS) {
            return Step(stop(state).copy(lastTickMs = nowMs), Decision.Allow)
        }

        val delta = (nowMs - state.lastTickMs).coerceIn(0L, MAX_TICK_DELTA_MS)
        val pending = state.pendingMs + delta
        val counted = state.copy(
            activeSecondsToday = state.activeSecondsToday + (pending / 1000L).toInt(),
            pendingMs = pending % 1000L,
            lastTickMs = nowMs
        )

        if (isZeroTolerance(config) || counted.activeSecondsToday >= config.dailyLimitSeconds) {
            val reason = if (isZeroTolerance(config)) InterruptionReason.SURGICAL_ZERO_TOLERANCE else InterruptionReason.DAILY_LIMIT_EXHAUSTED
            return interrupt(clearPass(counted), config, active, reason, false, nowMs, countImpulse = false)
        }
        if (!hasValidPass(counted, active, nowMs)) {
            return interrupt(counted, config, active, InterruptionReason.MINDFUL_PAUSE_ON_ENTRY, true, nowMs, countImpulse = false)
        }
        return Step(counted, Decision.Allow)
    }

    /** R5: the user completed the breathing pause and chose to continue. Activation waits for the next positive. */
    fun onContinue(state: RuntimeState, config: ProtectionConfig, target: ShortFormTarget, nowMs: Long): RuntimeState {
        val seconds = max(MIN_PASS_SECONDS, remainingSeconds(config, state).toLong() + PASS_EXTRA_SECONDS)
        return state.copy(passTarget = target, passUntilMs = nowMs + seconds * 1000L)
    }

    /** R6: the user stepped back. */
    fun onStepBack(state: RuntimeState): RuntimeState = clearPass(stop(state))

    /** R7: day rollover with streaks and shields; skipped days with no data count as clean. */
    fun rollover(state: RuntimeState, config: ProtectionConfig, todayIso: String): RuntimeState {
        if (state.dayIso == todayIso) return state
        val today = parseDate(todayIso) ?: return state
        val previous = parseDate(state.dayIso)
        if (previous == null || !today.isAfter(previous)) return state.copy(dayIso = todayIso)

        var streak = state.streakDays
        var shields = state.streakShields
        fun closeDay(withinGoal: Boolean) {
            if (withinGoal) {
                streak += 1
                if (streak % 7 == 0) shields = min(MAX_SHIELDS, shields + 1)
            } else if (shields > 0) {
                shields -= 1
            } else {
                streak = 0
                shields = 0
            }
        }
        closeDay(state.activeSecondsToday <= config.dailyLimitSeconds)
        val skipped = min(MAX_SKIPPED_DAYS, ChronoUnit.DAYS.between(previous, today) - 1)
        repeat(skipped.toInt()) { closeDay(true) }

        val oldest = today.minusDays(HISTORY_DAYS.toLong())
        val history = (state.history.filter { it.dateIso != state.dayIso } +
            DayRecord(state.dayIso, state.activeSecondsToday, state.interceptionsToday))
            .filter { record -> parseDate(record.dateIso)?.let { !it.isBefore(oldest) && it.isBefore(today) } == true }
            .sortedBy { it.dateIso }

        return state.copy(
            dayIso = todayIso,
            activeTarget = null,
            pendingMs = 0L,
            activeSecondsToday = 0,
            interceptionsToday = 0,
            history = history,
            streakDays = streak,
            streakShields = shields,
            passTarget = null,
            passUntilMs = 0L,
            lastInterruptionTarget = null,
            lastInterruptionMs = 0L
        )
    }

    /** R8: today plus the previous 6 days. */
    fun secondsLast7Days(state: RuntimeState): Int =
        state.activeSecondsToday + windowHistory(state).sumOf { it.seconds }

    fun interceptionsLast7Days(state: RuntimeState): Int =
        state.interceptionsToday + windowHistory(state).sumOf { it.interceptions }

    /** The part of the runtime state that is worth persisting (volatile session fields dropped). */
    fun persistable(state: RuntimeState): RuntimeState = state.copy(
        activeTarget = null,
        lastPositiveMs = 0L,
        lastTickMs = 0L,
        pendingMs = 0L,
        lastInterruptionTarget = null,
        lastInterruptionMs = 0L,
        lastLaunchTarget = null,
        lastLaunchMs = 0L
    )

    private fun windowHistory(state: RuntimeState): List<DayRecord> {
        val today = parseDate(state.dayIso) ?: return emptyList()
        val first = today.minusDays((HISTORY_DAYS - 1).toLong())
        return state.history.filter { record ->
            parseDate(record.dateIso)?.let { !it.isBefore(first) && it.isBefore(today) } == true
        }
    }

    private fun interrupt(
        state: RuntimeState,
        config: ProtectionConfig,
        target: ShortFormTarget,
        reason: InterruptionReason,
        canContinue: Boolean,
        nowMs: Long,
        countImpulse: Boolean
    ): Step {
        val sameImpulse = state.lastInterruptionTarget == target && nowMs - state.lastInterruptionMs < IMPULSE_DEDUPE_MS
        val shouldLaunch = !(state.lastLaunchTarget == target && nowMs - state.lastLaunchMs < LAUNCH_DEDUPE_MS)
        var next = stop(state).copy(
            interceptionsToday = state.interceptionsToday + if (countImpulse && !sameImpulse) 1 else 0,
            lastInterruptionTarget = target,
            lastInterruptionMs = nowMs
        )
        if (shouldLaunch) next = next.copy(lastLaunchTarget = target, lastLaunchMs = nowMs)
        val remaining = if (canContinue) remainingSeconds(config, next) else 0
        return Step(next, Decision.Interrupt(target, reason, canContinue, remaining, shouldLaunch))
    }

    private fun parseDate(iso: String): LocalDate? =
        if (iso.isBlank()) null else runCatching { LocalDate.parse(iso) }.getOrNull()
}

/** Pure string codecs used by LocalStore so they can be unit tested. */
object RuntimeCodec {
    fun encodeHistory(history: List<DayRecord>): String =
        history.joinToString(";") { "${it.dateIso}=${it.seconds},${it.interceptions}" }

    fun decodeHistory(raw: String?): List<DayRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";").mapNotNull { item ->
            val date = item.substringBefore("=", "").trim()
            val values = item.substringAfter("=", "").split(",")
            val seconds = values.getOrNull(0)?.trim()?.toIntOrNull()
            val interceptions = values.getOrNull(1)?.trim()?.toIntOrNull()
            val valid = date.isNotEmpty() && runCatching { LocalDate.parse(date) }.isSuccess
            if (valid && seconds != null && interceptions != null) DayRecord(date, seconds, interceptions) else null
        }
    }

    fun encodeTargets(targets: Set<ShortFormTarget>): String = targets.joinToString(",") { it.name }

    fun decodeTargets(raw: String?, default: Set<ShortFormTarget>): Set<ShortFormTarget> = when {
        raw == null -> default
        raw.isBlank() -> emptySet()
        else -> raw.split(",")
            .mapNotNull { name -> runCatching { ShortFormTarget.valueOf(name.trim()) }.getOrNull() }
            .toSet()
    }
}
