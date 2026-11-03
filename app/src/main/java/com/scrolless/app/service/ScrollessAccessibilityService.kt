package com.scrolless.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scrolless.app.core.DetectionEngine
import com.scrolless.app.core.InterventionDecision
import com.scrolless.app.core.SessionAndStreakManager
import com.scrolless.app.core.ShortFormTarget
import com.scrolless.app.core.WindowSnapshot
import com.scrolless.app.data.LocalStore
import com.scrolless.app.ui.InterruptionActivity

class ScrollessAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ScrollessAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    private lateinit var store: LocalStore
    private val detectionEngine = DetectionEngine()
    private val tickHandler = Handler(Looper.getMainLooper())
    private var lastInterruptionLaunchMs: Long = 0L
    private val ignoredSystemPackages = setOf("com.android.systemui", "android")

    private val secondTicker = object : Runnable {
        override fun run() {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val currentState = store.loadState()
            if (powerManager != null && !powerManager.isInteractive) {
                if (currentState.isCurrentlyInReelTab || currentState.mindfulAllowanceTarget != null) {
                    store.saveState(SessionAndStreakManager.onUserBouncedBack(currentState))
                }
                tickHandler.postDelayed(this, 1000L)
                return
            }

            if (currentState.isCurrentlyInReelTab) {
                val nowSec = System.currentTimeMillis() / 1000L
                val (updated, decision) = SessionAndStreakManager.onActiveSecondTick(currentState, nowSec)
                store.saveState(updated)
                if (decision is InterventionDecision.ShowInterruption) {
                    launchInterruptionScreen(decision)
                }
            } else {
                dismissPictureInPictureWindows()
            }
            tickHandler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = LocalStore(applicationContext)
        instance = this
        tickHandler.post(secondTicker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow
        val source = event?.source
        val pkg = event?.packageName?.toString() ?: root?.packageName?.toString() ?: source?.packageName?.toString() ?: return
        if (pkg == applicationContext.packageName) return
        val lowerPkg = pkg.lowercase()
        if (pkg in ignoredSystemPackages || lowerPkg.contains("inputmethod") || lowerPkg.contains("keyboard")) return

        val isWatchedApp = pkg == "com.instagram.android" ||
            pkg == "com.google.android.youtube" ||
            pkg in DetectionEngine.TIKTOK_PACKAGES

        if (!isWatchedApp) {
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val currentState = store.loadState()
                val nowSec = System.currentTimeMillis() / 1000L
                val grantStartSec = currentState.mindfulAllowanceUntilEpochSec - 180L
                val inTransitionGrace = currentState.mindfulAllowanceUntilEpochSec > nowSec &&
                    (nowSec - grantStartSec) in 0L..3L
                if (!inTransitionGrace && (currentState.isCurrentlyInReelTab || currentState.mindfulAllowanceTarget != null)) {
                    store.saveState(SessionAndStreakManager.onUserBouncedBack(currentState))
                }
            }
            return
        }

        val data = NodeData()
        root?.let { traverseNode(it, data, depth = 0) }
        if (data.visitedCount < 400 && source != null && source != root) {
            traverseNode(source, data, depth = 0)
        }

        if (store.isInspectorEnabled() && data.viewIds.isNotEmpty()) {
            store.recordInspectedViewIds(data.viewIds)
        }

        val snapshot = WindowSnapshot(
            packageName = pkg,
            viewIds = data.viewIds,
            contentDescriptions = data.contentDescriptions,
            selectedNodeLabels = data.selectedNodeLabels
        )
        val detection = detectionEngine.evaluateWindow(snapshot)
        val currentState = store.loadState()

        val isTargetApp = (currentState.currentTarget?.packageName == pkg) ||
            (currentState.currentTarget == ShortFormTarget.TIKTOK && pkg in DetectionEngine.TIKTOK_PACKAGES)

        if (!detection.isShortForm && currentState.isCurrentlyInReelTab && isTargetApp) {
            if (!detectionEngine.isExplicitNonShortFormInTargetApp(snapshot)) {
                // Partial TYPE_WINDOW_CONTENT_CHANGED sub-node event inside Instagram/YouTube while watching Reels/Shorts; ignore so timer keeps ticking
                return
            }
        }

        val nowSec = System.currentTimeMillis() / 1000L
        val (updatedState, decision) = SessionAndStreakManager.onWindowEvaluated(
            state = currentState,
            detection = detection,
            nowEpochSec = nowSec
        )
        store.saveState(updatedState)

        if (decision is InterventionDecision.ShowInterruption) {
            launchInterruptionScreen(decision)
        }
    }

    fun bounceBackFromShortForm() {
        val updated = SessionAndStreakManager.onUserBouncedBack(store.loadState())
        store.saveState(updated)
        pauseActiveMediaPlayback()

        // 1. YouTube/Instagram is now the foreground window.
        // Send BACK to YouTube so it closes the Shorts video player cleanly!
        tickHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            dismissPictureInPictureWindows()
        }, 80L)

        // 2. Second dismissal pass
        tickHandler.postDelayed({
            dismissPictureInPictureWindows()
        }, 160L)

        // 3. Now send HOME to return to Android Home Screen cleanly without PiP!
        tickHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
            dismissPictureInPictureWindows()
        }, 250L)

        // 4. Clean up any trailing PiP window after returning to Home
        tickHandler.postDelayed({
            dismissPictureInPictureWindows()
        }, 500L)
    }

    fun dismissPictureInPictureWindows() {
        try {
            val windowList = windows ?: return
            for (window in windowList) {
                if (window.isInPictureInPictureMode) {
                    val root = window.root ?: continue
                    root.performAction(AccessibilityNodeInfo.ACTION_DISMISS)
                    root.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id)
                    clickCloseInNode(root)
                }
            }
        } catch (_: Throwable) {
            // Safe fallback if window access is restricted
        }
    }

    private fun clickCloseInNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        val id = node.viewIdResourceName?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        if (id.contains("close") || id.contains("dismiss") ||
            desc.contains("close") || desc.contains("cerrar") || desc.contains("dismiss") ||
            text.contains("close") || text.contains("cerrar")
        ) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        for (i in 0 until node.childCount) {
            clickCloseInNode(node.getChild(i))
        }
    }

    fun pauseActiveMediaPlayback() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener { /* no-op */ }
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } catch (_: Throwable) {
            // Audio focus failure ignored safely
        }
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Throwable) {
            // Media key failure ignored safely
        }
    }

    private fun launchInterruptionScreen(decision: InterventionDecision.ShowInterruption) {
        val now = System.currentTimeMillis()
        if (now - lastInterruptionLaunchMs < 900L) return
        lastInterruptionLaunchMs = now

        pauseActiveMediaPlayback()
        val intent = Intent(this, InterruptionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("target_id", decision.target.name)
            putExtra("target_name", decision.target.displayName)
            putExtra("reason_name", decision.reason.name)
            putExtra("can_continue", decision.canContinueAfterPause)
            putExtra("remaining_sec", decision.remainingSecondsToday)
        }
        startActivity(intent)

        tickHandler.postDelayed({
            dismissPictureInPictureWindows()
        }, 200L)
        tickHandler.postDelayed({
            dismissPictureInPictureWindows()
        }, 450L)
    }

    private class NodeData {
        val viewIds = mutableSetOf<String>()
        val selectedNodeLabels = mutableSetOf<String>()
        val contentDescriptions = mutableSetOf<String>()
        var visitedCount = 0
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, data: NodeData, depth: Int) {
        if (node == null || depth > 35 || data.visitedCount >= 400) return
        data.visitedCount++

        node.viewIdResourceName?.let { data.viewIds.add(it) }

        val desc = node.contentDescription?.toString()
        val text = node.text?.toString()

        if (!desc.isNullOrBlank()) {
            data.contentDescriptions.add(desc)
        }
        if (!text.isNullOrBlank()) {
            data.contentDescriptions.add(text)
        }

        if (node.isSelected) {
            if (!desc.isNullOrBlank()) {
                data.selectedNodeLabels.add(desc)
            }
            if (!text.isNullOrBlank()) {
                data.selectedNodeLabels.add(text)
            }
        }

        for (i in 0 until node.childCount) {
            if (data.visitedCount >= 400) break
            traverseNode(node.getChild(i), data, depth + 1)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        tickHandler.removeCallbacks(secondTicker)
        instance = null
        super.onDestroy()
    }
}
