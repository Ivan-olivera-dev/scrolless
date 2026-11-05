package com.scrolless.app.core

enum class ShortFormTarget(val displayName: String, val packageName: String) {
    INSTAGRAM_REELS("Instagram Reels", "com.instagram.android"),
    YOUTUBE_SHORTS("YouTube Shorts", "com.google.android.youtube"),
    TIKTOK("TikTok", "com.zhiliaoapp.musically")
}

data class WindowSnapshot(
    val packageName: String,
    val viewIds: Set<String>,
    val contentDescriptions: Set<String> = emptySet(),
    val selectedNodeLabels: Set<String> = emptySet()
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
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.aweme"
        )

        val INSTAGRAM_REEL_VIEW_IDS = setOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_root",
            "com.instagram.android:id/clips_viewer_content_pager",
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

        private val INSTAGRAM_REEL_ID_SUBSTRINGS = listOf(
            "clips_viewer",
            "clips_swipe"
        )

        private val YOUTUBE_SHORTS_ID_SUBSTRINGS = listOf(
            ":id/reel_",
            "reel_player",
            "reel_recycler",
            "reel_watch",
            "shorts_",
            "reel_time_bar",
            "reel_progress",
            "reel_rhs",
            "reel_dyn"
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
            val hasSafeNonReelId = snapshot.viewIds.any { id ->
                val lower = id.lowercase()
                lower.contains("direct_thread") ||
                    lower.contains("row_inbox") ||
                    lower.contains("message_list") ||
                    lower.contains("reel_viewer") ||
                    lower.contains("story_viewer") ||
                    lower.contains("reel_tray") ||
                    lower.contains("feed_timeline") ||
                    lower.contains("main_feed") ||
                    id in INSTAGRAM_SAFE_NON_REEL_VIEW_IDS
            }
            if (hasSafeNonReelId) {
                return DetectionResult(isShortForm = false)
            }

            if (isInstagramNonReelsTabSelected(snapshot.selectedNodeLabels)) {
                return DetectionResult(isShortForm = false)
            }

            val matchedId = snapshot.viewIds.firstOrNull { id ->
                val lower = id.lowercase()
                id in INSTAGRAM_REEL_VIEW_IDS ||
                    id in customReelViewIds ||
                    INSTAGRAM_REEL_ID_SUBSTRINGS.any { lower.contains(it) }
            }
            if (matchedId != null) {
                return DetectionResult(
                    isShortForm = true,
                    target = ShortFormTarget.INSTAGRAM_REELS,
                    matchedSignal = matchedId
                )
            }

            val matchedSelectedLabel = snapshot.selectedNodeLabels.firstOrNull { label ->
                val lower = label.lowercase().trim()
                lower == "reels" || lower.startsWith("reels,") || lower.startsWith("reels ")
            }
            if (matchedSelectedLabel != null) {
                return DetectionResult(
                    isShortForm = true,
                    target = ShortFormTarget.INSTAGRAM_REELS,
                    matchedSignal = "selectedTab:$matchedSelectedLabel"
                )
            }

            return DetectionResult(isShortForm = false)
        }

        if (pkg == "com.google.android.youtube") {
            val hasYoutubeNormalWatchPlayer = snapshot.viewIds.any { id ->
                val lower = id.lowercase()
                lower.contains("watch_player") || lower.contains("player_fragment")
            }

            val matchedId = snapshot.viewIds.firstOrNull { id ->
                val lower = id.lowercase()
                id in YOUTUBE_SHORTS_VIEW_IDS ||
                    id in customReelViewIds ||
                    YOUTUBE_SHORTS_ID_SUBSTRINGS.any { lower.contains(it) }
            }
            if (matchedId != null) {
                return DetectionResult(
                    isShortForm = true,
                    target = ShortFormTarget.YOUTUBE_SHORTS,
                    matchedSignal = matchedId
                )
            }

            val matchedSelectedLabel = snapshot.selectedNodeLabels.firstOrNull { label ->
                val lower = label.lowercase().trim()
                lower == "shorts" || lower.startsWith("shorts,") || lower.startsWith("shorts ")
            }
            if (matchedSelectedLabel != null) {
                return DetectionResult(
                    isShortForm = true,
                    target = ShortFormTarget.YOUTUBE_SHORTS,
                    matchedSignal = "selectedTab:$matchedSelectedLabel"
                )
            }

            if (!hasYoutubeNormalWatchPlayer) {
                val matchedDesc = snapshot.contentDescriptions.firstOrNull { desc ->
                    val lower = desc.lowercase()
                    lower.contains("remezclar este short") ||
                        lower.contains("remix this short") ||
                        lower.contains("usar este sonido") ||
                        lower.contains("use this sound") ||
                        lower.contains("reproductor de shorts") ||
                        lower.contains("shorts player")
                }
                if (matchedDesc != null) {
                    return DetectionResult(
                        isShortForm = true,
                        target = ShortFormTarget.YOUTUBE_SHORTS,
                        matchedSignal = "contentDesc:$matchedDesc"
                    )
                }
            }

            return DetectionResult(isShortForm = false)
        }

        return DetectionResult(isShortForm = false)
    }

    fun isExplicitNonShortFormInTargetApp(snapshot: WindowSnapshot): Boolean {
        if (snapshot.packageName == "com.instagram.android") {
            val hasSafeNonReelId = snapshot.viewIds.any { id ->
                val lower = id.lowercase()
                lower.contains("direct_thread") ||
                    lower.contains("row_inbox") ||
                    lower.contains("message_list") ||
                    lower.contains("reel_viewer") ||
                    lower.contains("story_viewer") ||
                    lower.contains("reel_tray") ||
                    lower.contains("feed_timeline") ||
                    lower.contains("main_feed") ||
                    id in INSTAGRAM_SAFE_NON_REEL_VIEW_IDS
            }
            if (hasSafeNonReelId) return true

            if (isInstagramNonReelsTabSelected(snapshot.selectedNodeLabels)) return true

            return false
        }

        if (snapshot.packageName == "com.google.android.youtube") {
            val hasNormalPlayerId = snapshot.viewIds.any { id ->
                val lower = id.lowercase()
                lower.contains("watch_player") || lower.contains("player_fragment")
            }
            if (hasNormalPlayerId) return true

            val hasNonShortsTabSelected = snapshot.selectedNodeLabels.any { label ->
                val lower = label.lowercase().trim()
                lower.contains("home") || lower.contains("inicio") ||
                    lower.contains("subscriptions") || lower.contains("suscripciones") ||
                    lower.contains("you") || lower.contains("tú") ||
                    lower.contains("biblioteca") || lower.contains("library")
            }
            if (hasNonShortsTabSelected) return true

            return false
        }

        return false
    }

    private fun isInstagramNonReelsTabSelected(selectedNodeLabels: Set<String>): Boolean {
        val hasReelsSelected = selectedNodeLabels.any { label ->
            val lower = label.lowercase().trim()
            lower == "reels" || lower.startsWith("reels,") || lower.startsWith("reels ")
        }
        if (hasReelsSelected) return false

        return selectedNodeLabels.any { label ->
            val lower = label.lowercase().trim()
            lower == "home" || lower == "inicio" ||
                lower.startsWith("home,") || lower.startsWith("inicio,") ||
                lower == "search and explore" || lower == "buscar y explorar" ||
                lower.startsWith("search and explore,") || lower.startsWith("buscar y explorar,") ||
                lower == "explore" || lower == "explorar" ||
                lower.startsWith("explore,") || lower.startsWith("explorar,") ||
                lower == "search" || lower == "buscar" ||
                lower.startsWith("search,") || lower.startsWith("buscar,") ||
                lower == "profile" || lower == "perfil" ||
                lower.startsWith("profile,") || lower.startsWith("perfil,")
        }
    }
}
