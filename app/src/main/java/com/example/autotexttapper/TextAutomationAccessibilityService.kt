package com.example.autotexttapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * All user-configurable text, delay, and gesture values for the automation
 * routine. Edit these constants to retune behaviour without touching the
 * state machine logic below.
 */
private object AutomationConfig {
    const val LIKE_VIDEO_TEXT = "Like video"
    const val SKIP_TEXT = "Skip"
    const val LOADING_TEXT = "Loading"

    const val INITIAL_DELAY_MS = 5000L
    const val WAIT_AFTER_LIKE_MS = 5000L
    const val WAIT_AFTER_SKIP_MS = 4000L
    const val MAIN_SCAN_INTERVAL_MS = 1000L
    const val LOADING_SETTLE_DELAY_MS = 500L
    const val LOADING_TIMEOUT_MS = 30000L

    const val TAP_DURATION_MS = 60L
    const val DOUBLE_TAP_GAP_MS = 150L

    /** Gap between the two GLOBAL_ACTION_RECENTS presses that "double tap" the recents
     *  button to jump straight to the previously used app (same as pressing Overview twice). */
    const val RECENTS_DOUBLE_TAP_GAP_MS = 150L
}

/** Finite states for the automation routine. Exactly one action runs per transition. */
enum class AutomationState {
    IDLE,
    INITIAL_WAIT,
    FIND_LIKE_OR_SKIP,
    WAIT_AFTER_LIKE,
    DOUBLE_TAP_CENTER,
    OPEN_RECENTS,
    SWITCH_TO_PREVIOUS_APP,
    WAIT_FOR_LOADING,
    WAIT_FOR_LOADING_TO_FINISH,
    WAIT_AFTER_LOADING_FINISH,
    WAIT_AFTER_SKIP
}

/**
 * User-controlled accessibility automation service.
 *
 * Safety scope: this service only reads visible accessibility text/content
 * descriptions and performs clicks/gestures while explicitly started from
 * MainActivity. It never takes screenshots, never reads screen pixels, and
 * performs no action until the device owner presses Start. Pressing Stop
 * (or destroying the service) cancels all pending work immediately.
 */
class TextAutomationAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var state: AutomationState = AutomationState.IDLE

    /** Incremented on every Start/Stop so stale delayed callbacks can detect they're obsolete. */
    private var sessionId: Int = 0

    private var mainScanScheduled = false
    private var loadingCheckScheduled = false
    private var loadingWaitStartElapsed = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }
        when (state) {
            AutomationState.FIND_LIKE_OR_SKIP -> scheduleMainScan(sessionId, 0L)
            AutomationState.WAIT_FOR_LOADING,
            AutomationState.WAIT_FOR_LOADING_TO_FINISH -> scheduleLoadingCheck(sessionId, 0L)
            else -> Unit
        }
    }

    override fun onInterrupt() {
        stopAutomationInternal()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopAutomationInternal()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopAutomationInternal()
        instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Start / Stop
    // ------------------------------------------------------------------

    private fun startAutomationInternal() {
        // Cancel anything left over from a previous run before starting fresh.
        handler.removeCallbacksAndMessages(null)
        mainScanScheduled = false
        loadingCheckScheduled = false
        sessionId++
        val session = sessionId

        state = AutomationState.INITIAL_WAIT
        AutomationStatusHolder.update(getString(R.string.status_started_initial_wait))

        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            state = AutomationState.FIND_LIKE_OR_SKIP
            AutomationStatusHolder.update(getString(R.string.status_searching))
            scheduleMainScan(session, 0L)
        }, AutomationConfig.INITIAL_DELAY_MS)
    }

    private fun stopAutomationInternal() {
        sessionId++ // invalidates every in-flight closure immediately
        handler.removeCallbacksAndMessages(null)
        mainScanScheduled = false
        loadingCheckScheduled = false
        state = AutomationState.IDLE
        AutomationStatusHolder.update(getString(R.string.status_stopped))
    }

    private fun isCurrentSession(session: Int): Boolean = session == sessionId

    // ------------------------------------------------------------------
    // B. Main scan rule — Like video always has priority over Skip
    // ------------------------------------------------------------------

    private fun scheduleMainScan(session: Int, delayMs: Long) {
        if (!isCurrentSession(session) || state != AutomationState.FIND_LIKE_OR_SKIP) return
        if (mainScanScheduled) return
        mainScanScheduled = true
        handler.postDelayed({
            mainScanScheduled = false
            performMainScan(session)
        }, delayMs)
    }

    private fun performMainScan(session: Int) {
        if (!isCurrentSession(session) || state != AutomationState.FIND_LIKE_OR_SKIP) return

        val root = rootInActiveWindow
        val likeNode = findNodeByText(root, AutomationConfig.LIKE_VIDEO_TEXT)
        if (likeNode != null) {
            attemptClick(likeNode) { clicked ->
                if (!isCurrentSession(session)) return@attemptClick
                if (clicked) {
                    onLikeVideoClicked(session)
                } else {
                    scheduleMainScan(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                }
            }
            return
        }

        val skipNode = findNodeByText(root, AutomationConfig.SKIP_TEXT)
        if (skipNode != null) {
            attemptClick(skipNode) { clicked ->
                if (!isCurrentSession(session)) return@attemptClick
                if (clicked) {
                    onSkipClicked(session)
                } else {
                    scheduleMainScan(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                }
            }
            return
        }

        // Neither text visible: don't touch the screen, wait, scan again.
        scheduleMainScan(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
    }

    // ------------------------------------------------------------------
    // C. Like video route
    // ------------------------------------------------------------------

    private fun onLikeVideoClicked(session: Int) {
        state = AutomationState.WAIT_AFTER_LIKE
        AutomationStatusHolder.update(getString(R.string.status_like_clicked))
        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            state = AutomationState.DOUBLE_TAP_CENTER
            AutomationStatusHolder.update(getString(R.string.status_double_tap))
            doubleTapCentre { success ->
                if (!isCurrentSession(session)) return@doubleTapCentre
                if (success) {
                    switchToPreviousApp(session)
                } else {
                    abortRouteToMainScan(session)
                }
            }
        }, AutomationConfig.WAIT_AFTER_LIKE_MS)
    }

    /**
     * Double-tap the Recents/Overview action: one GLOBAL_ACTION_RECENTS press opens the
     * recent-apps screen, a second press shortly after switches straight to the app that
     * was open before the current one (same behaviour as physically double-pressing the
     * Overview button). No on-screen swipe coordinates are needed for this.
     */
    private fun switchToPreviousApp(session: Int) {
        state = AutomationState.OPEN_RECENTS
        AutomationStatusHolder.update(getString(R.string.status_open_recents))
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
            abortRouteToMainScan(session)
            return
        }
        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            state = AutomationState.SWITCH_TO_PREVIOUS_APP
            AutomationStatusHolder.update(getString(R.string.status_switch_previous_app))
            if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
                abortRouteToMainScan(session)
                return@postDelayed
            }
            beginLoadingWait(session)
        }, AutomationConfig.RECENTS_DOUBLE_TAP_GAP_MS)
    }

    private fun abortRouteToMainScan(session: Int) {
        if (!isCurrentSession(session)) return
        AutomationStatusHolder.update(getString(R.string.status_gesture_interrupted))
        state = AutomationState.FIND_LIKE_OR_SKIP
        scheduleMainScan(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
    }

    // ------------------------------------------------------------------
    // D. Loading wait route
    // ------------------------------------------------------------------

    private fun beginLoadingWait(session: Int) {
        state = AutomationState.WAIT_FOR_LOADING
        loadingWaitStartElapsed = SystemClock.elapsedRealtime()
        AutomationStatusHolder.update(getString(R.string.status_waiting_for_loading))
        scheduleLoadingCheck(session, 0L)
    }

    private fun scheduleLoadingCheck(session: Int, delayMs: Long) {
        if (!isCurrentSession(session)) return
        if (state != AutomationState.WAIT_FOR_LOADING &&
            state != AutomationState.WAIT_FOR_LOADING_TO_FINISH
        ) {
            return
        }
        if (loadingCheckScheduled) return
        loadingCheckScheduled = true
        handler.postDelayed({
            loadingCheckScheduled = false
            performLoadingCheck(session)
        }, delayMs)
    }

    private fun performLoadingCheck(session: Int) {
        if (!isCurrentSession(session)) return

        val root = rootInActiveWindow
        val loadingVisible = findNodeByText(root, AutomationConfig.LOADING_TEXT) != null

        when (state) {
            AutomationState.WAIT_FOR_LOADING -> {
                if (loadingVisible) {
                    state = AutomationState.WAIT_FOR_LOADING_TO_FINISH
                    AutomationStatusHolder.update(getString(R.string.status_loading_detected))
                    scheduleLoadingCheck(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                } else {
                    val elapsed = SystemClock.elapsedRealtime() - loadingWaitStartElapsed
                    if (elapsed >= AutomationConfig.LOADING_TIMEOUT_MS) {
                        returnToMainScanAfterLoading(session)
                    } else {
                        scheduleLoadingCheck(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                    }
                }
            }

            AutomationState.WAIT_FOR_LOADING_TO_FINISH -> {
                if (loadingVisible) {
                    scheduleLoadingCheck(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                } else {
                    state = AutomationState.WAIT_AFTER_LOADING_FINISH
                    AutomationStatusHolder.update(getString(R.string.status_loading_finished))
                    handler.postDelayed({
                        if (!isCurrentSession(session)) return@postDelayed
                        returnToMainScanAfterLoading(session)
                    }, AutomationConfig.LOADING_SETTLE_DELAY_MS)
                }
            }

            else -> Unit
        }
    }

    private fun returnToMainScanAfterLoading(session: Int) {
        if (!isCurrentSession(session)) return
        state = AutomationState.FIND_LIKE_OR_SKIP
        AutomationStatusHolder.update(getString(R.string.status_searching))
        scheduleMainScan(session, 0L)
    }

    // ------------------------------------------------------------------
    // E. Skip route
    // ------------------------------------------------------------------

    private fun onSkipClicked(session: Int) {
        state = AutomationState.WAIT_AFTER_SKIP
        AutomationStatusHolder.update(getString(R.string.status_skip_clicked))
        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            state = AutomationState.FIND_LIKE_OR_SKIP
            AutomationStatusHolder.update(getString(R.string.status_searching))
            scheduleMainScan(session, 0L)
        }, AutomationConfig.WAIT_AFTER_SKIP_MS)
    }

    // ------------------------------------------------------------------
    // Node search + click helpers
    // ------------------------------------------------------------------

    private fun findNodeByText(root: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (matchesTarget(node.text?.toString(), target) ||
                matchesTarget(node.contentDescription?.toString(), target)
            ) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return null
    }

    private fun matchesTarget(value: String?, target: String): Boolean {
        if (value == null) return false
        return value.trim().equals(target.trim(), ignoreCase = true)
    }

    /**
     * 1) Try ACTION_CLICK on the node itself.
     * 2) Walk up parents for the first clickable ancestor.
     * 3) Fall back to a single-tap gesture at the node's on-screen bounds centre.
     */
    private fun attemptClick(node: AccessibilityNodeInfo, onResult: (Boolean) -> Unit) {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            onResult(true)
            return
        }

        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onResult(true)
                return
            }
            parent = parent.parent
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            onResult(false)
            return
        }
        tapAt(bounds.exactCenterX(), bounds.exactCenterY(), onResult)
    }

    // ------------------------------------------------------------------
    // Gesture helpers
    // ------------------------------------------------------------------

    private fun tapAt(x: Float, y: Float, onResult: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, AutomationConfig.TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onResult?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onResult?.invoke(false)
            }
        }, handler)
        if (!dispatched) {
            onResult?.invoke(false)
        }
    }

    private fun doubleTapCentre(onResult: (Boolean) -> Unit) {
        val (cx, cy) = screenCenter()
        tapAt(cx, cy) { firstTapOk ->
            if (!firstTapOk) {
                onResult(false)
                return@tapAt
            }
            handler.postDelayed({
                tapAt(cx, cy) { secondTapOk -> onResult(secondTapOk) }
            }, AutomationConfig.DOUBLE_TAP_GAP_MS)
        }
    }

    private fun screenCenter(): Pair<Float, Float> {
        val metrics = resources.displayMetrics
        return Pair(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
    }

    companion object {
        @Volatile
        private var instance: TextAutomationAccessibilityService? = null

        fun isServiceRunning(): Boolean = instance != null

        /** Returns true if the request was delivered to a connected service instance. */
        fun requestStart(): Boolean {
            val service = instance ?: return false
            service.startAutomationInternal()
            return true
        }

        fun requestStop() {
            instance?.stopAutomationInternal()
        }
    }
}
