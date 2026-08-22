package com.example.autotexttapper.ui.atmosphere

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autotexttapper.automation.ServiceState
import com.example.autotexttapper.ui.theme.Amber
import com.example.autotexttapper.ui.theme.Crimson
import com.example.autotexttapper.ui.theme.Phosphor
import kotlin.random.Random

private const val ALPHA_CEILING = 0.22f
private val GLYPHS = ("ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾊﾐﾋｰｳｼﾅﾓﾆｻﾜ" + "0123456789ABCDEF").toCharArray()

private fun colorFor(state: ServiceState): Color = when (state) {
    ServiceState.DISABLED -> Crimson
    ServiceState.READY -> Amber
    ServiceState.RUNNING -> Phosphor
    ServiceState.ERROR -> Crimson
}

private fun speedFor(state: ServiceState): Float = when (state) {
    ServiceState.DISABLED -> 0f
    ServiceState.READY -> 38f
    ServiceState.RUNNING -> 115f
    ServiceState.ERROR -> 115f
}

private class RainColumn(seed: Int) {
    val random = Random(seed)
    var y = random.nextFloat() * 1000f
    val speedMultiplier = 0.75f + random.nextFloat() * 0.65f
    val trailLength = 6 + random.nextInt(10)
    val glyphs = CharArray(trailLength) { GLYPHS[random.nextInt(GLYPHS.size)] }
    var reRollAccumulatorMs = 0f
}

@Composable
fun RainField(state: ServiceState, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val latestState = rememberUpdatedState(state)
    val color = colorFor(state)
    val density = LocalDensity.current

    val paint = remember(density) {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textSize = with(density) { 12.sp.toPx() }
        }
    }

    // A frame counter that Canvas reads, so mutating the plain (non-Compose-state) column
    // list each frame still triggers a redraw.
    var frameTick by remember { mutableLongStateOf(0L) }
    var currentSpeed by remember { mutableFloatStateOf(0f) }
    val columns = remember { mutableListOf<RainColumn>() }
    var initializedWidth by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(reduceMotion) {
        var lastFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { frameMs ->
                val dt = (frameMs - lastFrame).coerceIn(0, 100)
                lastFrame = frameMs

                val targetSpeed = if (reduceMotion) 0f else speedFor(latestState.value)
                currentSpeed += (targetSpeed - currentSpeed) * (dt / 900f).coerceIn(0f, 1f)

                if (!reduceMotion && currentSpeed > 0.01f) {
                    columns.forEach { col ->
                        col.y += currentSpeed * col.speedMultiplier * (dt / 1000f)
                        col.reRollAccumulatorMs += dt
                        if (col.reRollAccumulatorMs > 120f) {
                            col.reRollAccumulatorMs = 0f
                            for (i in col.glyphs.indices) {
                                if (col.random.nextFloat() < 0.3f) {
                                    col.glyphs[i] = GLYPHS[col.random.nextInt(GLYPHS.size)]
                                }
                            }
                        }
                        if (col.y - col.trailLength * 14f > 2000f) {
                            col.y = -col.random.nextFloat() * 400f
                        }
                    }
                }
                frameTick = frameMs
            }
        }
    }

    Canvas(modifier = modifier) {
        // Reading frameTick here ties this draw to the frame clock.
        @Suppress("UNUSED_EXPRESSION") frameTick

        val columnWidthPx = 13.dp.toPx()
        val glyphHeightPx = 14.sp.toPx()
        val columnCount = (size.width / columnWidthPx).toInt().coerceAtLeast(1)

        if (initializedWidth != size.width) {
            columns.clear()
            repeat(columnCount) { i -> columns.add(RainColumn(i * 7919 + 13)) }
            initializedWidth = size.width
        }

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            columns.forEachIndexed { index, col ->
                val headY = col.y
                for (t in 0 until col.trailLength) {
                    val glyphY = headY - t * glyphHeightPx
                    if (glyphY < -glyphHeightPx || glyphY > size.height + glyphHeightPx) continue
                    val fade = 1f - (t.toFloat() / col.trailLength)
                    val alpha = (fade * ALPHA_CEILING).coerceIn(0f, ALPHA_CEILING)
                    paint.color = color.copy(alpha = alpha).toArgb()
                    nativeCanvas.drawText(
                        col.glyphs[t].toString(),
                        index * columnWidthPx,
                        glyphY,
                        paint
                    )
                }
            }
        }
    }
}
