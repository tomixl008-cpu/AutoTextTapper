package com.example.autotexttapper

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.openAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!isAutomationServiceEnabled()) {
                statusText.text = "First enable Auto Text Tapper service in Accessibility Settings."
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }

            val started = TextAutomationAccessibilityService.startAutomation()
            statusText.text = if (started) {
                "Started. Switch to the authorized target screen within 5 seconds."
            } else {
                "Service is not connected yet. Turn it off/on in Accessibility Settings, then try again."
            }
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            TextAutomationAccessibilityService.stopAutomation()
            statusText.text = "Automation stopped."
        }
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            statusText.text = if (isAutomationServiceEnabled()) {
                "Service enabled. Press Start when ready."
            } else {
                "Enable the accessibility service, then press Start."
            }
        }
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
