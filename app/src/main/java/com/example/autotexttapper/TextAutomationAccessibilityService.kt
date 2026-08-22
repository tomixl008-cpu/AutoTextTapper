package com.example.autotexttapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
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
    const val WAIT_AFTER_LIKE_MS = 5000L
    const val WAIT_AFTER_SKIP_MS = 4000L
    const val MAIN_SCAN_INTERVAL_MS = 1000L
    const val LOADING_SETTLE_DELAY_MS = 1000L
    const val LOADING_TIMEOUT_MS = 30000L

    /** Safety cap while polling for "Like video" to leave the screen after it's clicked. */
    const val LIKE_VIDEO_GONE_TIMEOUT_MS = 10000L

    const val TAP_DURATION_MS = 60L
    const val DOUBLE_TAP_GAP_MS = 150L

    /** Gap between the two GLOBAL_ACTION_RECENTS presses that "double tap" the recents
     *  button to jump straight to the previously used app (same as pressing Overview twice). */
    const val RECENTS_DOUBLE_TAP_GAP_MS = 150L
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
        createNotificationChannel()
    }

    /**
     * Accessibility services are normally only bound by the system, but a Service can also be
     * explicitly started — the Stop notification action does exactly that (with ACTION_STOP) so
     * the user can stop automation without opening the app.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAutomationInternal()
        }
        return START_NOT_STICKY
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
        showRunningNotification()

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
        cancelRunningNotification()
    }

    // ------------------------------------------------------------------
    // Notification (Stop action, no need to open the app)
    // ------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Automation status",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun showRunningNotification() {
        val stopIntent = Intent(this, TextAutomationAccessibilityService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Automation running")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopPendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+) — automation still works, it's just
            // not stoppable from the notification shade until the user grants it.
        }
    }

    private fun cancelRunningNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(NOTIFICATION_ID)
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
        AutomationState.log(LogTag.OK, "like video clicked")
        // Counted the moment the click happens (same as Skip), not gated on any later
        // confirmation — this is what "collected" tracks: like taps performed, not hearts seen.
        AutomationState.collected()
        waitForLikeVideoGoneThenDoubleTap(session, SystemClock.elapsedRealtime())
    }

    /**
     * Polls until "Like video" is no longer on screen (confirming the tap actually took effect
     * and the app moved on) before starting the fixed 5-second wait and the double-tap. Bounded
     * by LIKE_VIDEO_GONE_TIMEOUT_MS so a screen that never updates can't stall automation.
     */
    private fun waitForLikeVideoGoneThenDoubleTap(session: Int, waitStartElapsed: Long) {
        if (!isCurrentSession(session)) return
        val stillVisible = findNodeByText(rootInActiveWindow, AutomationConfig.LIKE_VIDEO_TEXT) != null
        val elapsed = SystemClock.elapsedRealtime() - waitStartElapsed
        if (stillVisible && elapsed < AutomationConfig.LIKE_VIDEO_GONE_TIMEOUT_MS) {
            handler.postDelayed({
                waitForLikeVideoGoneThenDoubleTap(session, waitStartElapsed)
            }, AutomationConfig.MAIN_SCAN_INTERVAL_MS)
            return
        }
        if (stillVisible) {
            AutomationState.log(LogTag.WARN, "like video never left screen · proceeding anyway")
        }
        handler.postDelayed({
            if (!isCurrentSession(session)) return@postDelayed
            state = RouteState.DOUBLE_TAP_CENTER
            AutomationState.setSegment(DialSegment.COLLECT)
            doubleTapOnce(session)
        }, AutomationConfig.WAIT_AFTER_LIKE_MS)
    }

    /**
     * Double-taps the screen centre exactly once, then moves on regardless of what happens next
     * — no retrying. The red-heart text is checked once afterwards purely for the log line; it
     * never blocks or repeats the gesture.
     */
    private fun doubleTapOnce(session: Int) {
        if (!isCurrentSession(session)) return
        AutomationState.setPhase("sending like gesture")
        AutomationState.log(LogTag.ACTION, "double-tap dispatched")
        doubleTapCentre { success ->
            if (!isCurrentSession(session)) return@doubleTapCentre
            if (!success) {
                AutomationState.log(LogTag.FAIL, "gesture cancelled · aborting to scan")
                abortRouteToMainScan(session)
                return@doubleTapCentre
            }
            if (findNodeByText(rootInActiveWindow, AutomationConfig.LIKE_CONFIRMATION_TEXT) != null) {
                AutomationState.log(LogTag.OK, "heart confirmed")
            } else {
                AutomationState.log(LogTag.WARN, "heart not confirmed · proceeding anyway")
            }
            switchToPreviousApp(session)
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
        private const val NOTIFICATION_CHANNEL_ID = "automation_status"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.example.autotexttapper.action.STOP"

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
