package com.scrolless.app

import com.scrolless.app.core.DetectionEngine
import com.scrolless.app.core.ScreenNode
import com.scrolless.app.core.ShortFormTarget
import com.scrolless.app.core.WindowSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val IG = "com.instagram.android"
private const val YT = "com.google.android.youtube"

internal fun igNode(entry: String, height: Float = 1f, selected: Boolean = false, width: Float = 1f) =
    ScreenNode("$IG:id/$entry", selected, height, width)

internal fun ytNode(entry: String, height: Float = 1f, selected: Boolean = false, width: Float = 1f) =
    ScreenNode("$YT:id/$entry", selected, height, width)

internal fun igSnapshot(vararg nodes: ScreenNode) = WindowSnapshot(IG, nodes.toList())
internal fun ytSnapshot(vararg nodes: ScreenNode) = WindowSnapshot(YT, nodes.toList())

/** Realistic Instagram Reels tab: full-height pager + full-height single media + selected Reels tab. */
internal fun igReelsTab() = igSnapshot(
    igNode("action_bar_root", 1f),
    igNode("clips_viewer_view_pager", 0.92f),
    igNode("clips_single_media_component", 0.92f),
    igNode("clips_ufi_component", 0.35f, width = 0.12f),
    igNode("feed_tab", 0.07f, width = 0.2f),
    igNode("clips_tab", 0.07f, selected = true, width = 0.2f),
    igNode("profile_tab", 0.07f, width = 0.2f)
)

/** Instagram home feed after the Reels fragment was visited: the hidden pager is NOT in the visible snapshot. */
internal fun igHomeFeed() = igSnapshot(
    igNode("action_bar_root", 1f),
    igNode("refreshable_container", 0.85f),
    igNode("row_feed_photo_profile_name", 0.04f, width = 0.5f),
    igNode("clips_video_container", 0.45f), // a reel preview embedded in the feed
    igNode("feed_tab", 0.07f, selected = true, width = 0.2f),
    igNode("clips_tab", 0.07f, width = 0.2f)
)

internal fun igDmInbox() = igSnapshot(
    igNode("direct_inbox_action_bar", 0.08f),
    igNode("inbox_refreshable_thread_list_recyclerview", 0.85f)
)

internal fun ytShortsPlayer() = ytSnapshot(
    ytNode("reel_watch_player", 1f),
    ytNode("reel_recycler", 1f),
    ytNode("pivot_bar", 0.07f)
)

internal fun ytHomeWithShortsShelf() = ytSnapshot(
    ytNode("results", 0.85f),
    ytNode("reel_recycler", 0.32f), // Shorts shelf in the home feed
    ytNode("pivot_bar", 0.07f)
)

internal fun ytLongVideo() = ytSnapshot(
    ytNode("watch_player", 0.3f),
    ytNode("watch_list", 0.6f)
)

class DetectionEngineTest {

    @Test
    fun `entry strips the package and id prefix`() {
        assertEquals("clips_tab", DetectionEngine.entry("com.instagram.android:id/clips_tab"))
        assertEquals("plain", DetectionEngine.entry("plain"))
    }

    @Test
    fun `Instagram Reels tab with tall pager and tall media is Reels`() {
        val d = DetectionEngine.detect(igReelsTab(), emptySet())
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, d.target)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, d.watchedTarget)
    }

    @Test
    fun `Instagram tall pager plus selected clips_tab is Reels even without media node`() {
        val d = DetectionEngine.detect(
            igSnapshot(igNode("clips_viewer_view_pager", 0.9f), igNode("clips_tab", 0.07f, selected = true)),
            emptySet()
        )
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, d.target)
    }

    @Test
    fun `Instagram tall pager alone without tall media or selected tab is not Reels`() {
        val d = DetectionEngine.detect(
            igSnapshot(igNode("clips_viewer_view_pager", 0.9f), igNode("clips_tab", 0.07f, selected = false)),
            emptySet()
        )
        assertNull(d.target)
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, d.watchedTarget)
    }

    @Test
    fun `Instagram small pager is not Reels even with media and selected tab`() {
        val d = DetectionEngine.detect(
            igSnapshot(
                igNode("clips_viewer_view_pager", 0.59f),
                igNode("clips_single_media_component", 0.9f),
                igNode("clips_tab", 0.07f, selected = true)
            ),
            emptySet()
        )
        assertNull(d.target)
    }

    @Test
    fun `Instagram pager exactly at TALL boundary counts as tall`() {
        val d = DetectionEngine.detect(
            igSnapshot(igNode("clips_viewer_view_pager", DetectionEngine.TALL), igNode("clips_single_media_component", DetectionEngine.TALL)),
            emptySet()
        )
        assertEquals(ShortFormTarget.INSTAGRAM_REELS, d.target)
    }

    @Test
    fun `Instagram home feed with embedded reel preview is not Reels`() {
        assertNull(DetectionEngine.detect(igHomeFeed(), emptySet()).target)
    }

    @Test
    fun `Instagram Stories reel_viewer excludes even with Reels-like nodes`() {
        val d = DetectionEngine.detect(
            igSnapshot(
                igNode("reel_viewer_root", 1f),
                igNode("clips_viewer_view_pager", 0.9f),
                igNode("clips_single_media_component", 0.9f)
            ),
            emptySet()
        )
        assertNull(d.target)
    }

    @Test
    fun `Instagram DM screens are excluded`() {
        for (entry in listOf(
            "direct_inbox_action_bar",
            "inbox_refreshable_thread_list_recyclerview",
            "message_thread_container",
            "row_thread_composer_container"
        )) {
            val d = DetectionEngine.detect(
                igSnapshot(
                    igNode(entry, 0.1f),
                    igNode("clips_viewer_view_pager", 0.9f),
                    igNode("clips_single_media_component", 0.9f),
                    igNode("clips_tab", 0.07f, selected = true)
                ),
                emptySet()
            )
            assertNull("$entry must exclude Reels", d.target)
        }
    }

    @Test
    fun `YouTube Shorts player is Shorts`() {
        for (entry in listOf("reel_watch_player", "reel_player_page_container", "reel_recycler")) {
            val d = DetectionEngine.detect(ytSnapshot(ytNode(entry, 0.95f)), emptySet())
            assertEquals("$entry tall must be Shorts", ShortFormTarget.YOUTUBE_SHORTS, d.target)
        }
    }

    @Test
    fun `YouTube Shorts shelf in the home feed is not Shorts`() {
        val d = DetectionEngine.detect(ytHomeWithShortsShelf(), emptySet())
        assertNull(d.target)
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, d.watchedTarget)
    }

    @Test
    fun `YouTube long-form video is not Shorts`() {
        assertNull(DetectionEngine.detect(ytLongVideo(), emptySet()).target)
    }

    @Test
    fun `all TikTok packages are always TikTok`() {
        for (pkg in listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.aweme")) {
            val d = DetectionEngine.detect(WindowSnapshot(pkg, emptyList()), emptySet())
            assertEquals(ShortFormTarget.TIKTOK, d.target)
            assertEquals(ShortFormTarget.TIKTOK, d.watchedTarget)
        }
    }

    @Test
    fun `unwatched packages have no target and no watched target`() {
        val d = DetectionEngine.detect(WindowSnapshot("com.spotify.music", listOf(ScreenNode("x:id/clips_viewer_view_pager", false, 1f, 1f))), emptySet())
        assertNull(d.target)
        assertNull(d.watchedTarget)
    }

    @Test
    fun `full-app Instagram blocks DMs and Stories with signal full-app`() {
        val full = setOf(ShortFormTarget.INSTAGRAM_REELS)
        for (snap in listOf(igDmInbox(), igHomeFeed(), igSnapshot(igNode("reel_viewer_root")))) {
            val d = DetectionEngine.detect(snap, full)
            assertEquals(ShortFormTarget.INSTAGRAM_REELS, d.target)
            assertEquals("full-app", d.signal)
        }
    }

    @Test
    fun `full-app YouTube blocks long-form videos and leaves Instagram surgical`() {
        val full = setOf(ShortFormTarget.YOUTUBE_SHORTS)
        val yt = DetectionEngine.detect(ytLongVideo(), full)
        assertEquals(ShortFormTarget.YOUTUBE_SHORTS, yt.target)
        assertEquals("full-app", yt.signal)
        assertNull(DetectionEngine.detect(igDmInbox(), full).target)
    }

    @Test
    fun `fromBounds drops nodes not visible to the user`() {
        assertNull(ScreenNode.fromBounds("$IG:id/clips_viewer_view_pager", false, false, 0, 0, 1080, 2400, 1080, 2400))
    }

    @Test
    fun `fromBounds drops nodes with no visible area on screen`() {
        // Retained fragment laid out off-screen to the right
        assertNull(ScreenNode.fromBounds("$IG:id/clips_viewer_view_pager", false, true, 1080, 0, 2160, 2400, 1080, 2400))
        // Zero height
        assertNull(ScreenNode.fromBounds("x", false, true, 0, 100, 1080, 100, 1080, 2400))
    }

    @Test
    fun `fromBounds clips bounds to the screen and computes fractions`() {
        val node = ScreenNode.fromBounds("$IG:id/clips_viewer_view_pager", true, true, -100, -200, 1080, 1200, 1080, 2400)
        assertNotNull(node)
        assertEquals(0.5f, node!!.heightFraction, 0.0001f)
        assertEquals(1f, node.widthFraction, 0.0001f)
        assertEquals(true, node.selected)
    }
}
