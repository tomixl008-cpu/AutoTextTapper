package com.example.autotexttapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class TextAutomationAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var state = State.IDLE
    private var scanQueued = false

    private enum class State {
        IDLE,
        INITIAL_WAIT,
        FIND_LIKE_OR_SKIP,
        WAIT_AFTER_LIKE,
        WAIT_AFTER_DOUBLE_TAP,
        FIND_FANTIK,
        WAIT_AFTER_FANTIK,
        WAIT_AFTER_SKIP
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        stopInternal()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        // Android calls this if it wants the service to interrupt feedback.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // UI changed: scan sooner only while the service is waiting for text.
        if (state == State.FIND_LIKE_OR_SKIP || state == State.FIND_FANTIK) {
            scheduleScan(EVENT_SCAN_DELAY_MS)
        }
    }

    private fun startInternal() {
        stopInternal()
        state = State.INITIAL_WAIT

        handler.postDelayed({
            if (state == State.INITIAL_WAIT) {
                state = State.FIND_LIKE_OR_SKIP
                scanNow()
            }
        }, INITIAL_WAIT_MS)
    }

    private fun stopInternal() {
        handler.removeCallbacksAndMessages(null)
        scanQueued = false
        state = State.IDLE
    }

    private fun scheduleScan(delayMs: Long = SCAN_INTERVAL_MS) {
        if (state == State.IDLE || scanQueued) return

        scanQueued = true
        handler.postDelayed({
            scanQueued = false
            scanNow()
        }, delayMs)
    }

    private fun scanNow() {
        val root = rootInActiveWindow ?: run {
            if (state == State.FIND_LIKE_OR_SKIP || state == State.FIND_FANTIK) {
                scheduleScan()
            }
            return
        }

        when (state) {
            State.FIND_LIKE_OR_SKIP -> scanLikeThenSkip(root)
            State.FIND_FANTIK -> scanFanTik(root)
            else -> Unit
        }
    }

    private fun scanLikeThenSkip(root: AccessibilityNodeInfo) {
        // PRIORITY RULE: search Like video before Skip.
        val likeNode = findVisibleExactText(root, LIKE_VIDEO)
        if (likeNode != null) {
            if (clickNodeOrParent(likeNode)) {
                waitAfterLikeTap()
            } else {
                scheduleScan()
            }
            return
        }

        // This is a fallback only. It runs only when Like video is absent.
        val skipNode = findVisibleExactText(root, SKIP)
        if (skipNode != null) {
            if (clickNodeOrParent(skipNode)) {
                waitAfterSkipTap()
            } else {
                scheduleScan()
            }
            return
        }

        // Neither text is visible: do nothing, then retry.
        scheduleScan()
    }

    private fun scanFanTik(root: AccessibilityNodeInfo) {
        val fanTikNode = findVisibleExactText(root, FANTIK)
        if (fanTikNode != null) {
            if (clickNodeOrParent(fanTikNode)) {
                waitAfterFanTikTap()
            } else {
                scheduleScan()
            }
        } else {
            scheduleScan()
        }
    }

    // Like video -> wait 4 s -> centre double-tap -> wait 2 s -> look for FanTik.
    private fun waitAfterLikeTap() {
        state = State.WAIT_AFTER_LIKE

        handler.postDelayed({
            if (state != State.WAIT_AFTER_LIKE) return@postDelayed

            sendCentreDoubleTap()
            state = State.WAIT_AFTER_DOUBLE_TAP

            handler.postDelayed({
                if (state == State.WAIT_AFTER_DOUBLE_TAP) {
                    state = State.FIND_FANTIK
                    scanNow()
                }
            }, WAIT_AFTER_DOUBLE_TAP_MS)
        }, WAIT_AFTER_LIKE_TAP_MS)
    }

    // Skip -> wait 4 s -> immediately resume Like video first priority scan.
    private fun waitAfterSkipTap() {
        state = State.WAIT_AFTER_SKIP

        handler.postDelayed({
            if (state == State.WAIT_AFTER_SKIP) {
                state = State.FIND_LIKE_OR_SKIP
                scanNow()
            }
        }, WAIT_AFTER_SKIP_TAP_MS)
    }

    // FanTik -> wait 11 s -> resume Like video first priority scan.
    private fun waitAfterFanTikTap() {
        state = State.WAIT_AFTER_FANTIK

        handler.postDelayed({
            if (state == State.WAIT_AFTER_FANTIK) {
                state = State.FIND_LIKE_OR_SKIP
                scanNow()
            }
        }, WAIT_AFTER_FANTIK_TAP_MS)
    }

    private fun findVisibleExactText(
        root: AccessibilityNodeInfo,
        requiredText: String
    ): AccessibilityNodeInfo? {
        val required = requiredText.trim().lowercase(Locale.ROOT)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val visibleText = node.text?.toString()?.trim()?.lowercase(Locale.ROOT)
            val contentDescription = node.contentDescription?.toString()?.trim()?.lowercase(Locale.ROOT)

            if (node.isVisibleToUser && (visibleText == required || contentDescription == required)) {
                return node
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }

        // Fallback: tap the matched text element's visible bounds.
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return if (!bounds.isEmpty) {
            sendSingleTap(bounds.exactCenterX(), bounds.exactCenterY())
        } else {
            false
        }
    }

    private fun sendCentreDoubleTap() {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val y = metrics.heightPixels / 2f
        val path = Path().apply { moveTo(x, y) }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .addStroke(GestureDescription.StrokeDescription(path, DOUBLE_TAP_GAP_MS, TAP_DURATION_MS))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun sendSingleTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    companion object {
        private const val LIKE_VIDEO = "Like video"
        private const val SKIP = "Skip"
        private const val FANTIK = "FanTik"

        private const val INITIAL_WAIT_MS = 5_000L
        private const val WAIT_AFTER_LIKE_TAP_MS = 4_000L
        private const val WAIT_AFTER_SKIP_TAP_MS = 4_000L
        private const val WAIT_AFTER_DOUBLE_TAP_MS = 2_000L
        private const val WAIT_AFTER_FANTIK_TAP_MS = 11_000L
        private const val SCAN_INTERVAL_MS = 1_000L
        private const val EVENT_SCAN_DELAY_MS = 150L
        private const val TAP_DURATION_MS = 60L
        private const val DOUBLE_TAP_GAP_MS = 150L

        private var instance: TextAutomationAccessibilityService? = null

        fun startAutomation(): Boolean {
            val service = instance ?: return false
            service.startInternal()
            return true
        }

        fun stopAutomation() {
            instance?.stopInternal()
        }
    }
}
