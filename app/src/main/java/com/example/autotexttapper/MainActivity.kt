package com.example.autotexttapper

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.autotexttapper.ui.CollectorScreen
import com.example.autotexttapper.ui.theme.CoinCollectorTheme
import com.example.autotexttapper.ui.theme.Void

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen is always dark (Void), regardless of the system's light/dark setting, so
        // force light (white) status/nav bar icons — otherwise "auto" style picks dark icons on
        // a light-mode device and they become invisible against our black background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            CoinCollectorApp()
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
