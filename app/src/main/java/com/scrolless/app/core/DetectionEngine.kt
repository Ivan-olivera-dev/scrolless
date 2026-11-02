package com.scrolless.app.core

enum class ShortFormTarget(val displayName: String, val packageName: String) {
    INSTAGRAM_REELS("Instagram Reels", "com.instagram.android"),
    YOUTUBE_SHORTS("YouTube Shorts", "com.google.android.youtube"),
    TIKTOK("TikTok", "com.zhiliaoapp.musically")
}

data class WindowSnapshot(
    val packageName: String,
    val viewIds: Set<String>,
    val contentDescriptions: Set<String> = emptySet()
)

data class DetectionResult(
    val isShortForm: Boolean,
    val target: ShortFormTarget? = null,
    val matchedSignal: String? = null
)

/**
 * Surgical detector that distinguishes short-form vertical feeds (Reels/Shorts/TikTok)
 * from normal app usage (Instagram DMs/stories/feed, long-form YouTube videos).
 */
class DetectionEngine(
    private val customReelViewIds: Set<String> = emptySet()
) {
    companion object {
        val TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill"
        )

        val INSTAGRAM_REEL_VIEW_IDS = setOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/clips_SwipeRefreshLayout"
        )

        val INSTAGRAM_SAFE_NON_REEL_VIEW_IDS = setOf(
            "com.instagram.android:id/direct_thread_toggle",
            "com.instagram.android:id/row_inbox_container"
        )

        val YOUTUBE_SHORTS_VIEW_IDS = setOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/shorts_container"
        )
    }

    fun evaluateWindow(snapshot: WindowSnapshot): DetectionResult {
        val pkg = snapshot.packageName

        if (pkg in TIKTOK_PACKAGES) {
            return DetectionResult(
                isShortForm = true,
                target = ShortFormTarget.TIKTOK,
                matchedSignal = "package:$pkg"
            )
        }

        if (pkg == "com.instagram.android") {
            if (snapshot.viewIds.any { it in INSTAGRAM_SAFE_NON_REEL_VIEW_IDS }) {
                return DetectionResult(isShortForm = false)
            }
            val matchedId = snapshot.viewIds.firstOrNull {
                it in INSTAGRAM_REEL_VIEW_IDS || it in customReelViewIds
            }
            if (matchedId != null) {
                return DetectionResult(
                    isShortForm = true,
                    target = ShortFormTarget.INSTAGRAM_REELS,
                    matchedSignal = matchedId
                )
            }
            return DetectionResult(isShortForm = false)
        }

        if (pkg == "com.google.android.youtube") {
            val matchedId = snapshot.viewIds.firstOrNull {
                it in YOUTUBE_SHORTS_VIEW_IDS || it in customReelViewIds
            }
            if (matchedId != null) {
                return DetectionResult(
                    isShortForm = true,
                    target = ShortFormTarget.YOUTUBE_SHORTS,
                    matchedSignal = matchedId
                )
            }
            return DetectionResult(isShortForm = false)
        }

        return DetectionResult(isShortForm = false)
    }
}
