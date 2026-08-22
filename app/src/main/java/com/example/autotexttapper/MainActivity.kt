package com.example.autotexttapper

import android.accessibilityservice.AccessibilityServiceInfo
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var glowRing: View
    private lateinit var logoMark: ImageView
    private lateinit var statusCard: View

    private var glowPulse: ObjectAnimator? = null
    private var logoSpin: ObjectAnimator? = null
    private var cardWobble: ObjectAnimator? = null

    private val statusListener: (String) -> Unit = { newStatus ->
        runOnUiThread {
            crossfadeStatusText(newStatus)
            refreshStatusDot()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        glowRing = findViewById(R.id.glowRing)
        logoMark = findViewById(R.id.logoMark)
        statusCard = findViewById(R.id.statusCard)

        val priorityPill = findViewById<View>(R.id.priorityPill)
        val disclosureBox = findViewById<View>(R.id.disclosureBox)
        val openAccessibilityButton = findViewById<Button>(R.id.openAccessibilityButton)
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)

        setUpAmbientAnimations()
        playEntranceAnimation(
            listOf(logoMark, statusCard, priorityPill, openAccessibilityButton, startButton, stopButton, disclosureBox)
        )
        addPressBounce(openAccessibilityButton)
        addPressBounce(startButton)
        addPressBounce(stopButton)

        openAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        startButton.setOnClickListener {
            if (!isAutomationServiceEnabled()) {
                crossfadeStatusText(getString(R.string.status_service_disabled))
                refreshStatusDot()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }

            val started = TextAutomationAccessibilityService.requestStart()
            if (!started) {
                crossfadeStatusText(getString(R.string.status_service_not_connected))
            }
            refreshStatusDot()
        }

        stopButton.setOnClickListener {
            TextAutomationAccessibilityService.requestStop()
        }
    }

    override fun onResume() {
        super.onResume()
        AutomationStatusHolder.addListener(statusListener)
        if (!isAutomationServiceEnabled()) {
            statusText.text = getString(R.string.status_service_disabled)
        } else if (!TextAutomationAccessibilityService.isServiceRunning()) {
            statusText.text = getString(R.string.status_service_ready)
        }
        refreshStatusDot()
        glowPulse?.start()
        logoSpin?.start()
        cardWobble?.start()
    }

    override fun onPause() {
        glowPulse?.cancel()
        logoSpin?.cancel()
        cardWobble?.cancel()
        AutomationStatusHolder.removeListener(statusListener)
        super.onPause()
    }

    // ------------------------------------------------------------------
    // Animations
    // ------------------------------------------------------------------

    /** 2D fade + slide-up "boot sequence" reveal, staggered per view. */
    private fun playEntranceAnimation(views: List<View>) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 60f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 90L)
                .setDuration(420L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** Continuous 3D spin on the logo mark, plus a subtle 3D tilt "wobble" on the status card. */
    private fun setUpAmbientAnimations() {
        glowPulse = ObjectAnimator.ofFloat(glowRing, View.ALPHA, 0.35f, 1f).apply {
            duration = 900L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }

        // True 3D rotation around the Y axis — a slowly spinning coin/hologram.
        logoMark.cameraDistance = 8000f * resources.displayMetrics.density
        logoSpin = ObjectAnimator.ofFloat(logoMark, View.ROTATION_Y, 0f, 360f).apply {
            duration = 3400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        // Gentle holographic tilt on the status panel.
        statusCard.cameraDistance = 12000f * resources.displayMetrics.density
        cardWobble = ObjectAnimator.ofFloat(statusCard, View.ROTATION_Y, -3f, 3f).apply {
            duration = 3200L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    /** Physical-feeling press/release scale bounce, independent of the click listener. */
    @Suppress("ClickableViewAccessibility")
    private fun addPressBounce(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
            }
            false // let the click listener still receive the event
        }
    }

    private fun crossfadeStatusText(newText: String) {
        if (statusText.text == newText) return
        statusText.animate()
            .alpha(0f)
            .setDuration(120L)
            .withEndAction {
                statusText.text = newText
                statusText.animate().alpha(1f).setDuration(180L).start()
            }
            .start()
    }

    private fun refreshStatusDot() {
        val colorRes = when {
            !isAutomationServiceEnabled() -> R.color.status_dot_disabled
            TextAutomationAccessibilityService.isServiceRunning() -> R.color.status_dot_running
            else -> R.color.status_dot_ready
        }
        statusDot.background.setTint(ContextCompat.getColor(this, colorRes))
    }

    private fun isAutomationServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expectedComponent = ComponentName(this, TextAutomationAccessibilityService::class.java)

        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { serviceInfo ->
                serviceInfo.resolveInfo.serviceInfo.packageName == expectedComponent.packageName &&
                    serviceInfo.resolveInfo.serviceInfo.name == expectedComponent.className
            }
    }
}
