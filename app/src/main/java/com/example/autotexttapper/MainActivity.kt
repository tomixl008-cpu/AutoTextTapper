package com.example.autotexttapper

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusDot: View

    private val statusListener: (String) -> Unit = {
        runOnUiThread {
            statusText.text = it
            refreshStatusDot()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)

        findViewById<Button>(R.id.openAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!isAutomationServiceEnabled()) {
                statusText.text = getString(R.string.status_service_disabled)
                refreshStatusDot()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }

            val started = TextAutomationAccessibilityService.requestStart()
            if (!started) {
                statusText.text = getString(R.string.status_service_not_connected)
            }
            refreshStatusDot()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
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
    }

    override fun onPause() {
        AutomationStatusHolder.removeListener(statusListener)
        super.onPause()
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
