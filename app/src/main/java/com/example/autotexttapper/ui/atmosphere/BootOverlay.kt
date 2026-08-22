package com.example.autotexttapper.ui.atmosphere

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.autotexttapper.ui.theme.PhosphorDim
import com.example.autotexttapper.ui.theme.Void
import kotlinx.coroutines.delay

/** A brief ~1s "booting" flash on first launch — kept short on purpose. */
@Composable
fun BootOverlay(reduceMotion: Boolean, onFinished: () -> Unit) {
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            onFinished()
            return@LaunchedEffect
        }
        delay(650L)
        alpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Void),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "booting_",
            color = PhosphorDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
    }
}
