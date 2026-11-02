package com.scrolless.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scrolless.app.core.DetectionEngine
import com.scrolless.app.core.InterventionDecision
import com.scrolless.app.core.SessionAndStreakManager
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

    private val secondTicker = object : Runnable {
        override fun run() {
            val currentState = store.loadState()
            if (currentState.isCurrentlyInReelTab) {
                val nowSec = System.currentTimeMillis() / 1000L
                val (updated, decision) = SessionAndStreakManager.onActiveSecondTick(currentState, nowSec)
                store.saveState(updated)
                if (decision is InterventionDecision.ShowInterruption) {
                    launchInterruptionScreen(decision)
                }
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
        val root = rootInActiveWindow ?: return
        val pkg = event?.packageName?.toString() ?: root.packageName?.toString() ?: return
        if (pkg == applicationContext.packageName) return

        val viewIds = mutableSetOf<String>()
        collectViewIds(root, viewIds, depth = 0)

        if (store.isInspectorEnabled() && viewIds.isNotEmpty()) {
            store.recordInspectedViewIds(viewIds)
        }

        val snapshot = WindowSnapshot(packageName = pkg, viewIds = viewIds)
        val detection = detectionEngine.evaluateWindow(snapshot)
        val nowSec = System.currentTimeMillis() / 1000L

        val (updatedState, decision) = SessionAndStreakManager.onWindowEvaluated(
            state = store.loadState(),
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
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun launchInterruptionScreen(decision: InterventionDecision.ShowInterruption) {
        val intent = Intent(this, InterruptionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("target_name", decision.target.displayName)
            putExtra("reason_name", decision.reason.name)
            putExtra("can_continue", decision.canContinueAfterPause)
            putExtra("remaining_sec", decision.remainingSecondsToday)
        }
        startActivity(intent)
    }

    private fun collectViewIds(node: AccessibilityNodeInfo?, out: MutableSet<String>, depth: Int) {
        if (node == null || depth > 18 || out.size > 60) return
        node.viewIdResourceName?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            collectViewIds(node.getChild(i), out, depth + 1)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        tickHandler.removeCallbacks(secondTicker)
        instance = null
        super.onDestroy()
    }
}
