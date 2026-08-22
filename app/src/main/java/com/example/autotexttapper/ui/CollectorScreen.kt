package com.example.autotexttapper.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autotexttapper.TextAutomationAccessibilityService
import com.example.autotexttapper.automation.AutomationState
import com.example.autotexttapper.automation.ServiceState
import com.example.autotexttapper.ui.atmosphere.BootOverlay
import com.example.autotexttapper.ui.atmosphere.CrtOverlay
import com.example.autotexttapper.ui.atmosphere.RainField
import com.example.autotexttapper.ui.components.AccessBanner
import com.example.autotexttapper.ui.components.CycleDial
import com.example.autotexttapper.ui.components.HeaderStrip
import com.example.autotexttapper.ui.components.LogConsole
import com.example.autotexttapper.ui.components.PhaseLine
import com.example.autotexttapper.ui.components.PowerButton
import com.example.autotexttapper.ui.components.StatsLine
import com.example.autotexttapper.ui.components.TargetPair
import com.example.autotexttapper.ui.theme.MicroTextStyle
import com.example.autotexttapper.ui.theme.Spacing
import com.example.autotexttapper.ui.theme.TextGhost
import com.example.autotexttapper.ui.theme.Void

private const val FANTIK_PACKAGE = "com.tikboost.fantik"
private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val expected = ComponentName(context, TextAutomationAccessibilityService::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == expected.packageName && it.resolveInfo.serviceInfo.name == expected.className }
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
    context.packageManager.getPackageInfo(packageName, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

private fun isReducedMotion(context: Context): Boolean = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (e: Settings.SettingNotFoundException) {
    false
}

@Composable
fun CollectorScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    val reduceMotion = remember { isReducedMotion(context) }
    val fantikInstalled = remember { isPackageInstalled(context, FANTIK_PACKAGE) }
    val tiktokInstalled = remember { isPackageInstalled(context, TIKTOK_PACKAGE) }

    var bootFinished by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val serviceState by AutomationState.state.collectAsStateWithLifecycle()
    val phase by AutomationState.phase.collectAsStateWithLifecycle()
    val likeCount by AutomationState.likeCount.collectAsStateWithLifecycle()
    val skipCount by AutomationState.skipCount.collectAsStateWithLifecycle()
    val sessionStart by AutomationState.sessionStart.collectAsStateWithLifecycle()
    val segment by AutomationState.segment.collectAsStateWithLifecycle()
    val cycleDone by AutomationState.cycleDone.collectAsStateWithLifecycle()
    val foreground by AutomationState.foreground.collectAsStateWithLifecycle()
    val collectTick by AutomationState.collectTick.collectAsStateWithLifecycle()
    val logs by AutomationState.logs.collectAsStateWithLifecycle()

    val displayedState = if (!accessibilityEnabled) ServiceState.DISABLED else serviceState

    Box(modifier = Modifier.fillMaxSize().background(Void)) {
        // Layer 0 — atmosphere
        RainField(state = displayedState, reduceMotion = reduceMotion, modifier = Modifier.fillMaxSize())

        // Layer 1 — content. statusBarsPadding/navigationBarsPadding keep the readable
        // content clear of the time/battery area and the gesture bar, while the atmosphere
        // layers behind it still draw fully edge-to-edge. Centered vertically so there's
        // empty space above and below on screens taller than the content; scrolls instead
        // of clipping on screens shorter than the content.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenPadding)
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderStrip(state = displayedState, reduceMotion = reduceMotion, modifier = Modifier.fillMaxWidth())

            if (!accessibilityEnabled) {
                Box(modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) {
                    AccessBanner(
                        visible = true,
                        onGrantClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Couldn't open settings", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            Box(modifier = Modifier.padding(top = 8.dp), contentAlignment = Alignment.Center) {
                CycleDial(
                    state = displayedState,
                    segment = segment,
                    cycleDone = cycleDone,
                    collectTick = collectTick,
                    reduceMotion = reduceMotion
                )
            }

            Box(modifier = Modifier.padding(top = 4.dp)) {
                PhaseLine(
                    state = displayedState,
                    phase = when (displayedState) {
                        ServiceState.DISABLED -> "accessibility service is off"
                        ServiceState.READY -> "ready · press start"
                        ServiceState.RUNNING -> phase
                        ServiceState.ERROR -> phase
                    }
                )
            }

            Box(modifier = Modifier.padding(top = 16.dp)) {
                TargetPair(
                    foreground = foreground,
                    running = displayedState == ServiceState.RUNNING,
                    fantikInstalled = fantikInstalled,
                    tiktokInstalled = tiktokInstalled
                )
            }

            Box(modifier = Modifier.padding(top = 20.dp)) {
                StatsLine(
                    likeCount = likeCount,
                    skipCount = skipCount,
                    sessionStart = sessionStart,
                    running = displayedState == ServiceState.RUNNING
                )
            }

            Box(modifier = Modifier.padding(top = 18.dp).fillMaxWidth()) {
                PowerButton(
                    state = displayedState,
                    onStartRequested = {
                        val started = TextAutomationAccessibilityService.requestStart()
                        if (!started) {
                            android.widget.Toast.makeText(context, "Service not connected yet", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStopRequested = { TextAutomationAccessibilityService.requestStop() },
                    onOpenAccessibilitySettings = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Couldn't open settings", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Box(modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                LogConsole(entries = logs)
            }

            Box(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = "authorized use only · runs on this device only",
                    style = MicroTextStyle,
                    color = TextGhost.copy(alpha = 0.6f)
                )
            }
        }

        // Layer 2 — glass
        CrtOverlay(state = displayedState, reduceMotion = reduceMotion, modifier = Modifier.fillMaxSize())

        if (!bootFinished) {
            BootOverlay(
                accessibilityEnabled = accessibilityEnabled,
                fantikInstalled = fantikInstalled,
                tiktokInstalled = tiktokInstalled,
                reduceMotion = reduceMotion,
                onFinished = { bootFinished = true }
            )
        }
    }
}
