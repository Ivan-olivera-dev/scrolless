package com.scrolless.app

import com.scrolless.app.core.DetectionEngine
import com.scrolless.app.core.InterruptionReason
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
    fun `default SessionAndStreakState starts with zero mocked data`() {
        val defaultState = SessionAndStreakState()
        assertEquals(0, defaultState.streakDays)
        assertEquals(0, defaultState.streakShields)
        assertEquals(0, defaultState.interceptionsToday)
        assertEquals(0, defaultState.weeklyInterceptions)
        assertEquals(0.0, SessionAndStreakManager.estimateSavedHours(defaultState.weeklyInterceptions), 0.01)
    }

    @Test
    fun `rollover from fresh zero state achieves day 1 streak`() {
        val fresh = SessionAndStreakState(
            dailyLimitSeconds = 600,
            activeReelSecondsToday = 150,
            streakDays = 0,
            streakShields = 0
        )
        val next = SessionAndStreakManager.onDayRollover(fresh)
        assertEquals(1, next.streakDays)
        assertEquals(0, next.streakShields)
        assertEquals(0, next.activeReelSecondsToday)
    }

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
            currentTarget = ShortFormTarget.INSTAGRAM_REELS,
            mindfulAllowanceTarget = ShortFormTarget.INSTAGRAM_REELS,
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

    @Test
    fun `detects Instagram Reels via clips substring view ID`() {
        val snapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/clips_viewer_container")
        )
        val result = engine.evaluateWindow(snapshot)
        assertTrue(result.isShortForm)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, result.target)
    }

    @Test
    fun `detects Instagram Reels via selected bottom tab label Reels`() {
        val snapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = emptySet(),
            selectedNodeLabels = setOf("Reels")
        )
        val result = engine.evaluateWindow(snapshot)
        assertTrue(result.isShortForm)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, result.target)
    }

    @Test
    fun `deselected surgical target is allowed and does not start timer`() {
        val disabledInstagram = SessionAndStreakState(
            mode = ProtectionMode.SURGICAL_BLOCK,
            enabledTargets = setOf(ShortFormTarget.YOUTUBE_SHORTS, ShortFormTarget.TIKTOK)
        )
        val reelsDetection = engine.evaluateWindow(
            WindowSnapshot("com.instagram.android", setOf("com.instagram.android:id/clips_viewer_view_pager"))
        )
        val (state, decision) = SessionAndStreakManager.onWindowEvaluated(disabledInstagram, reelsDetection, 1000L)
        assertTrue(decision is InterventionDecision.Allow)
        assertFalse(state.isCurrentlyInReelTab)
    }

    @Test
    fun `detects YouTube Shorts via selected bottom tab label Shorts`() {
        val snapshot = WindowSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = emptySet(),
            selectedNodeLabels = setOf("Shorts")
        )
        val result = engine.evaluateWindow(snapshot)
        assertTrue(result.isShortForm)
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, result.target)
    }

    @Test
    fun `detects YouTube Shorts via reel player substring view ID`() {
        val snapshot = WindowSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = setOf("com.google.android.youtube:id/reel_player_fragment_root")
        )
        val result = engine.evaluateWindow(snapshot)
        assertTrue(result.isShortForm)
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, result.target)
    }

    @Test
    fun `detects Instagram Reels via selected Reels tab and ignores contentDescriptions without selected tab`() {
        val selectedTabSnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = emptySet(),
            selectedNodeLabels = setOf("Reels, tab 4 of 5")
        )
        val result1 = engine.evaluateWindow(selectedTabSnapshot)
        assertTrue(result1.isShortForm)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, result1.target)

        val unselectedDescSnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = emptySet(),
            contentDescriptions = setOf("Reels, video", "Original audio by artist")
        )
        val result2 = engine.evaluateWindow(unselectedDescSnapshot)
        assertFalse(result2.isShortForm)
    }

    @Test
    fun `safe Instagram direct thread suppresses Reels detection`() {
        val snapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf(
                "com.instagram.android:id/direct_thread_toggle",
                "com.instagram.android:id/clips_viewer_container"
            )
        )
        val result = engine.evaluateWindow(snapshot)
        assertFalse(result.isShortForm)
    }

    @Test
    fun `onUserBouncedBack clears reel state and resets mindful allowance`() {
        val active = SessionAndStreakState(
            isCurrentlyInReelTab = true,
            currentTarget = ShortFormTarget.INSTAGRAM_REELS,
            mindfulAllowanceTarget = ShortFormTarget.INSTAGRAM_REELS,
            mindfulAllowanceUntilEpochSec = 5000L
        )
        val bounced = SessionAndStreakManager.onUserBouncedBack(active)
        assertFalse(bounced.isCurrentlyInReelTab)
        assertEquals(null, bounced.currentTarget)
        assertEquals(null, bounced.mindfulAllowanceTarget)
        assertEquals(0L, bounced.mindfulAllowanceUntilEpochSec)
    }

    @Test
    fun `user journey 2m Instagram Reels then 1m YouTube Shorts then 1m TikTok triggers breathing pause on each app and records 4m consumed`() {
        val initial = SessionAndStreakState(
            mode = ProtectionMode.MINDFUL_BUDGET,
            dailyLimitSeconds = 15 * 60 // 15 mins
        )

        val reelsSnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/clips_viewer_view_pager")
        )
        val shortsSnapshot = WindowSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = setOf("com.google.android.youtube:id/reel_player_page_container")
        )
        val tikTokSnapshot = WindowSnapshot(
            packageName = "com.zhiliaoapp.musically",
            viewIds = setOf("com.zhiliaoapp.musically:id/view_pager")
        )

        var t = 1000L

        // 1. Enter Instagram Reels -> should trigger breathing pause
        val (state1, decision1) = SessionAndStreakManager.onWindowEvaluated(
            initial,
            engine.evaluateWindow(reelsSnapshot),
            t
        )
        assertTrue("Reels entry must show interruption", decision1 is InterventionDecision.ShowInterruption)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, (decision1 as InterventionDecision.ShowInterruption).target)
        assertEquals(InterruptionReason.MINDFUL_PAUSE_ON_ENTRY, decision1.reason)

        // User breathes and continues
        var currentState = SessionAndStreakManager.grantMindfulContinue(state1, t)

        // User watches Reels for 2 minutes (120 seconds)
        repeat(120) {
            t++
            val (ticked, tickDecision) = SessionAndStreakManager.onActiveSecondTick(currentState, t)
            assertTrue("Second tick in Reels must be allowed", tickDecision is InterventionDecision.Allow)
            currentState = ticked
        }
        assertEquals(120, currentState.activeReelSecondsToday)

        // 2. User closes Reels and switches to YouTube Shorts at t
        val (state2, decision2) = SessionAndStreakManager.onWindowEvaluated(
            currentState,
            engine.evaluateWindow(shortsSnapshot),
            t
        )
        assertTrue("YouTube Shorts entry must show interruption even if Reels had allowance", decision2 is InterventionDecision.ShowInterruption)
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, (decision2 as InterventionDecision.ShowInterruption).target)

        // User breathes and continues in Shorts
        currentState = SessionAndStreakManager.grantMindfulContinue(state2, t)

        // User watches Shorts for 1 minute (60 seconds)
        repeat(60) {
            t++
            val (ticked, tickDecision) = SessionAndStreakManager.onActiveSecondTick(currentState, t)
            assertTrue("Second tick in Shorts must be allowed", tickDecision is InterventionDecision.Allow)
            currentState = ticked
        }
        assertEquals(180, currentState.activeReelSecondsToday)

        // 3. User closes Shorts and switches to TikTok at t
        val (state3, decision3) = SessionAndStreakManager.onWindowEvaluated(
            currentState,
            engine.evaluateWindow(tikTokSnapshot),
            t
        )
        assertTrue("TikTok entry must show interruption even if Shorts had allowance", decision3 is InterventionDecision.ShowInterruption)
        assertEquals(ShortFormTarget.TIKTOK, (decision3 as InterventionDecision.ShowInterruption).target)

        // User breathes and continues in TikTok
        currentState = SessionAndStreakManager.grantMindfulContinue(state3, t)

        // User watches TikTok for 1 minute (60 seconds)
        repeat(60) {
            t++
            val (ticked, tickDecision) = SessionAndStreakManager.onActiveSecondTick(currentState, t)
            assertTrue("Second tick in TikTok must be allowed", tickDecision is InterventionDecision.Allow)
            currentState = ticked
        }

        // Final verification: 4 minutes (240s) consumed, 11 minutes (660s) left
        assertEquals(240, currentState.activeReelSecondsToday)
        assertEquals(11 * 60, currentState.remainingSecondsToday)
    }

    @Test
    fun `isExplicitNonShortFormInTargetApp correctly identifies explicit exits vs partial sub-nodes`() {
        // Instagram safe feed / DM view IDs
        val igDmSnapshot = WindowSnapshot("com.instagram.android", setOf("com.instagram.android:id/direct_thread_toggle"))
        assertTrue(engine.isExplicitNonShortFormInTargetApp(igDmSnapshot))

        val igFeedSnapshot = WindowSnapshot("com.instagram.android", setOf("com.instagram.android:id/feed_timeline"))
        assertTrue(engine.isExplicitNonShortFormInTargetApp(igFeedSnapshot))

        // Instagram non-reels bottom tabs
        val igHomeTab = WindowSnapshot("com.instagram.android", emptySet(), selectedNodeLabels = setOf("Inicio"))
        assertTrue(engine.isExplicitNonShortFormInTargetApp(igHomeTab))

        val igSearchTab = WindowSnapshot("com.instagram.android", emptySet(), selectedNodeLabels = setOf("Buscar"))
        assertTrue(engine.isExplicitNonShortFormInTargetApp(igSearchTab))

        val igProfileTab = WindowSnapshot("com.instagram.android", emptySet(), selectedNodeLabels = setOf("Perfil"))
        assertTrue(engine.isExplicitNonShortFormInTargetApp(igProfileTab))

        // Instagram partial sub-node in Reels (like button / author label / etc)
        val igPartialReelSubNode = WindowSnapshot("com.instagram.android", setOf("com.instagram.android:id/row_feed_button_like"))
        assertFalse(engine.isExplicitNonShortFormInTargetApp(igPartialReelSubNode))

        // YouTube normal watch player
        val ytWatchSnapshot = WindowSnapshot("com.google.android.youtube", setOf("com.google.android.youtube:id/watch_player"))
        assertTrue(engine.isExplicitNonShortFormInTargetApp(ytWatchSnapshot))

        // YouTube non-shorts bottom tabs
        val ytHomeTab = WindowSnapshot("com.google.android.youtube", emptySet(), selectedNodeLabels = setOf("Principal")) // wait, "Inicio" or "Home"
        val ytInicioTab = WindowSnapshot("com.google.android.youtube", emptySet(), selectedNodeLabels = setOf("Inicio"))
        assertTrue(engine.isExplicitNonShortFormInTargetApp(ytInicioTab))

        val ytSubsTab = WindowSnapshot("com.google.android.youtube", emptySet(), selectedNodeLabels = setOf("Suscripciones"))
        assertTrue(engine.isExplicitNonShortFormInTargetApp(ytSubsTab))

        // YouTube partial sub-node in Shorts
        val ytPartialShortsSubNode = WindowSnapshot("com.google.android.youtube", setOf("com.google.android.youtube:id/like_button"))
        assertFalse(engine.isExplicitNonShortFormInTargetApp(ytPartialShortsSubNode))
    }

    @Test
    fun `detects YouTube Shorts via contentDescriptions and ignores when normal watch player is present`() {
        val remixShortDesc = WindowSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = emptySet(),
            contentDescriptions = setOf("Remezclar este short", "Me gusta")
        )
        val result1 = engine.evaluateWindow(remixShortDesc)
        assertTrue(result1.isShortForm)
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, result1.target)

        val soundShortDesc = WindowSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = emptySet(),
            contentDescriptions = setOf("Usar este sonido")
        )
        val result2 = engine.evaluateWindow(soundShortDesc)
        assertTrue(result2.isShortForm)
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, result2.target)

        // If normal watch_player is present, should NOT be short-form even with those descriptions
        val normalPlayerSnapshot = WindowSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = setOf("com.google.android.youtube:id/watch_player"),
            contentDescriptions = setOf("Remezclar este short")
        )
        val result3 = engine.evaluateWindow(normalPlayerSnapshot)
        assertFalse(result3.isShortForm)
    }

    @Test
    fun `detects all regional TikTok package variants`() {
        for (pkg in listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.aweme")) {
            val snapshot = WindowSnapshot(packageName = pkg, viewIds = emptySet())
            val result = engine.evaluateWindow(snapshot)
            assertTrue("Package $pkg must be detected as short-form", result.isShortForm)
            assertEquals(ShortFormTarget.TIKTOK, result.target)
        }
    }

    @Test
    fun `detects expanded YouTube Shorts view id substrings`() {
        val idVariants = listOf(
            "com.google.android.youtube:id/reel_time_bar",
            "com.google.android.youtube:id/reel_progress_bar",
            "com.google.android.youtube:id/reel_rhs_layout",
            "com.google.android.youtube:id/reel_dyn_container"
        )
        for (id in idVariants) {
            val snapshot = WindowSnapshot(packageName = "com.google.android.youtube", viewIds = setOf(id))
            val result = engine.evaluateWindow(snapshot)
            assertTrue("ID $id must be detected as YouTube Shorts", result.isShortForm)
            assertEquals(ShortFormTarget.YOUTUBE_SHORTS, result.target)
        }
    }

    @Test
    fun `second tick exhausts daily budget and resets tab tracking`() {
        val state = SessionAndStreakState(
            mode = ProtectionMode.MINDFUL_BUDGET,
            dailyLimitSeconds = 10,
            activeReelSecondsToday = 9,
            isCurrentlyInReelTab = true,
            currentTarget = ShortFormTarget.INSTAGRAM_REELS,
            mindfulAllowanceTarget = ShortFormTarget.INSTAGRAM_REELS,
            mindfulAllowanceUntilEpochSec = 5000L
        )

        val (nextState, decision) = SessionAndStreakManager.onActiveSecondTick(state, 1000L)
        assertEquals(10, nextState.activeReelSecondsToday)
        assertFalse("Must reset isCurrentlyInReelTab when exhausted", nextState.isCurrentlyInReelTab)
        assertEquals(null, nextState.mindfulAllowanceTarget)
        assertEquals(0L, nextState.mindfulAllowanceUntilEpochSec)
        assertTrue(decision is InterventionDecision.ShowInterruption)
        assertEquals(InterruptionReason.DAILY_LIMIT_EXHAUSTED, (decision as InterventionDecision.ShowInterruption).reason)
        assertFalse(decision.canContinueAfterPause)
    }

    @Test
    fun `second tick expires 3-minute mindful allowance and pauses tab tracking for breathing screen`() {
        val state = SessionAndStreakState(
            mode = ProtectionMode.MINDFUL_BUDGET,
            dailyLimitSeconds = 900,
            activeReelSecondsToday = 60,
            isCurrentlyInReelTab = true,
            currentTarget = ShortFormTarget.INSTAGRAM_REELS,
            mindfulAllowanceTarget = ShortFormTarget.INSTAGRAM_REELS,
            mindfulAllowanceUntilEpochSec = 1000L
        )

        // At epoch 1000L (time expired)
        val (nextState, decision) = SessionAndStreakManager.onActiveSecondTick(state, 1000L)
        assertEquals(61, nextState.activeReelSecondsToday)
        assertFalse("Must reset isCurrentlyInReelTab when allowance window expires", nextState.isCurrentlyInReelTab)
        assertEquals(null, nextState.mindfulAllowanceTarget)
        assertEquals(0L, nextState.mindfulAllowanceUntilEpochSec)
        assertTrue(decision is InterventionDecision.ShowInterruption)
        assertEquals(InterruptionReason.MINDFUL_PAUSE_ON_ENTRY, (decision as InterventionDecision.ShowInterruption).reason)
        assertTrue(decision.canContinueAfterPause)
    }

    @Test
    fun `Instagram Stories with reel_viewer view IDs are not detected as Reels`() {
        val storyRootSnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/reel_viewer_root")
        )
        val result1 = engine.evaluateWindow(storyRootSnapshot)
        assertFalse("reel_viewer_root must not trigger Reels detection", result1.isShortForm)

        val storyMediaSnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/reel_viewer_media_container", "com.instagram.android:id/story_viewer")
        )
        val result2 = engine.evaluateWindow(storyMediaSnapshot)
        assertFalse("reel_viewer_media_container must not trigger Reels detection", result2.isShortForm)
    }

    @Test
    fun `Instagram Home Feed video ads and posts are not detected as Reels`() {
        val feedAdSnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/clips_video_container", "com.instagram.android:id/feed_timeline"),
            contentDescriptions = setOf("Reels", "Audio original"),
            selectedNodeLabels = setOf("Inicio")
        )
        val result1 = engine.evaluateWindow(feedAdSnapshot)
        assertFalse("Feed video ad with clips_video_container and Inicio tab selected must not be detected as Reels", result1.isShortForm)

        val feedAdWithoutTimeline = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/clips_video_container", "com.instagram.android:id/clips_item"),
            contentDescriptions = setOf("Reels", "Audio original"),
            selectedNodeLabels = setOf("Home")
        )
        val result2 = engine.evaluateWindow(feedAdWithoutTimeline)
        assertFalse("Feed ad with Home tab selected must not be detected as Reels", result2.isShortForm)
    }

    @Test
    fun `returning to Instagram Reels while daily budget remains allows entry without breathing pause`() {
        val initial = SessionAndStreakState(
            mode = ProtectionMode.MINDFUL_BUDGET,
            dailyLimitSeconds = 15 * 60,
            activeReelSecondsToday = 0
        )
        val reelsSnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/clips_viewer_view_pager")
        )
        var t = 1000L

        // 1. Initial entry shows breathing pause
        val (state1, decision1) = SessionAndStreakManager.onWindowEvaluated(initial, engine.evaluateWindow(reelsSnapshot), t)
        assertTrue(decision1 is InterventionDecision.ShowInterruption)

        // 2. User unlocks mindful continue with 15m budget
        var currentState = SessionAndStreakManager.grantMindfulContinue(state1, t)
        assertTrue(currentState.isCurrentlyInReelTab)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, currentState.mindfulAllowanceTarget)
        assertTrue("Allowance window must cover remaining budget", currentState.mindfulAllowanceUntilEpochSec >= t + 900L)

        // User watches 120s of Reels
        repeat(120) {
            t++
            val (ticked, _) = SessionAndStreakManager.onActiveSecondTick(currentState, t)
            currentState = ticked
        }
        assertEquals(120, currentState.activeReelSecondsToday)

        // 3. User switches to Instagram Stories/DMs -> timer pauses
        val storySnapshot = WindowSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/reel_viewer_root")
        )
        val (storyState, storyDecision) = SessionAndStreakManager.onWindowEvaluated(currentState, engine.evaluateWindow(storySnapshot), t)
        assertTrue(storyDecision is InterventionDecision.Allow)
        assertFalse("Timer must be paused while in Stories", storyState.isCurrentlyInReelTab)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, storyState.mindfulAllowanceTarget)
        assertTrue(storyState.mindfulAllowanceUntilEpochSec > t)

        // 4. User returns to Reels while budget remains (< 15 min) -> allowed without breathing pause!
        val (returnedState, returnDecision) = SessionAndStreakManager.onWindowEvaluated(storyState, engine.evaluateWindow(reelsSnapshot), t)
        assertTrue("Returning to Reels with remaining budget must be Allowed without breathing pause", returnDecision is InterventionDecision.Allow)
        assertTrue("Timer must resume when returning to Reels", returnedState.isCurrentlyInReelTab)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, returnedState.currentTarget)

        // 5. Switching from Reels to YouTube Shorts STILL triggers breathing pause
        val shortsSnapshot = WindowSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = setOf("com.google.android.youtube:id/reel_player_page_container")
        )
        val (shortsState, shortsDecision) = SessionAndStreakManager.onWindowEvaluated(returnedState, engine.evaluateWindow(shortsSnapshot), t)
        assertTrue("Switching to YouTube Shorts must trigger Interruption", shortsDecision is InterventionDecision.ShowInterruption)
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, (shortsDecision as InterventionDecision.ShowInterruption).target)
    }
}
