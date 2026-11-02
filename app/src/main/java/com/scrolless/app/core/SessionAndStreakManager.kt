package com.scrolless.app.core

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

sealed class InterventionDecision {
    data object Allow : InterventionDecision()
    data class ShowInterruption(
        val target: ShortFormTarget,
        val reason: InterruptionReason,
        val canContinueAfterPause: Boolean,
        val remainingSecondsToday: Int
    ) : InterventionDecision()
}

data class SessionAndStreakState(
    val mode: ProtectionMode = ProtectionMode.MINDFUL_BUDGET,
    val dailyLimitSeconds: Int = ProtectionMode.MINDFUL_BUDGET.defaultDailyLimitSeconds,
    val activeReelSecondsToday: Int = 0,
    val isCurrentlyInReelTab: Boolean = false,
    val currentTarget: ShortFormTarget? = null,
    val interceptionsToday: Int = 0,
    val weeklyInterceptions: Int = 0,
    val streakDays: Int = 1,
    val streakShields: Int = 1,
    val mindfulAllowanceUntilEpochSec: Long = 0L,
    val enabledTargets: Set<ShortFormTarget> = ShortFormTarget.entries.toSet()
) {
    val remainingSecondsToday: Int
        get() = max(0, dailyLimitSeconds - activeReelSecondsToday)

    val usageRatio: Float
        get() = if (dailyLimitSeconds <= 0) 1f else min(1f, activeReelSecondsToday.toFloat() / dailyLimitSeconds)
}

/**
 * Pure state machine managing tab-only time accounting, mindful pause intervals,
 * and non-punitive daily streaks with shields.
 */
object SessionAndStreakManager {
    private const val MINDFUL_ALLOWANCE_WINDOW_SEC = 180L // 3 min pass after completing a mindful pause
    private const val MAX_SHIELDS = 3
    private const val AVG_MINUTES_SAVED_PER_INTERCEPTION = 8.5

    fun onWindowEvaluated(
        state: SessionAndStreakState,
        detection: DetectionResult,
        nowEpochSec: Long
    ): Pair<SessionAndStreakState, InterventionDecision> {
        if (!detection.isShortForm || detection.target == null || detection.target !in state.enabledTargets) {
            // Immediately pause timer when user returns to normal feed, DMs, long-form video, or if target is disabled
            return state.copy(
                isCurrentlyInReelTab = false,
                currentTarget = null
            ) to InterventionDecision.Allow
        }

        val enteringNewSession = !state.isCurrentlyInReelTab || state.currentTarget != detection.target
        val updatedEntryState = state.copy(
            isCurrentlyInReelTab = true,
            currentTarget = detection.target
        )

        if (state.mode == ProtectionMode.SURGICAL_BLOCK) {
            val bumped = if (enteringNewSession) bumpInterception(updatedEntryState) else updatedEntryState
            return bumped to InterventionDecision.ShowInterruption(
                target = detection.target,
                reason = InterruptionReason.SURGICAL_ZERO_TOLERANCE,
                canContinueAfterPause = false,
                remainingSecondsToday = 0
            )
        }

        if (state.activeReelSecondsToday >= state.dailyLimitSeconds) {
            val bumped = if (enteringNewSession) bumpInterception(updatedEntryState) else updatedEntryState
            return bumped to InterventionDecision.ShowInterruption(
                target = detection.target,
                reason = InterruptionReason.DAILY_LIMIT_EXHAUSTED,
                canContinueAfterPause = false,
                remainingSecondsToday = 0
            )
        }

        if (nowEpochSec >= state.mindfulAllowanceUntilEpochSec) {
            val bumped = if (enteringNewSession) bumpInterception(updatedEntryState) else updatedEntryState
            return bumped to InterventionDecision.ShowInterruption(
                target = detection.target,
                reason = InterruptionReason.MINDFUL_PAUSE_ON_ENTRY,
                canContinueAfterPause = true,
                remainingSecondsToday = bumped.remainingSecondsToday
            )
        }

        return updatedEntryState to InterventionDecision.Allow
    }

    fun onActiveSecondTick(
        state: SessionAndStreakState,
        nowEpochSec: Long
    ): Pair<SessionAndStreakState, InterventionDecision> {
        // Crucial invariant (S2): only increment timer while actively inside an enabled Reels/Shorts/TikTok tab
        if (!state.isCurrentlyInReelTab || state.currentTarget == null || state.currentTarget !in state.enabledTargets) {
            return state.copy(isCurrentlyInReelTab = false, currentTarget = null) to InterventionDecision.Allow
        }

        val nextSeconds = state.activeReelSecondsToday + 1
        val nextState = state.copy(activeReelSecondsToday = nextSeconds)

        if (state.mode == ProtectionMode.SURGICAL_BLOCK || nextSeconds >= state.dailyLimitSeconds) {
            return nextState to InterventionDecision.ShowInterruption(
                target = state.currentTarget,
                reason = if (state.mode == ProtectionMode.SURGICAL_BLOCK) {
                    InterruptionReason.SURGICAL_ZERO_TOLERANCE
                } else {
                    InterruptionReason.DAILY_LIMIT_EXHAUSTED
                },
                canContinueAfterPause = false,
                remainingSecondsToday = nextState.remainingSecondsToday
            )
        }

        if (nowEpochSec >= state.mindfulAllowanceUntilEpochSec) {
            return nextState to InterventionDecision.ShowInterruption(
                target = state.currentTarget,
                reason = InterruptionReason.MINDFUL_PAUSE_ON_ENTRY,
                canContinueAfterPause = true,
                remainingSecondsToday = nextState.remainingSecondsToday
            )
        }

        return nextState to InterventionDecision.Allow
    }

    fun grantMindfulContinue(
        state: SessionAndStreakState,
        nowEpochSec: Long
    ): SessionAndStreakState {
        return state.copy(
            mindfulAllowanceUntilEpochSec = nowEpochSec + MINDFUL_ALLOWANCE_WINDOW_SEC
        )
    }

    fun onUserBouncedBack(state: SessionAndStreakState): SessionAndStreakState {
        return state.copy(
            isCurrentlyInReelTab = false,
            currentTarget = null,
            mindfulAllowanceUntilEpochSec = 0L
        )
    }

    fun onDayRollover(state: SessionAndStreakState, didBypassProtection: Boolean = false): SessionAndStreakState {
        val withinGoal = !didBypassProtection && state.activeReelSecondsToday <= state.dailyLimitSeconds
        val nextStreak: Int
        val nextShields: Int

        if (withinGoal) {
            nextStreak = state.streakDays + 1
            nextShields = if (nextStreak % 7 == 0) {
                min(MAX_SHIELDS, state.streakShields + 1)
            } else {
                state.streakShields
            }
        } else if (state.streakShields > 0) {
            nextStreak = state.streakDays
            nextShields = state.streakShields - 1
        } else {
            nextStreak = 0
            nextShields = 0
        }

        return state.copy(
            activeReelSecondsToday = 0,
            interceptionsToday = 0,
            isCurrentlyInReelTab = false,
            currentTarget = null,
            streakDays = nextStreak,
            streakShields = nextShields,
            mindfulAllowanceUntilEpochSec = 0L
        )
    }

    fun estimateSavedHours(weeklyInterceptions: Int): Double {
        val hours = (weeklyInterceptions * AVG_MINUTES_SAVED_PER_INTERCEPTION) / 60.0
        return Math.round(hours * 10.0) / 10.0
    }

    private fun bumpInterception(state: SessionAndStreakState): SessionAndStreakState {
        return state.copy(
            interceptionsToday = state.interceptionsToday + 1,
            weeklyInterceptions = state.weeklyInterceptions + 1
        )
    }
}
