package com.scrolless.app

import com.scrolless.app.core.DayRecord
import com.scrolless.app.core.Decision
import com.scrolless.app.core.Detection
import com.scrolless.app.core.InterruptionReason
import com.scrolless.app.core.ProtectionConfig
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.RuntimeCodec
import com.scrolless.app.core.RuntimeState
import com.scrolless.app.core.SessionController
import com.scrolless.app.core.ShortFormTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val IG_T = ShortFormTarget.INSTAGRAM_REELS
private val YT_T = ShortFormTarget.YOUTUBE_SHORTS
private val TT_T = ShortFormTarget.TIKTOK

internal fun pos(t: ShortFormTarget) = Detection(t, t, "test-positive")
internal fun neg(watched: ShortFormTarget?) = Detection(null, watched, "test-negative")

class SessionControllerTest {

    private val mindful = ProtectionConfig(ProtectionMode.MINDFUL_BUDGET, 900, ShortFormTarget.entries.toSet(), emptySet())
    private val day = "2026-11-15"

    /** State with an already valid pass for [t] and an active session started at [now]. */
    private fun activeOn(t: ShortFormTarget, now: Long, config: ProtectionConfig = mindful): RuntimeState {
        val withPass = SessionController.onContinue(RuntimeState(dayIso = day), config, t, now)
        val step = SessionController.onDetection(withPass, config, pos(t), now)
        assertEquals(Decision.Allow, step.decision)
        assertEquals(t, step.state.activeTarget)
        return step.state
    }

    private fun interrupt(d: Decision): Decision.Interrupt {
        assertTrue("expected Interrupt but was $d", d is Decision.Interrupt)
        return d as Decision.Interrupt
    }

    // ---------- R1 ----------
    @Test
    fun `R1 positive for the active target refreshes lastPositiveMs and allows`() {
        val s = activeOn(IG_T, 1_000)
        val step = SessionController.onDetection(s, mindful, pos(IG_T), 1_700)
        assertEquals(Decision.Allow, step.decision)
        assertEquals(1_700, step.state.lastPositiveMs)
        assertEquals(IG_T, step.state.activeTarget)
        assertEquals(s.interceptionsToday, step.state.interceptionsToday)
    }

    // ---------- R2 ----------
    @Test
    fun `R2 surgical block mode interrupts with zero tolerance and no continue`() {
        val cfg = mindful.copy(mode = ProtectionMode.SURGICAL_BLOCK, dailyLimitSeconds = 0)
        val step = SessionController.onDetection(RuntimeState(dayIso = day), cfg, pos(IG_T), 1_000)
        val i = interrupt(step.decision)
        assertEquals(InterruptionReason.SURGICAL_ZERO_TOLERANCE, i.reason)
        assertFalse(i.canContinue)
        assertTrue(i.shouldLaunch)
        assertEquals(0, i.remainingSecondsToday)
        assertNull(step.state.activeTarget)
        assertEquals(1, step.state.interceptionsToday)
    }

    @Test
    fun `R2 limit zero in a budget mode is also zero tolerance`() {
        val cfg = mindful.copy(dailyLimitSeconds = 0)
        val i = interrupt(SessionController.onDetection(RuntimeState(dayIso = day), cfg, pos(YT_T), 1_000).decision)
        assertEquals(InterruptionReason.SURGICAL_ZERO_TOLERANCE, i.reason)
        assertFalse(i.canContinue)
    }

    @Test
    fun `R2 exhausted budget interrupts with daily limit even with a valid pass`() {
        val s = SessionController.onContinue(RuntimeState(dayIso = day, activeSecondsToday = 900), mindful, IG_T, 1_000)
        val step = SessionController.onDetection(s, mindful, pos(IG_T), 2_000)
        val i = interrupt(step.decision)
        assertEquals(InterruptionReason.DAILY_LIMIT_EXHAUSTED, i.reason)
        assertFalse(i.canContinue)
        assertEquals(0, i.remainingSecondsToday)
        assertNull(step.state.activeTarget)
    }

    @Test
    fun `R2 valid pass for the same target activates without interrupt`() {
        val s = SessionController.onContinue(RuntimeState(dayIso = day), mindful, IG_T, 1_000)
        val step = SessionController.onDetection(s, mindful, pos(IG_T), 2_000)
        assertEquals(Decision.Allow, step.decision)
        assertEquals(IG_T, step.state.activeTarget)
        assertEquals(2_000, step.state.lastPositiveMs)
        assertEquals(0, step.state.interceptionsToday)
    }

    @Test
    fun `R2 pass for another target does not unlock this one`() {
        val s = SessionController.onContinue(RuntimeState(dayIso = day), mindful, IG_T, 1_000)
        val i = interrupt(SessionController.onDetection(s, mindful, pos(YT_T), 2_000).decision)
        assertEquals(YT_T, i.target)
        assertEquals(InterruptionReason.MINDFUL_PAUSE_ON_ENTRY, i.reason)
        assertTrue(i.canContinue)
        assertEquals(900, i.remainingSecondsToday)
    }

    @Test
    fun `R2 expired pass interrupts with mindful pause`() {
        val s = SessionController.onContinue(RuntimeState(dayIso = day), mindful, IG_T, 1_000)
        val after = s.passUntilMs
        val step = SessionController.onDetection(s, mindful, pos(IG_T), after)
        assertEquals(InterruptionReason.MINDFUL_PAUSE_ON_ENTRY, interrupt(step.decision).reason)
        assertNull(step.state.activeTarget)
    }

    @Test
    fun `R2 repeated interrupts within 10 s count one impulse and within 1500 ms launch once`() {
        var s = RuntimeState(dayIso = day)
        val first = SessionController.onDetection(s, mindful, pos(IG_T), 1_000)
        assertTrue(interrupt(first.decision).shouldLaunch)
        s = first.state
        val second = SessionController.onDetection(s, mindful, pos(IG_T), 2_000)
        assertFalse("relaunch within 1500 ms must be suppressed", interrupt(second.decision).shouldLaunch)
        s = second.state
        val third = SessionController.onDetection(s, mindful, pos(IG_T), 3_600)
        assertTrue("launch allowed again after 1500 ms", interrupt(third.decision).shouldLaunch)
        assertEquals("same target within 10 s is one impulse", 1, third.state.interceptionsToday)
        val later = SessionController.onDetection(third.state, mindful, pos(IG_T), 13_700)
        assertEquals(2, later.state.interceptionsToday)
    }

    @Test
    fun `R2 a different target is a new impulse and launches immediately`() {
        val first = SessionController.onDetection(RuntimeState(dayIso = day), mindful, pos(IG_T), 1_000)
        val second = SessionController.onDetection(first.state, mindful, pos(YT_T), 1_200)
        assertTrue(interrupt(second.decision).shouldLaunch)
        assertEquals(2, second.state.interceptionsToday)
    }

    @Test
    fun `R2 disabled target is treated as negative and allowed`() {
        val cfg = mindful.copy(mode = ProtectionMode.SURGICAL_BLOCK, dailyLimitSeconds = 0, enabledTargets = setOf(YT_T, TT_T))
        val step = SessionController.onDetection(RuntimeState(dayIso = day), cfg, pos(IG_T), 1_000)
        assertEquals(Decision.Allow, step.decision)
        assertNull(step.state.activeTarget)
        assertEquals(0, step.state.interceptionsToday)
    }

    // ---------- R3 ----------
    @Test
    fun `R3 negative in the same app tolerates transient frames under 1500 ms`() {
        val s = activeOn(IG_T, 1_000)
        val step = SessionController.onDetection(s, mindful, neg(IG_T), 2_499)
        assertEquals(Decision.Allow, step.decision)
        assertEquals(IG_T, step.state.activeTarget)
    }

    @Test
    fun `R3 negative in the same app stops once 1500 ms passed since the last positive`() {
        val s = activeOn(IG_T, 1_000)
        val step = SessionController.onDetection(s, mindful, neg(IG_T), 2_500)
        assertEquals(Decision.Allow, step.decision)
        assertNull(step.state.activeTarget)
    }

    @Test
    fun `R3 negative from another app or no app stops immediately`() {
        val s = activeOn(IG_T, 1_000)
        assertNull(SessionController.onDetection(s, mindful, neg(YT_T), 1_010).state.activeTarget)
        assertNull(SessionController.onDetection(s, mindful, neg(null), 1_010).state.activeTarget)
    }

    @Test
    fun `R3 negative keeps the pass so re-entry within the pass does not interrupt`() {
        val s = activeOn(IG_T, 1_000)
        val stopped = SessionController.onDetection(s, mindful, neg(null), 1_100).state
        assertEquals(IG_T, stopped.passTarget)
        val back = SessionController.onDetection(stopped, mindful, pos(IG_T), 5_000)
        assertEquals(Decision.Allow, back.decision)
        assertEquals(IG_T, back.state.activeTarget)
    }

    // ---------- R4 ----------
    @Test
    fun `R4 tick while active adds elapsed time and accumulates sub-second remainders`() {
        var s = activeOn(IG_T, 1_000)
        s = SessionController.tick(s, mindful, 1_600, true).state
        assertEquals(0, s.activeSecondsToday)
        s = SessionController.onDetection(s, mindful, pos(IG_T), 1_600).state
        s = SessionController.tick(s, mindful, 2_200, true).state
        assertEquals(1, s.activeSecondsToday)
        assertEquals(200, s.pendingMs)
    }

    @Test
    fun `R4 a single tick never adds more than 2000 ms`() {
        var s = activeOn(IG_T, 1_000)
        s = SessionController.onDetection(s, mindful, pos(IG_T), 2_900).state
        s = SessionController.tick(s, mindful, 2_900, true).state // last tick was at activation (1000)
        assertEquals(1, s.activeSecondsToday) // 1900 ms -> 1 s + 900 pending
        val big = SessionController.onDetection(s, mindful, pos(IG_T), 9_000).state
        val after = SessionController.tick(big, mindful, 9_000, true).state
        assertEquals("6100 ms gap capped at 2000 ms", 3, after.activeSecondsToday)
    }

    @Test
    fun `R4 tick when inactive does not count`() {
        val s = RuntimeState(dayIso = day)
        val step = SessionController.tick(s, mindful, 5_000, true)
        assertEquals(Decision.Allow, step.decision)
        assertEquals(0, step.state.activeSecondsToday)
    }

    @Test
    fun `R4 screen off stops the session and clears the pass`() {
        val s = activeOn(IG_T, 1_000)
        val step = SessionController.tick(s, mindful, 2_000, false)
        assertEquals(Decision.Allow, step.decision)
        assertNull(step.state.activeTarget)
        assertNull(step.state.passTarget)
        assertEquals(0L, step.state.passUntilMs)
        assertEquals(0, step.state.activeSecondsToday)
    }

    @Test
    fun `R4 active without a positive for 3000 ms stops without counting`() {
        val s = activeOn(IG_T, 1_000)
        val step = SessionController.tick(s, mindful, 4_000, true)
        assertNull(step.state.activeTarget)
        assertEquals(0, step.state.activeSecondsToday)
        assertEquals(IG_T, step.state.passTarget)
    }

    @Test
    fun `R4 reaching the budget interrupts with daily limit, stops and clears the pass`() {
        val cfg = mindful.copy(dailyLimitSeconds = 10)
        var s = activeOn(IG_T, 1_000, cfg).copy(activeSecondsToday = 9)
        s = SessionController.onDetection(s, cfg, pos(IG_T), 2_000).state
        val step = SessionController.tick(s, cfg, 2_000, true)
        val i = interrupt(step.decision)
        assertEquals(InterruptionReason.DAILY_LIMIT_EXHAUSTED, i.reason)
        assertFalse(i.canContinue)
        assertEquals(0, i.remainingSecondsToday)
        assertEquals(10, step.state.activeSecondsToday)
        assertNull(step.state.activeTarget)
        assertNull(step.state.passTarget)
    }

    @Test
    fun `R4 pass expiry while watching interrupts with a mindful pause and stops`() {
        var s = activeOn(IG_T, 1_000)
        val until = s.passUntilMs
        s = SessionController.onDetection(s, mindful, pos(IG_T), until).state.copy(lastTickMs = until - 1_000)
        val step = SessionController.tick(s, mindful, until, true)
        val i = interrupt(step.decision)
        assertEquals(InterruptionReason.MINDFUL_PAUSE_ON_ENTRY, i.reason)
        assertTrue(i.canContinue)
        assertNull(step.state.activeTarget)
    }

    @Test
    fun `R4 target disabled mid-session stops counting`() {
        val s = activeOn(IG_T, 1_000)
        val cfg = mindful.copy(enabledTargets = setOf(YT_T))
        val step = SessionController.tick(s, cfg, 2_000, true)
        assertNull(step.state.activeTarget)
        assertEquals(0, step.state.activeSecondsToday)
    }

    // ---------- R5 ----------
    @Test
    fun `R5 continue grants at least 180 s or remaining plus 60 s and does not activate`() {
        val small = SessionController.onContinue(RuntimeState(dayIso = day, activeSecondsToday = 880), mindful, IG_T, 1_000)
        assertEquals(IG_T, small.passTarget)
        assertEquals(1_000 + 180_000L, small.passUntilMs)
        assertNull(small.activeTarget)
        val big = SessionController.onContinue(RuntimeState(dayIso = day), mindful, IG_T, 1_000)
        assertEquals(1_000 + 960_000L, big.passUntilMs)
    }

    // ---------- R6 ----------
    @Test
    fun `R6 step back stops and clears the pass`() {
        val s = activeOn(IG_T, 1_000)
        val back = SessionController.onStepBack(s)
        assertNull(back.activeTarget)
        assertNull(back.passTarget)
        assertEquals(0L, back.passUntilMs)
        val again = SessionController.onDetection(back, mindful, pos(IG_T), 2_000)
        assertEquals(InterruptionReason.MINDFUL_PAUSE_ON_ENTRY, interrupt(again.decision).reason)
    }

    // ---------- R7 ----------
    @Test
    fun `R7 clean day increments streak and resets today counters into history`() {
        val s = RuntimeState(dayIso = "2026-11-14", activeSecondsToday = 150, interceptionsToday = 4, passTarget = IG_T, passUntilMs = 9)
        val r = SessionController.rollover(s, mindful, "2026-11-15")
        assertEquals("2026-11-15", r.dayIso)
        assertEquals(1, r.streakDays)
        assertEquals(0, r.streakShields)
        assertEquals(0, r.activeSecondsToday)
        assertEquals(0, r.interceptionsToday)
        assertNull(r.passTarget)
        assertEquals(listOf(DayRecord("2026-11-14", 150, 4)), r.history)
    }

    @Test
    fun `R7 seventh clean day earns a shield and an over-goal day consumes it`() {
        val day6 = RuntimeState(dayIso = "2026-11-14", activeSecondsToday = 120, streakDays = 6, streakShields = 1)
        val day7 = SessionController.rollover(day6, mindful, "2026-11-15")
        assertEquals(7, day7.streakDays)
        assertEquals(2, day7.streakShields)
        val over = SessionController.rollover(day7.copy(activeSecondsToday = 901), mindful, "2026-11-16")
        assertEquals(7, over.streakDays)
        assertEquals(1, over.streakShields)
    }

    @Test
    fun `R7 shields are capped at 3`() {
        val s = RuntimeState(dayIso = "2026-11-14", streakDays = 13, streakShields = 3)
        assertEquals(3, SessionController.rollover(s, mindful, "2026-11-15").streakShields)
    }

    @Test
    fun `R7 over-goal day without shields resets the streak`() {
        val s = RuntimeState(dayIso = "2026-11-14", activeSecondsToday = 1_000, streakDays = 5)
        val r = SessionController.rollover(s, mindful, "2026-11-15")
        assertEquals(0, r.streakDays)
        assertEquals(0, r.streakShields)
    }

    @Test
    fun `R7 fully skipped days count as clean`() {
        val s = RuntimeState(dayIso = "2026-11-10", activeSecondsToday = 10, streakDays = 2)
        // 11-10 clean (+1), skipped 11..14 (4 days, +4) -> 7, which also earns a shield
        val r = SessionController.rollover(s, mindful, "2026-11-15")
        assertEquals(7, r.streakDays)
        assertEquals(1, r.streakShields)
    }

    @Test
    fun `R7 history keeps only 7 days`() {
        val old = (1..7).map { DayRecord("2026-11-0$it", it * 60, it) }
        val s = RuntimeState(dayIso = "2026-11-08", activeSecondsToday = 480, interceptionsToday = 8, history = old)
        val r = SessionController.rollover(s, mindful, "2026-11-09")
        assertEquals((2..8).map { "2026-11-0$it" }, r.history.map { it.dateIso })
    }

    @Test
    fun `R7 same day is a no-op and first run only sets the date`() {
        val s = RuntimeState(dayIso = day, activeSecondsToday = 30)
        assertEquals(s, SessionController.rollover(s, mindful, day))
        val fresh = SessionController.rollover(RuntimeState(), mindful, day)
        assertEquals(day, fresh.dayIso)
        assertEquals(0, fresh.streakDays)
    }

    // ---------- R8 ----------
    @Test
    fun `R8 7-day totals sum the last 6 history days plus today`() {
        val history = listOf(
            DayRecord("2026-11-08", 1_000, 100), // 7 days ago: outside the 7-day window
            DayRecord("2026-11-09", 60, 1),
            DayRecord("2026-11-14", 120, 2)
        )
        val s = RuntimeState(dayIso = day, activeSecondsToday = 30, interceptionsToday = 3, history = history)
        assertEquals(210, SessionController.secondsLast7Days(s))
        assertEquals(6, SessionController.interceptionsLast7Days(s))
        assertEquals(0, SessionController.secondsLast7Days(RuntimeState()))
    }

    // ---------- Codec ----------
    @Test
    fun `history codec round-trips and ignores malformed entries`() {
        val h = listOf(DayRecord("2026-11-13", 61, 2), DayRecord("2026-11-14", 0, 0))
        assertEquals(h, RuntimeCodec.decodeHistory(RuntimeCodec.encodeHistory(h)))
        assertEquals(emptyList<DayRecord>(), RuntimeCodec.decodeHistory(""))
        assertEquals(listOf(DayRecord("2026-11-14", 5, 1)), RuntimeCodec.decodeHistory("garbage;2026-11-14=5,1;x=y,z"))
    }

    @Test
    fun `targets codec keeps null as default, blank as empty and ignores unknown names`() {
        val all = ShortFormTarget.entries.toSet()
        assertEquals(all, RuntimeCodec.decodeTargets(null, all))
        assertEquals(emptySet<ShortFormTarget>(), RuntimeCodec.decodeTargets("", all))
        assertEquals(setOf(IG_T, TT_T), RuntimeCodec.decodeTargets("INSTAGRAM_REELS, NOPE,TIKTOK", all))
        assertEquals(setOf(YT_T), RuntimeCodec.decodeTargets(RuntimeCodec.encodeTargets(setOf(YT_T)), all))
    }

    @Test
    fun `persistable drops volatile session fields only`() {
        val s = activeOn(IG_T, 1_000).copy(activeSecondsToday = 5, interceptionsToday = 2, streakDays = 3)
        val p = SessionController.persistable(s)
        assertNull(p.activeTarget)
        assertEquals(0L, p.lastPositiveMs)
        assertEquals(0L, p.lastTickMs)
        assertEquals(5, p.activeSecondsToday)
        assertEquals(2, p.interceptionsToday)
        assertEquals(3, p.streakDays)
        assertEquals(IG_T, p.passTarget)
        assertEquals(s.passUntilMs, p.passUntilMs)
    }
}
