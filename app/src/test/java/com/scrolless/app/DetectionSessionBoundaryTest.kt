package com.scrolless.app

import com.scrolless.app.core.Decision
import com.scrolless.app.core.DetectionEngine
import com.scrolless.app.core.ProtectionConfig
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.RuntimeState
import com.scrolless.app.core.ScreenNode
import com.scrolless.app.core.SessionController
import com.scrolless.app.core.ShortFormTarget
import com.scrolless.app.core.WindowSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionSessionBoundaryTest {
    private val IG = "com.instagram.android"
    private val YT = "com.google.android.youtube"
    private fun n(pkg: String, id: String, h: Float, sel: Boolean = false) = ScreenNode("$pkg:id/$id", sel, h, 1f)
    private fun ig(vararg nodes: ScreenNode) = WindowSnapshot(IG, nodes.toList())
    private fun yt(vararg nodes: ScreenNode) = WindowSnapshot(YT, nodes.toList())
    private fun t(s: WindowSnapshot, full: Set<ShortFormTarget> = emptySet()) = DetectionEngine.detect(s, full).target

    @Test
    fun `ig rule pager tall and media tall or tab selected`() {
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, t(ig(n(IG, "clips_viewer_view_pager", 0.6f), n(IG, "clips_single_media_component", 0.6f))))
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, t(ig(n(IG, "clips_viewer_view_pager", 0.6f), n(IG, "clips_tab", 0.05f, sel = true))))
        assertNull(t(ig(n(IG, "clips_viewer_view_pager", 0.59f), n(IG, "clips_single_media_component", 0.9f), n(IG, "clips_tab", 0.05f, true))))
        assertNull(t(ig(n(IG, "clips_viewer_view_pager", 0.9f))))
        assertNull(t(ig(n(IG, "clips_viewer_view_pager", 0.9f), n(IG, "clips_single_media_component", 0.59f), n(IG, "clips_tab", 0.05f, false))))
        assertNull(t(ig(n(IG, "clips_single_media_component", 0.9f), n(IG, "clips_tab", 0.05f, true))))
        assertNull(t(ig()))
    }

    @Test
    fun `ig exclusions stories and dm win over reels signals`() {
        val reels = arrayOf(n(IG, "clips_viewer_view_pager", 0.9f), n(IG, "clips_single_media_component", 0.9f))
        for (ex in listOf(
            "reel_viewer_root", "reel_viewer_media_layout", "direct_inbox_action_bar",
            "inbox_refreshable_thread_list_recyclerview", "message_thread_container", "row_thread_composer_container"
        )) {
            assertNull(ex, t(ig(*reels, n(IG, ex, 0.1f))))
        }
    }

    @Test
    fun `hidden or offscreen nodes are not captured`() {
        assertNull(ScreenNode.fromBounds("x", false, false, 0, 0, 1080, 2400, 1080, 2400))
        assertNull(ScreenNode.fromBounds("x", false, true, 0, 2400, 1080, 4800, 1080, 2400))
        assertNull(ScreenNode.fromBounds("x", false, true, 0, 0, 1080, 2400, 0, 2400))
        val clipped = ScreenNode.fromBounds("x", false, true, 0, -2000, 1080, 600, 1080, 2400)!!
        assertEquals(0.25f, clipped.heightFraction, 0.0001f)
        assertTrue(clipped.heightFraction < DetectionEngine.TALL)
    }

    @Test
    fun `yt shorts tall ids only and full app toggles`() {
        for (id in listOf("reel_watch_player", "reel_player_page_container")) {
            assertEquals(id, ShortFormTarget.YOUTUBE_SHORTS, t(yt(n(YT, id, 0.6f))))
            assertNull(id, t(yt(n(YT, id, 0.3f))))
        }
        assertNull(t(yt(n(YT, "watch_player", 0.9f), n(YT, "reel_shelf", 0.3f))))
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, t(yt(), setOf(ShortFormTarget.YOUTUBE_SHORTS)))
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, t(ig(), setOf(ShortFormTarget.INSTAGRAM_REELS)))
        assertNull(t(ig(), setOf(ShortFormTarget.YOUTUBE_SHORTS)))
        assertNull(t(WindowSnapshot("com.android.launcher3", emptyList()), ShortFormTarget.entries.toSet()))
    }

    private val cfg = ProtectionConfig(ProtectionMode.MINDFUL_BUDGET, 900, ShortFormTarget.entries.toSet(), emptySet())
    private val reels = ig(n(IG, "clips_viewer_view_pager", 0.9f), n(IG, "clips_single_media_component", 0.9f))
    private val feed = ig(n(IG, "main_feed", 0.9f))

    /** Reels entered, breathing pause continued, then a positive at t=100 ms makes the session active. */
    private fun active(): RuntimeState {
        var s = RuntimeState(dayIso = "2026-11-15")
        val first = SessionController.onDetection(s, cfg, DetectionEngine.detect(reels, emptySet()), 0)
        assertTrue(first.decision is Decision.Interrupt)
        s = SessionController.onContinue(first.state, cfg, ShortFormTarget.INSTAGRAM_REELS, 0)
        s = SessionController.onDetection(s, cfg, DetectionEngine.detect(reels, emptySet()), 100).state
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, s.activeTarget)
        return s
    }

    @Test
    fun `in app negative stops after 1_5 s grace and app change immediately`() {
        val s = active()
        assertEquals(
            ShortFormTarget.INSTAGRAM_REELS,
            SessionController.onDetection(s, cfg, DetectionEngine.detect(feed, emptySet()), 1_599).state.activeTarget
        )
        assertNull(SessionController.onDetection(s, cfg, DetectionEngine.detect(feed, emptySet()), 1_600).state.activeTarget)
        assertNull(SessionController.onDetection(s, cfg, DetectionEngine.detect(WindowSnapshot("com.android.launcher3", emptyList()), emptySet()), 101).state.activeTarget)
        assertNull(SessionController.onDetection(s, cfg, DetectionEngine.detect(yt(), emptySet()), 101).state.activeTarget)
        assertNull(SessionController.onDetection(s, cfg, DetectionEngine.detect(WindowSnapshot("", emptyList()), emptySet()), 101).state.activeTarget)
    }

    @Test
    fun `ticks without positive detections count at most 3 s`() {
        var s = active()
        var now = 100L
        repeat(120) { now += 1_000; s = SessionController.tick(s, cfg, now, true).state }
        assertNull(s.activeTarget)
        assertTrue("counted=${s.activeSecondsToday}", s.activeSecondsToday <= 3)

        s = active()
        for (d in listOf(400L, 900L, 1_300L, 1_999L, 2_500L, 2_999L, 3_000L, 3_500L, 10_000L)) {
            s = SessionController.tick(s, cfg, 100 + d, true).state
        }
        assertNull(s.activeTarget)
        assertTrue("counted=${s.activeSecondsToday}", s.activeSecondsToday <= 3)
    }

    @Test
    fun `in app negatives with ticks stop counting within grace`() {
        var s = active()
        var now = 100L
        repeat(20) {
            now += 250
            s = SessionController.onDetection(s, cfg, DetectionEngine.detect(feed, emptySet()), now).state
            if (now % 1000L == 100L) s = SessionController.tick(s, cfg, now, true).state
        }
        assertNull(s.activeTarget)
        assertTrue("counted=${s.activeSecondsToday}", s.activeSecondsToday <= 2)
    }

    @Test
    fun `disabling the target while active stops counting on next tick`() {
        val s = active()
        val off = cfg.copy(enabledTargets = setOf(ShortFormTarget.TIKTOK))
        assertNull(SessionController.tick(s, off, 1_100, true).state.activeTarget)
    }
}
