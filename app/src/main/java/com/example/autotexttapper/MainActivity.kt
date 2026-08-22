package com.example.autotexttapper

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.autotexttapper.ui.CollectorScreen
import com.example.autotexttapper.ui.theme.CoinCollectorTheme
import com.example.autotexttapper.ui.theme.Void

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen is always dark (Void), regardless of the system's light/dark setting, so
        // force light (white) status/nav bar icons — otherwise "auto" style picks dark icons on
        // a light-mode device and they become invisible against our black background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        maybeRequestNotificationPermission()
        setContent {
            CoinCollectorApp()
        }
    }

    /** Needed so the "Stop" action on the running notification actually shows up on Android 13+. */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun CoinCollectorApp() {
    CoinCollectorTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Void) {
            CollectorScreen()
        }
    }
}
