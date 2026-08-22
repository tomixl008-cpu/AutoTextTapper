package com.example.autotexttapper.ui.atmosphere

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.autotexttapper.automation.ServiceState
import com.example.autotexttapper.ui.theme.Amber
import com.example.autotexttapper.ui.theme.Crimson
import com.example.autotexttapper.ui.theme.Phosphor
import kotlin.random.Random

private fun colorFor(state: ServiceState) = when (state) {
    ServiceState.DISABLED -> Crimson
    ServiceState.READY -> Amber
    ServiceState.RUNNING -> Phosphor
    ServiceState.ERROR -> Crimson
}

private fun generateGrainBitmap(): ImageBitmap {
    val size = 80
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val random = Random(42)
    for (x in 0 until size) {
        for (y in 0 until size) {
            val gray = random.nextInt(256)
            val argb = AndroidColor.argb(10, gray, gray, gray)
            bmp.setPixel(x, y, argb)
        }
    }
    return bmp.asImageBitmap()
}

/** Non-interactive glass layer: static scanlines, a moving scan band, and a cached grain texture. */
@Composable
fun CrtOverlay(state: ServiceState, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val color = colorFor(state)
    val grain = remember { generateGrainBitmap() }

    val infinite = rememberInfiniteTransition(label = "crt")
    val bandProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "band"
    )

    Canvas(modifier = modifier) {
        drawScanlines()
        if (!reduceMotion) {
            drawMovingBand(color, bandProgress)
        }
        drawGrain(grain)
    }
}

private fun DrawScope.drawScanlines() {
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.035f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += 3.dp.toPx()
    }
}

private fun DrawScope.drawMovingBand(color: androidx.compose.ui.graphics.Color, progress: Float) {
    val bandHeight = 90.dp.toPx()
    val travel = size.height + bandHeight * 2f
    val bandTop = -bandHeight + travel * progress
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0f),
                color.copy(alpha = 0.045f),
                color.copy(alpha = 0f)
            ),
            startY = bandTop,
            endY = bandTop + bandHeight
        ),
        topLeft = Offset(0f, bandTop),
        size = androidx.compose.ui.geometry.Size(size.width, bandHeight)
    )
}

private fun DrawScope.drawGrain(grain: ImageBitmap) {
    var x = 0f
    while (x < size.width) {
        var y = 0f
        while (y < size.height) {
            drawImage(grain, topLeft = Offset(x, y))
            y += grain.height
        }
        x += grain.width
    }
}
