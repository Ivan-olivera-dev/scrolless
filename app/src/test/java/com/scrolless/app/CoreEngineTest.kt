package com.scrolless.app

import com.scrolless.app.core.DetectionEngine
import com.scrolless.app.core.InterventionDecision
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.SessionAndStreakManager
import com.scrolless.app.core.SessionAndStreakState
import com.scrolless.app.core.ShortFormTarget
import com.scrolless.app.core.WindowSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEngineTest {

    private val engine = DetectionEngine()

    @Test
    fun `detects Instagram Reels tab but ignores Instagram DMs and normal feed`() {
        val dmSnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/row_inbox_container")
        )
        assertFalse(engine.evaluateWindow(dmSnapshot).isShortForm)

        val reelsSnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/clips_viewer_view_pager")
        )
        val result = engine.evaluateWindow(reelsSnapshot)
        assertTrue(result.isShortForm)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, result.target)
    }

    @Test
    fun `detects YouTube Shorts player but ignores normal YouTube watch player`() {
        val longVideoSnapshot = WindowSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = setOf("com.google.android.youtube:id/watch_player")
        )
        assertFalse(engine.evaluateWindow(longVideoSnapshot).isShortForm)

        val shortsSnapshot = WindowSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = setOf("com.google.android.youtube:id/reel_player_page_container")
        )
        val result = engine.evaluateWindow(shortsSnapshot)
        assertTrue(result.isShortForm)
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, result.target)
    }

    @Test
    fun `timer runs ONLY while inside Reels tab and pauses immediately on exit`() {
        val initial = SessionAndStreakState(
            mode = ProtectionMode.MINDFUL_BUDGET,
            dailyLimitSeconds = 600,
            mindfulAllowanceUntilEpochSec = 2000L
        )
        val reelsDetection = engine.evaluateWindow(
            WindowSnapshot("com.instagram.android", setOf("com.instagram.android:id/clips_viewer_view_pager"))
        )

        val (inReelState, decision1) = SessionAndStreakManager.onWindowEvaluated(initial, reelsDetection, 1000L)
        assertTrue(decision1 is InterventionDecision.Allow)
        assertTrue(inReelState.isCurrentlyInReelTab)

        val (afterTick1, _) = SessionAndStreakManager.onActiveSecondTick(inReelState, 1001L)
        assertEquals(1, afterTick1.activeReelSecondsToday)

        // Exit to Instagram DMs -> timer must stop counting
        val dmDetection = engine.evaluateWindow(
            WindowSnapshot("com.instagram.android", setOf("com.instagram.android:id/row_inbox_container"))
        )
        val (inDmState, _) = SessionAndStreakManager.onWindowEvaluated(afterTick1, dmDetection, 1002L)
        assertFalse(inDmState.isCurrentlyInReelTab)

        val (afterDmTick, _) = SessionAndStreakManager.onActiveSecondTick(inDmState, 1003L)
        assertEquals(1, afterDmTick.activeReelSecondsToday)
    }

    @Test
    fun `streak increments on clean day and consumes shield instead of resetting on single slip`() {
        val day6State = SessionAndStreakState(
            dailyLimitSeconds = 300,
            activeReelSecondsToday = 120,
            streakDays = 6,
            streakShields = 1
        )
        // Day 7 clean -> earns +1 shield (total 2) and reaches 7-day streak
        val day7State = SessionAndStreakManager.onDayRollover(day6State)
        assertEquals(7, day7State.streakDays)
        assertEquals(2, day7State.streakShields)

        // Over budget day -> uses 1 shield and keeps 7-day streak
        val overBudget = day7State.copy(activeReelSecondsToday = 450)
        val day8State = SessionAndStreakManager.onDayRollover(overBudget)
        assertEquals(7, day8State.streakDays)
        assertEquals(1, day8State.streakShields)
    }
}
