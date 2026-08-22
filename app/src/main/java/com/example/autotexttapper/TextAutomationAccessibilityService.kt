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
import com.example.autotexttapper.automation.AutomationState
import com.example.autotexttapper.automation.LogTag
import com.example.autotexttapper.automation.Target

/**
 * All user-configurable text, delay, and gesture values for the automation
 * routine. Edit these constants to retune behaviour without touching the
 * state machine logic below.
 */
private object AutomationConfig {
    const val LIKE_VIDEO_TEXT = "Like video"
    const val SKIP_TEXT = "Skip"
    const val LOADING_TEXT = "Loading"

    /** Text/content-description that confirms the like actually registered on screen. */
    const val LIKE_CONFIRMATION_TEXT = "❤️"

    /** Informational only — used for the live foreground indicator, never to gate actions. */
    const val FANTIK_PACKAGE = "com.tikboost.fantik"
    const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"

    const val INITIAL_DELAY_MS = 5000L
    const val WAIT_AFTER_LIKE_MS = 2000L
    const val WAIT_AFTER_SKIP_MS = 4000L
    const val MAIN_SCAN_INTERVAL_MS = 1000L
    const val LOADING_SETTLE_DELAY_MS = 1000L
    const val LOADING_TIMEOUT_MS = 30000L

    const val TAP_DURATION_MS = 60L
    const val DOUBLE_TAP_GAP_MS = 150L

    /** Gap between the two GLOBAL_ACTION_RECENTS presses that "double tap" the recents
     *  button to jump straight to the previously used app (same as pressing Overview twice). */
    const val RECENTS_DOUBLE_TAP_GAP_MS = 150L

    /** Gap between repeated centre double-taps while waiting for the red-heart confirmation. */
    const val LIKE_CONFIRM_RETRY_GAP_MS = 1000L

    /** Safety cap so a missing/misdetected heart can never stall automation forever. */
    const val MAX_LIKE_CONFIRM_ATTEMPTS = 3
}

/** Cycle-dial segment indices, matching AutomationState.segment. */
private object DialSegment {
    const val SCAN = 0
    const val DISPATCH = 1
    const val COLLECT = 2
    const val RETURN = 3
}

/** Finite states for the automation routine. Exactly one action runs per transition. */
enum class RouteState {
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

    private var state: RouteState = RouteState.IDLE

    /** Incremented on every Start/Stop so stale delayed callbacks can detect they're obsolete. */
    private var sessionId: Int = 0

    private var mainScanScheduled = false
    private var loadingCheckScheduled = false
    private var loadingWaitStartElapsed = 0L
    private var likeConfirmAttempts = 0

    private var mainScanSession = 0
    private val mainScanRunnable = Runnable {
        mainScanScheduled = false
        performMainScan(mainScanSession)
    }

    private var loadingCheckSession = 0
    private val loadingCheckRunnable = Runnable {
        loadingCheckScheduled = false
        performLoadingCheck(loadingCheckSession)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AutomationState.setForeground(
                when (event.packageName?.toString()) {
                    AutomationConfig.FANTIK_PACKAGE -> Target.FANTIK
                    AutomationConfig.TIKTOK_PACKAGE -> Target.TIKTOK
                    else -> Target.NONE
                }
            )
        }

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }
        when (state) {
            RouteState.FIND_LIKE_OR_SKIP -> scheduleMainScan(sessionId, 0L, immediate = true)
            RouteState.WAIT_FOR_LOADING,
            RouteState.WAIT_FOR_LOADING_TO_FINISH ->
                scheduleLoadingCheck(sessionId, 0L, immediate = true)
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

        state = RouteState.INITIAL_WAIT
        AutomationState.startSession()
        AutomationState.setPhase("initial wait")
        AutomationState.log(LogTag.ACTION, "session started · initial wait 5s")

        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            enterScanState(session, logMessage = "scan started")
        }, AutomationConfig.INITIAL_DELAY_MS)
    }

    private fun stopAutomationInternal() {
        sessionId++ // invalidates every in-flight closure immediately
        handler.removeCallbacksAndMessages(null)
        mainScanScheduled = false
        loadingCheckScheduled = false
        state = RouteState.IDLE
        AutomationState.log(LogTag.ACTION, "stopped by user")
        AutomationState.stopSession()
    }

    private fun isCurrentSession(session: Int): Boolean = session == sessionId

    /** Enters FIND_LIKE_OR_SKIP, resets the cycle dial to a fresh cycle, and kicks off a scan. */
    private fun enterScanState(session: Int, logMessage: String?) {
        state = RouteState.FIND_LIKE_OR_SKIP
        AutomationState.resetCycle()
        AutomationState.setSegment(DialSegment.SCAN)
        AutomationState.setPhase("scanning fantik")
        if (logMessage != null) AutomationState.log(LogTag.TICK, logMessage)
        scheduleMainScan(session, 0L, immediate = true)
    }

    // ------------------------------------------------------------------
    // B. Main scan rule — Like video always has priority over Skip
    // ------------------------------------------------------------------

    private fun scheduleMainScan(session: Int, delayMs: Long, immediate: Boolean = false) {
        if (!isCurrentSession(session) || state != RouteState.FIND_LIKE_OR_SKIP) return
        mainScanSession = session
        if (immediate) {
            handler.removeCallbacks(mainScanRunnable)
            mainScanScheduled = true
            handler.post(mainScanRunnable)
            return
        }
        if (mainScanScheduled) return
        mainScanScheduled = true
        handler.postDelayed(mainScanRunnable, delayMs)
    }

    private fun performMainScan(session: Int) {
        if (!isCurrentSession(session) || state != RouteState.FIND_LIKE_OR_SKIP) return

        val root = rootInActiveWindow
        val likeNode = findNodeByText(root, AutomationConfig.LIKE_VIDEO_TEXT)
        if (likeNode != null) {
            AutomationState.setSegment(DialSegment.DISPATCH)
            AutomationState.log(LogTag.ACTION, "matched \"like video\" · dispatching tap")
            attemptClick(likeNode) { clicked ->
                if (!isCurrentSession(session)) return@attemptClick
                if (clicked) {
                    onLikeVideoClicked(session)
                } else {
                    AutomationState.log(LogTag.FAIL, "like tap failed · retrying")
                    AutomationState.setSegment(DialSegment.SCAN)
                    scheduleMainScan(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                }
            }
            return
        }

        val skipNode = findNodeByText(root, AutomationConfig.SKIP_TEXT)
        if (skipNode != null) {
            AutomationState.setSegment(DialSegment.DISPATCH)
            AutomationState.log(LogTag.ACTION, "matched \"skip\" · dispatching tap")
            attemptClick(skipNode) { clicked ->
                if (!isCurrentSession(session)) return@attemptClick
                if (clicked) {
                    onSkipClicked(session)
                } else {
                    AutomationState.log(LogTag.FAIL, "skip tap failed · retrying")
                    AutomationState.setSegment(DialSegment.SCAN)
                    scheduleMainScan(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                }
            }
            return
        }

        // Neither text visible: don't touch the screen, wait, scan again.
        AutomationState.log(LogTag.TICK, "scan tick · no match")
        scheduleMainScan(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
    }

    // ------------------------------------------------------------------
    // C. Like video route
    // ------------------------------------------------------------------

    private fun onLikeVideoClicked(session: Int) {
        state = RouteState.WAIT_AFTER_LIKE
        AutomationState.setPhase("handing off to tiktok")
        AutomationState.log(LogTag.OK, "like video clicked · waiting 2s")
        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            state = RouteState.DOUBLE_TAP_CENTER
            likeConfirmAttempts = 0
            AutomationState.setSegment(DialSegment.COLLECT)
            doubleTapAndConfirmLike(session)
        }, AutomationConfig.WAIT_AFTER_LIKE_MS)
    }

    /**
     * Double-taps the screen centre, then checks for the red-heart confirmation
     * (AutomationConfig.LIKE_CONFIRMATION_TEXT). If the heart hasn't appeared yet, it waits
     * LIKE_CONFIRM_RETRY_GAP_MS and double-taps again, up to MAX_LIKE_CONFIRM_ATTEMPTS times so
     * a missing/misdetected heart can never stall automation forever.
     */
    private fun doubleTapAndConfirmLike(session: Int) {
        if (!isCurrentSession(session)) return
        likeConfirmAttempts++
        AutomationState.setPhase("sending like gesture")
        AutomationState.log(
            LogTag.ACTION,
            "double-tap dispatched (attempt $likeConfirmAttempts/${AutomationConfig.MAX_LIKE_CONFIRM_ATTEMPTS})"
        )
        doubleTapCentre { success ->
            if (!isCurrentSession(session)) return@doubleTapCentre
            if (!success) {
                AutomationState.log(LogTag.FAIL, "gesture cancelled · aborting to scan")
                abortRouteToMainScan(session)
                return@doubleTapCentre
            }
            if (findNodeByText(rootInActiveWindow, AutomationConfig.LIKE_CONFIRMATION_TEXT) != null) {
                AutomationState.setPhase("collected")
                AutomationState.log(LogTag.OK, "heart confirmed")
                AutomationState.collected()
                switchToPreviousApp(session)
            } else if (likeConfirmAttempts >= AutomationConfig.MAX_LIKE_CONFIRM_ATTEMPTS) {
                AutomationState.log(LogTag.WARN, "heart never confirmed · proceeding anyway")
                switchToPreviousApp(session)
            } else {
                AutomationState.log(
                    LogTag.WARN,
                    "heart not found · retry $likeConfirmAttempts/${AutomationConfig.MAX_LIKE_CONFIRM_ATTEMPTS}"
                )
                handler.postDelayed({
                    if (!isCurrentSession(session)) return@postDelayed
                    doubleTapAndConfirmLike(session)
                }, AutomationConfig.LIKE_CONFIRM_RETRY_GAP_MS)
            }
        }
    }

    /**
     * Double-tap the Recents/Overview action: one GLOBAL_ACTION_RECENTS press opens the
     * recent-apps screen, a second press shortly after switches straight to the app that
     * was open before the current one (same behaviour as physically double-pressing the
     * Overview button). No on-screen swipe coordinates are needed for this.
     */
    private fun switchToPreviousApp(session: Int) {
        state = RouteState.OPEN_RECENTS
        AutomationState.setSegment(DialSegment.RETURN)
        AutomationState.setPhase("returning to fantik")
        AutomationState.log(LogTag.ACTION, "opening recents")
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
            AutomationState.log(LogTag.FAIL, "recents action failed · aborting to scan")
            abortRouteToMainScan(session)
            return
        }
        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            state = RouteState.SWITCH_TO_PREVIOUS_APP
            if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
                AutomationState.log(LogTag.FAIL, "recents action failed · aborting to scan")
                abortRouteToMainScan(session)
                return@postDelayed
            }
            AutomationState.log(LogTag.ACTION, "switched to previous app")
            beginLoadingWait(session)
        }, AutomationConfig.RECENTS_DOUBLE_TAP_GAP_MS)
    }

    private fun abortRouteToMainScan(session: Int) {
        if (!isCurrentSession(session)) return
        enterScanState(session, logMessage = null)
    }

    // ------------------------------------------------------------------
    // D. Loading wait route
    // ------------------------------------------------------------------

    private fun beginLoadingWait(session: Int) {
        state = RouteState.WAIT_FOR_LOADING
        loadingWaitStartElapsed = SystemClock.elapsedRealtime()
        AutomationState.setPhase("waiting for load")
        scheduleLoadingCheck(session, 0L, immediate = true)
    }

    private fun scheduleLoadingCheck(session: Int, delayMs: Long, immediate: Boolean = false) {
        if (!isCurrentSession(session)) return
        if (state != RouteState.WAIT_FOR_LOADING &&
            state != RouteState.WAIT_FOR_LOADING_TO_FINISH
        ) {
            return
        }
        loadingCheckSession = session
        if (immediate) {
            handler.removeCallbacks(loadingCheckRunnable)
            loadingCheckScheduled = true
            handler.post(loadingCheckRunnable)
            return
        }
        if (loadingCheckScheduled) return
        loadingCheckScheduled = true
        handler.postDelayed(loadingCheckRunnable, delayMs)
    }

    private fun performLoadingCheck(session: Int) {
        if (!isCurrentSession(session)) return

        val root = rootInActiveWindow
        val loadingVisible = findNodeByText(root, AutomationConfig.LOADING_TEXT) != null
        // Some apps show a loading spinner with no accessible "Loading" label at all, so also
        // treat the target screen actually being ready (Like video / Skip visible again) as an
        // immediate "loading is done" signal instead of always waiting out the full timeout.
        val targetScreenReady = findNodeByText(root, AutomationConfig.LIKE_VIDEO_TEXT) != null ||
            findNodeByText(root, AutomationConfig.SKIP_TEXT) != null

        when (state) {
            RouteState.WAIT_FOR_LOADING -> {
                if (targetScreenReady) {
                    finishLoadingWait(session)
                } else if (loadingVisible) {
                    state = RouteState.WAIT_FOR_LOADING_TO_FINISH
                    AutomationState.log(LogTag.WARN, "loading detected · waiting")
                    scheduleLoadingCheck(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                } else {
                    val elapsed = SystemClock.elapsedRealtime() - loadingWaitStartElapsed
                    if (elapsed >= AutomationConfig.LOADING_TIMEOUT_MS) {
                        AutomationState.log(LogTag.WARN, "loading timeout · resuming scan")
                        returnToMainScanAfterLoading(session)
                    } else {
                        scheduleLoadingCheck(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                    }
                }
            }

            RouteState.WAIT_FOR_LOADING_TO_FINISH -> {
                if (loadingVisible && !targetScreenReady) {
                    scheduleLoadingCheck(session, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
                } else {
                    finishLoadingWait(session)
                }
            }

            else -> Unit
        }
    }

    private fun finishLoadingWait(session: Int) {
        state = RouteState.WAIT_AFTER_LOADING_FINISH
        AutomationState.log(LogTag.OK, "loading cleared")
        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            returnToMainScanAfterLoading(session)
        }, AutomationConfig.LOADING_SETTLE_DELAY_MS)
    }

    private fun returnToMainScanAfterLoading(session: Int) {
        if (!isCurrentSession(session)) return
        enterScanState(session, logMessage = null)
    }

    // ------------------------------------------------------------------
    // E. Skip route
    // ------------------------------------------------------------------

    private fun onSkipClicked(session: Int) {
        state = RouteState.WAIT_AFTER_SKIP
        AutomationState.setSegment(DialSegment.RETURN) // skip never enters COLLECT
        AutomationState.setPhase("skip · cooling down")
        AutomationState.log(LogTag.OK, "skip clicked · waiting 4s")
        AutomationState.incrementSkips()
        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            enterScanState(session, logMessage = null)
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
