package com.scrolless.app

import com.scrolless.app.core.Decision
import com.scrolless.app.core.DetectionEngine
import com.scrolless.app.core.InterruptionReason
import com.scrolless.app.core.ProtectionConfig
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.RuntimeState
import com.scrolless.app.core.SessionController
import com.scrolless.app.core.ShortFormTarget
import com.scrolless.app.core.WindowSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end scenarios: realistic visible-node snapshots -> DetectionEngine -> SessionController,
 * driven the way the accessibility service drives them (evaluate, then a 1 s tick).
 */
class CoreEngineTest {

    private val config15 = ProtectionConfig(ProtectionMode.MINDFUL_BUDGET, 15 * 60, ShortFormTarget.entries.toSet(), emptySet())
    private val tiktok = WindowSnapshot("com.zhiliaoapp.musically", emptyList())
    private val launcher = WindowSnapshot("com.android.launcher3", emptyList())

    private class Driver(var config: ProtectionConfig) {
        var state = RuntimeState(dayIso = "2026-11-15")
        var now = 1_000L
        val interrupts = mutableListOf<Decision.Interrupt>()

        fun evaluate(snapshot: WindowSnapshot): Decision {
            val step = SessionController.onDetection(state, config, DetectionEngine.detect(snapshot, config.fullAppTargets), now)
            state = step.state
            (step.decision as? Decision.Interrupt)?.let { interrupts += it }
            return step.decision
        }

        /** One second of the service: evaluate the active root, 1 s passes, then the poll ticks. */
        fun poll(snapshot: WindowSnapshot, seconds: Int = 1) {
            repeat(seconds) {
                evaluate(snapshot)
                now += 1_000
                val step = SessionController.tick(state, config, now, true)
                state = step.state
                (step.decision as? Decision.Interrupt)?.let { interrupts += it }
            }
        }

        fun continueOn(target: ShortFormTarget) {
            state = SessionController.onContinue(state, config, target, now)
        }
    }

    @Test
    fun `S8 journey 2 min Reels then 1 min Shorts then 1 min TikTok pauses each app and consumes 240 s`() {
        val d = Driver(config15)

        assertTrue(d.evaluate(igReelsTab()) is Decision.Interrupt)
        d.continueOn(ShortFormTarget.INSTAGRAM_REELS)
        d.poll(igReelsTab(), 120)
        assertEquals(120, d.state.activeSecondsToday)

        val yt = d.evaluate(ytShortsPlayer())
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, (yt as Decision.Interrupt).target)
        assertEquals(InterruptionReason.MINDFUL_PAUSE_ON_ENTRY, yt.reason)
        d.continueOn(ShortFormTarget.YOUTUBE_SHORTS)
        d.poll(ytShortsPlayer(), 60)
        assertEquals(180, d.state.activeSecondsToday)

        val tt = d.evaluate(tiktok)
        assertEquals(ShortFormTarget.TIKTOK, (tt as Decision.Interrupt).target)
        d.continueOn(ShortFormTarget.TIKTOK)
        d.poll(tiktok, 60)

        assertEquals(240, d.state.activeSecondsToday)
        assertEquals(660, SessionController.remainingSeconds(d.config, d.state))
        assertEquals(3, d.state.interceptionsToday)
        assertEquals(3, d.interrupts.size)
    }

    @Test
    fun `S11 after visiting Reels the rest of Instagram is free and the timer stops`() {
        val d = Driver(config15)
        d.evaluate(igReelsTab())
        d.continueOn(ShortFormTarget.INSTAGRAM_REELS)
        d.poll(igReelsTab(), 30)
        assertEquals(30, d.state.activeSecondsToday)

        // Back to the home feed: the retained Reels fragment is hidden, so it is absent from the visible snapshot.
        d.poll(igHomeFeed(), 60)
        assertNull(d.state.activeTarget)
        assertTrue("at most the 1.5 s transient grace may be counted", d.state.activeSecondsToday <= 32)
        d.poll(igDmInbox(), 60)
        assertTrue(d.state.activeSecondsToday <= 32)
        assertEquals("no interruption outside Reels", 1, d.interrupts.size)
    }

    @Test
    fun `S11 Instagram feed, DMs and Stories never interrupt in surgical block mode`() {
        val d = Driver(config15.copy(mode = ProtectionMode.SURGICAL_BLOCK, dailyLimitSeconds = 0))
        d.poll(igHomeFeed(), 20)
        d.poll(igDmInbox(), 20)
        d.poll(igSnapshot(igNode("reel_viewer_root"), igNode("clips_viewer_view_pager", 0.9f)), 20)
        d.poll(ytHomeWithShortsShelf(), 20)
        d.poll(ytLongVideo(), 20)
        assertEquals(0, d.interrupts.size)
        assertEquals(0, d.state.interceptionsToday)
    }

    @Test
    fun `S11 leaving Shorts to the launcher stops the timer immediately`() {
        val d = Driver(config15)
        d.evaluate(ytShortsPlayer())
        d.continueOn(ShortFormTarget.YOUTUBE_SHORTS)
        d.poll(ytShortsPlayer(), 10)
        val before = d.state.activeSecondsToday
        d.poll(launcher, 30)
        assertNull(d.state.activeTarget)
        assertEquals(before, d.state.activeSecondsToday)
    }

    @Test
    fun `S11 a single transient non-Reels frame inside Reels does not stop the session`() {
        val d = Driver(config15)
        d.evaluate(igReelsTab())
        d.continueOn(ShortFormTarget.INSTAGRAM_REELS)
        d.poll(igReelsTab(), 5)
        d.now -= 500 // a content event arriving between polls
        d.evaluate(igSnapshot(igNode("clips_tab", 0.07f, selected = true)))
        d.now += 500
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, d.state.activeTarget)
        d.poll(igReelsTab(), 5)
        assertEquals(10, d.state.activeSecondsToday)
        assertEquals(1, d.interrupts.size)
    }

    @Test
    fun `S11 full-app Instagram interrupts on DMs while YouTube stays surgical`() {
        val d = Driver(config15.copy(mode = ProtectionMode.SURGICAL_BLOCK, dailyLimitSeconds = 0, fullAppTargets = setOf(ShortFormTarget.INSTAGRAM_REELS)))
        val i = d.evaluate(igDmInbox())
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, (i as Decision.Interrupt).target)
        assertEquals(InterruptionReason.SURGICAL_ZERO_TOLERANCE, i.reason)
        d.now += 20_000
        assertEquals(Decision.Allow, d.evaluate(ytLongVideo()))
    }

    @Test
    fun `S10 returning to Reels within the pass resumes without a new breathing pause`() {
        val d = Driver(config15)
        d.evaluate(igReelsTab())
        d.continueOn(ShortFormTarget.INSTAGRAM_REELS)
        d.poll(igReelsTab(), 20)
        d.poll(igSnapshot(igNode("reel_viewer_root")), 10) // Stories
        d.poll(igReelsTab(), 20)
        assertEquals(1, d.interrupts.size)
        assertTrue(d.state.activeSecondsToday in 40..42)
    }

    @Test
    fun `S2 disabled target never interrupts or counts`() {
        val d = Driver(config15.copy(mode = ProtectionMode.SURGICAL_BLOCK, dailyLimitSeconds = 0, enabledTargets = setOf(ShortFormTarget.YOUTUBE_SHORTS)))
        d.poll(igReelsTab(), 10)
        d.poll(tiktok, 10)
        assertEquals(0, d.interrupts.size)
        assertEquals(0, d.state.activeSecondsToday)
    }

    @Test
    fun `S7 default runtime state starts with zero data`() {
        val s = RuntimeState()
        assertEquals(0, s.streakDays)
        assertEquals(0, s.streakShields)
        assertEquals(0, s.interceptionsToday)
        assertEquals(0, s.activeSecondsToday)
        assertEquals(0, SessionController.secondsLast7Days(s))
    }
}
