package com.scrolless.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scrolless.app.BuildConfig
import com.scrolless.app.core.AppBlockScope
import com.scrolless.app.core.Decision
import com.scrolless.app.core.Detection
import com.scrolless.app.core.DetectionEngine
import com.scrolless.app.core.ProtectionConfig
import com.scrolless.app.core.RuntimeState
import com.scrolless.app.core.ScreenNode
import com.scrolless.app.core.SessionController
import com.scrolless.app.core.ShortFormTarget
import com.scrolless.app.core.Step
import com.scrolless.app.core.WindowSnapshot
import com.scrolless.app.data.LocalStore
import com.scrolless.app.ui.InterruptionActivity
import java.time.LocalDate

/**
 * Reads only the active window of watched apps (Instagram, YouTube, TikTok), feeds visible-node
 * snapshots into the pure [DetectionEngine] / [SessionController], and launches the interruption
 * screen. It never touches other apps: no media keys, no audio focus, no PiP dismissal, no BACK/HOME.
 */
class ScrollessAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ScrollessAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null

        private const val TAG = "ScrollessDiag"
        private const val POLL_MS = 1_000L
        private const val CONTENT_THROTTLE_MS = 250L
        private const val PERSIST_THROTTLE_MS = 1_000L
        private const val MAX_NODES = 1_500
        private const val MAX_DEPTH = 64
        private const val DIAG_MIN_HEIGHT = 0.3f
    }

    private lateinit var store: LocalStore
    private val handler = Handler(Looper.getMainLooper())

    private var config = ProtectionConfig()
    private var runtime = RuntimeState()
    private var lastPersisted: RuntimeState? = null
    private var lastPersistMs = 0L
    private var lastEvaluationMs = 0L
    private var trailingScheduled = false
    private var lastDetectionKey: String? = null

    private val configListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in LocalStore.CONFIG_KEYS) config = store.loadConfig()
    }

    private val poll = object : Runnable {
        override fun run() {
            evaluateActiveWindow(fromWindowState = false)
            val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val interactive = power?.isInteractive ?: true
            apply(SessionController.tick(runtime, config, now(), interactive))
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val trailingEvaluation = Runnable {
        trailingScheduled = false
        evaluateActiveWindow(fromWindowState = false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = LocalStore(applicationContext)
        config = store.loadConfig()
        runtime = store.loadRuntimeForToday(config)
        store.registerListener(configListener)
        instance = this
        handler.post(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::store.isInitialized) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            evaluateActiveWindow(fromWindowState = true)
            return
        }
        // Content / scroll events: throttle to 250 ms with a trailing evaluation.
        val elapsed = SystemClock.uptimeMillis() - lastEvaluationMs
        if (elapsed >= CONTENT_THROTTLE_MS) {
            evaluateActiveWindow(fromWindowState = false)
        } else if (!trailingScheduled) {
            trailingScheduled = true
            handler.postDelayed(trailingEvaluation, CONTENT_THROTTLE_MS - elapsed)
        }
    }

    /** Called by InterruptionActivity when the user continues after the breathing pause. */
    fun onContinueFromInterruption(target: ShortFormTarget) {
        rollIfNeeded()
        runtime = SessionController.onContinue(runtime, config, target, now())
        persist(force = true)
    }

    /** Called by InterruptionActivity when the user steps back (the activity itself goes Home). */
    fun onStepBackFromInterruption() {
        rollIfNeeded()
        runtime = SessionController.onStepBack(runtime)
        persist(force = true)
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun rollIfNeeded() {
        runtime = SessionController.rollover(runtime, config, LocalDate.now().toString())
    }

    private fun evaluateActiveWindow(fromWindowState: Boolean) {
        lastEvaluationMs = SystemClock.uptimeMillis()
        if (trailingScheduled) {
            handler.removeCallbacks(trailingEvaluation)
            trailingScheduled = false
        }
        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        }
        val pkg = root?.packageName?.toString().orEmpty()
        val snapshot = if (pkg in DetectionEngine.WATCHED_PACKAGES && root != null) {
            captureSnapshot(root, pkg)
        } else {
            WindowSnapshot(pkg, emptyList())
        }
        val detection = DetectionEngine.detect(snapshot, config.fullAppTargets)
        val step = apply(SessionController.onDetection(runtime, config, detection, now()))
        diagnose(snapshot, detection, step.decision, fromWindowState)
    }

    private fun apply(step: Step): Step {
        val before = runtime
        runtime = step.state
        rollIfNeeded()
        val decision = step.decision
        if (decision is Decision.Interrupt) {
            persist(force = true)
            if (decision.shouldLaunch) launchInterruption(decision)
        } else if (runtime != before) {
            persist(force = false)
        }
        return step
    }

    private fun persist(force: Boolean) {
        val snapshot = SessionController.persistable(runtime)
        if (snapshot == lastPersisted) return
        val nowUptime = SystemClock.uptimeMillis()
        if (!force && nowUptime - lastPersistMs < PERSIST_THROTTLE_MS) return
        store.saveRuntime(snapshot)
        lastPersisted = snapshot
        lastPersistMs = nowUptime
    }

    private fun launchInterruption(decision: Decision.Interrupt) {
        val intent = Intent(this, InterruptionActivity::class.java).apply {
            // NO_USER_ACTION: the system does not treat this launch as the user leaving the watched
            // app, so YouTube/Instagram never auto-enter Picture-in-Picture behind the pause screen.
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
            putExtra("target_id", decision.target.name)
            putExtra("target_name", decision.target.displayName)
            putExtra("reason_name", decision.reason.name)
            putExtra("can_continue", decision.canContinue)
            putExtra("remaining_sec", decision.remainingSecondsToday)
            // Whole-app block (fullAppTargets or TikTok): the screen must not promise DMs stay free.
            putExtra("whole_app", config.scopeOf(decision.target) == AppBlockScope.FULL_APP)
        }
        startActivity(intent)
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = runCatching {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds
            }.getOrNull()
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0) return bounds.width() to bounds.height()
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun captureSnapshot(root: AccessibilityNodeInfo, pkg: String): WindowSnapshot {
        val (width, height) = screenSize()
        val nodes = ArrayList<ScreenNode>(256)
        val rect = Rect()
        var visited = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= MAX_NODES || depth > MAX_DEPTH) return
            visited++
            // Invisible parents are still descended into; only the node itself is filtered.
            node.getBoundsInScreen(rect)
            ScreenNode.fromBounds(
                viewId = node.viewIdResourceName,
                selected = node.isSelected,
                visibleToUser = node.isVisibleToUser,
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
                screenWidth = width,
                screenHeight = height
            )?.let(nodes::add)
            for (i in 0 until node.childCount) {
                if (visited >= MAX_NODES) break
                val child = try {
                    node.getChild(i)
                } catch (_: Throwable) {
                    null
                } ?: continue
                visit(child, depth + 1)
            }
        }

        try {
            visit(root, 0)
        } catch (_: Throwable) {
            // A node can become stale mid-traversal; evaluate whatever was captured.
        }
        return WindowSnapshot(pkg, nodes)
    }

    private fun diagnose(snapshot: WindowSnapshot, detection: Detection, decision: Decision, fromWindowState: Boolean) {
        if (!BuildConfig.DEBUG) return
        val key = "${snapshot.packageName}|${detection.target}|${detection.signal}"
        val changed = key != lastDetectionKey
        lastDetectionKey = key
        if (!fromWindowState && !changed) return
        val entries = snapshot.nodes
            .filter { it.viewId != null && (it.heightFraction >= DIAG_MIN_HEIGHT || it.selected) }
            .joinToString(",") { node ->
                val e = DetectionEngine.entry(node.viewId!!)
                "$e(${"%.2f".format(node.heightFraction)}${if (node.selected) ",sel" else ""})"
            }
        val decisionLabel = when (decision) {
            is Decision.Allow -> "allow active=${runtime.activeTarget}"
            is Decision.Interrupt -> "interrupt ${decision.target} ${decision.reason} launch=${decision.shouldLaunch}"
        }
        Log.d(TAG, "pkg=${snapshot.packageName} signal=${detection.signal} decision=$decisionLabel nodes=[$entries]")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        handler.removeCallbacks(trailingEvaluation)
        if (::store.isInitialized) {
            store.unregisterListener(configListener)
            persist(force = true)
        }
        instance = null
        super.onDestroy()
    }
}
