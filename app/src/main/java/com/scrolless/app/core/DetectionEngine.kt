package com.scrolless.app.core

enum class ShortFormTarget(val displayName: String, val packageName: String) {
    INSTAGRAM_REELS("Instagram Reels", "com.instagram.android"),
    YOUTUBE_SHORTS("YouTube Shorts", "com.google.android.youtube"),
    TIKTOK("TikTok", "com.zhiliaoapp.musically")
}

/** How much of a watched app is intercepted. TikTok is always whole-app. */
enum class AppBlockScope { SURGICAL, FULL_APP }

/**
 * One on-screen node. Only nodes visible to the user with a positive visible area are captured;
 * fractions are the visible bounds clipped to the screen divided by the screen size.
 * No text or content descriptions are ever captured (privacy).
 */
data class ScreenNode(
    val viewId: String?,
    val selected: Boolean,
    val heightFraction: Float,
    val widthFraction: Float
) {
    companion object {
        /** Builds a node from raw screen bounds, or null when it is invisible or has no on-screen area. */
        fun fromBounds(
            viewId: String?,
            selected: Boolean,
            visibleToUser: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            screenWidth: Int,
            screenHeight: Int
        ): ScreenNode? {
            if (!visibleToUser || screenWidth <= 0 || screenHeight <= 0) return null
            val width = right.coerceAtMost(screenWidth) - left.coerceAtLeast(0)
            val height = bottom.coerceAtMost(screenHeight) - top.coerceAtLeast(0)
            if (width <= 0 || height <= 0) return null
            return ScreenNode(
                viewId = viewId,
                selected = selected,
                heightFraction = height.toFloat() / screenHeight,
                widthFraction = width.toFloat() / screenWidth
            )
        }
    }
}

data class WindowSnapshot(
    val packageName: String,
    val nodes: List<ScreenNode>
)

/**
 * [target] is the short-form surface on screen (null = negative); [watchedTarget] is the target
 * owning the package, used to tell a same-app transient frame from leaving the app.
 */
data class Detection(
    val target: ShortFormTarget?,
    val watchedTarget: ShortFormTarget?,
    val signal: String
)

/**
 * Surgical detector based only on visible, large on-screen nodes (rules aligned with StopScroll,
 * 2026-10-31, Instagram 453). No label or content-description heuristics.
 */
object DetectionEngine {
    const val TALL = 0.6f

    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    val TIKTOK_PACKAGES = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically.go",
        "com.ss.android.ugc.aweme"
    )

    val WATCHED_PACKAGES: Set<String> = TIKTOK_PACKAGES + INSTAGRAM_PACKAGE + YOUTUBE_PACKAGE

    private val INSTAGRAM_EXCLUSION_ENTRIES = setOf(
        "direct_inbox_action_bar",
        "inbox_refreshable_thread_list_recyclerview",
        "message_thread_container",
        "row_thread_composer_container"
    )

    private val YOUTUBE_SHORTS_ENTRIES = setOf(
        "reel_watch_player",
        "reel_player_page_container",
        "reel_recycler"
    )

    fun entry(viewId: String): String = viewId.substringAfter(":id/")

    fun watchedTargetFor(packageName: String): ShortFormTarget? = when {
        packageName == INSTAGRAM_PACKAGE -> ShortFormTarget.INSTAGRAM_REELS
        packageName == YOUTUBE_PACKAGE -> ShortFormTarget.YOUTUBE_SHORTS
        packageName in TIKTOK_PACKAGES -> ShortFormTarget.TIKTOK
        else -> null
    }

    fun detect(snapshot: WindowSnapshot, fullAppTargets: Set<ShortFormTarget>): Detection {
        val watched = watchedTargetFor(snapshot.packageName)
            ?: return Detection(null, null, "unwatched")

        if (watched == ShortFormTarget.TIKTOK) return Detection(watched, watched, "tiktok")
        if (watched in fullAppTargets) return Detection(watched, watched, "full-app")

        return when (watched) {
            ShortFormTarget.INSTAGRAM_REELS -> detectInstagram(snapshot, watched)
            ShortFormTarget.YOUTUBE_SHORTS -> detectYouTube(snapshot, watched)
            ShortFormTarget.TIKTOK -> Detection(watched, watched, "tiktok")
        }
    }

    private fun detectInstagram(snapshot: WindowSnapshot, watched: ShortFormTarget): Detection {
        var tallPager = false
        var tallMedia = false
        var reelsTabSelected = false
        for (node in snapshot.nodes) {
            val id = node.viewId ?: continue
            val e = entry(id)
            if (e.startsWith("reel_viewer")) return Detection(null, watched, "ig-exclusion:stories")
            if (e in INSTAGRAM_EXCLUSION_ENTRIES) return Detection(null, watched, "ig-exclusion:$e")
            when (e) {
                "clips_viewer_view_pager" -> if (node.heightFraction >= TALL) tallPager = true
                "clips_single_media_component" -> if (node.heightFraction >= TALL) tallMedia = true
                "clips_tab" -> if (node.selected) reelsTabSelected = true
            }
        }
        return if (tallPager && (tallMedia || reelsTabSelected)) {
            Detection(watched, watched, if (tallMedia) "ig-reels:media" else "ig-reels:tab")
        } else {
            Detection(null, watched, "ig-none")
        }
    }

    private fun detectYouTube(snapshot: WindowSnapshot, watched: ShortFormTarget): Detection {
        val match = snapshot.nodes.firstOrNull { node ->
            val id = node.viewId
            id != null && node.heightFraction >= TALL && entry(id) in YOUTUBE_SHORTS_ENTRIES
        }
        return if (match != null) {
            Detection(watched, watched, "yt-shorts:${entry(match.viewId!!)}")
        } else {
            Detection(null, watched, "yt-none")
        }
    }
}
