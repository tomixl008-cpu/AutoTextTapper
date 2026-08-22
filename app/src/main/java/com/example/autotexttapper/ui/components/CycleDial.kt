package com.example.autotexttapper.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.autotexttapper.automation.ServiceState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.example.autotexttapper.ui.theme.Amber
import com.example.autotexttapper.ui.theme.Crimson
import com.example.autotexttapper.ui.theme.Ice
import com.example.autotexttapper.ui.theme.Phosphor
import com.example.autotexttapper.ui.theme.SurfaceRaised
import com.example.autotexttapper.ui.theme.Void
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** Fixed light source for every lit/shaded surface on the dial — never hardcode a second one. */
private const val LIGHT_ANGLE = -2.30f

private fun Color.lit(amount: Float): Color = lerp(this, Color.White, amount)
private fun Color.shaded(amount: Float): Color = lerp(this, Void, amount)

private fun colorFor(state: ServiceState): Color = when (state) {
    ServiceState.DISABLED -> Crimson
    ServiceState.READY -> Amber
    ServiceState.RUNNING -> Phosphor
    ServiceState.ERROR -> Crimson
}

private inline fun DrawScope.emboss(base: Color, alpha: Float, crossinline draw: DrawScope.(Color, Float) -> Unit) {
    translate(1.6.dp.toPx(), 1.6.dp.toPx()) { draw(base.shaded(0.72f), 0.55f * alpha) }
    translate(-1.6.dp.toPx(), -1.6.dp.toPx()) { draw(base.lit(0.55f), 0.60f * alpha) }
    draw(base, alpha)
}

/**
 * The signature element: a 3D coin with a 4-segment cycle dial around it. One Canvas.
 * segment: 0=SCAN 1=DISPATCH 2=COLLECT 3=RETURN. cycleDone: highest segment reached (-1 = none).
 */
@Composable
fun CycleDial(
    state: ServiceState,
    segment: Int,
    cycleDone: Int,
    collectTick: Long,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val color = colorFor(state)
    val haptics = LocalHapticFeedback.current

    // ---- Spin physics: velocity-lerp, not angle-lerp, so the coin has weight ----
    val latestState = rememberUpdatedState(state)
    val angleState = remember { mutableFloatStateOf(0f) }
    var boostUntilNanos by remember { mutableStateOf(0L) }
    var lastKnownState by remember { mutableStateOf(state) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            angleState.floatValue = if (state == ServiceState.DISABLED) 0.42f else 0f
            return@LaunchedEffect
        }
        var velocity = 0f
        var lastFrameNanos = android.os.SystemClock.elapsedRealtimeNanos()
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { frameNanos ->
                val dtMs = ((frameNanos - lastFrameNanos) / 1_000_000f).coerceIn(0f, 50f)
                lastFrameNanos = frameNanos

                val current = latestState.value
                if (current != lastKnownState) {
                    if (lastKnownState != ServiceState.RUNNING && current == ServiceState.RUNNING) {
                        boostUntilNanos = frameNanos + 260_000_000L
                    }
                    lastKnownState = current
                }

                val periodMs = when (current) {
                    ServiceState.DISABLED -> null
                    ServiceState.READY -> 7200f
                    ServiceState.RUNNING -> 2100f
                    ServiceState.ERROR -> 600f
                }
                val targetVelocity = if (periodMs == null) 0f else (2f * Math.PI.toFloat() / periodMs)
                val boost = if (frameNanos < boostUntilNanos) 1.42f else 1f
                val k = if (targetVelocity > 0f) 0.0042f else 0.0016f

                velocity += (targetVelocity * boost - velocity) * k * dtMs
                angleState.floatValue += velocity * dtMs

                if (targetVelocity == 0f && abs(velocity) < 0.00012f) {
                    val restBase = 0.42f
                    val twoPi = 2f * Math.PI.toFloat()
                    val n = round((angleState.floatValue - restBase) / twoPi)
                    val restAngle = restBase + n * twoPi
                    angleState.floatValue += (restAngle - angleState.floatValue) * 0.006f * dtMs
                }
            }
        }
    }

    // ---- Collect moment: ice flash + ring pulse + haptic, fired on every collectTick bump ----
    val collectProgress = remember { Animatable(1f) } // 1f = idle/settled
    val iceMix = remember { Animatable(0f) } // 0 = base color, 1 = full ice
    LaunchedEffect(collectTick) {
        if (collectTick == 0L) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        iceMix.snapTo(1f)
        collectProgress.snapTo(0f)
        coroutineScope {
            launch { collectProgress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch { iceMix.animateTo(0f, tween(420, easing = LinearEasing)) }
        }
    }

    val sweepAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            sweepAnim.snapTo(0f)
            sweepAnim.animateTo(1f, tween(2800, easing = LinearEasing))
        }
    }

    val displayColor = lerp(color, Ice, iceMix.value)

    Box(modifier = modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(170.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val R = 44.dp.toPx()
            val theta = angleState.floatValue
            val cosT = cos(theta)
            val sinT = sin(theta)
            val faceAlpha = if (state == ServiceState.DISABLED) abs(cosT).toFloat() * 0.45f else abs(cosT).toFloat()
            val horizontalScale = abs(cosT).toFloat().coerceAtLeast(0.02f)

            // Ambient glow
            if (state != ServiceState.DISABLED) {
                val breathAlpha = 0.18f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(displayColor.copy(alpha = breathAlpha), displayColor.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = 80.dp.toPx()
                    ),
                    radius = 80.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }

            // Machined groove ring at 81dp — lit toward LIGHT_ANGLE, shaded away from it
            val grooveR = 81.dp.toPx()
            val grooveSegments = 48
            for (i in 0 until grooveSegments) {
                val a0 = (2 * Math.PI * i / grooveSegments)
                val a1 = (2 * Math.PI * (i + 0.6) / grooveSegments)
                val mid = (a0 + a1) / 2f
                val lightDot = cos(mid - LIGHT_ANGLE)
                val segColor = if (lightDot > 0) SurfaceRaised.lit((0.16f * lightDot).toFloat())
                else SurfaceRaised.shaded(0.90f)
                drawArc(
                    color = segColor,
                    startAngle = Math.toDegrees(a0).toFloat() - 90f,
                    sweepAngle = Math.toDegrees(a1 - a0).toFloat(),
                    useCenter = false,
                    topLeft = Offset(cx - grooveR, cy - grooveR),
                    size = Size(grooveR * 2f, grooveR * 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Precision bezel at 75dp — 72 ticks, every 18th longer/brighter
            val bezelR = 75.dp.toPx()
            for (i in 0 until 72) {
                val angle = 2 * Math.PI * i / 72 - Math.PI / 2
                val isQuarter = i % 18 == 0
                val len = if (isQuarter) 9.dp.toPx() else 4.dp.toPx()
                val alpha = if (isQuarter) 0.22f else 0.09f
                val width = if (isQuarter) 1.6.dp.toPx() else 1.dp.toPx()
                val ox = cos(angle).toFloat()
                val oy = sin(angle).toFloat()
                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = Offset(cx + ox * bezelR, cy + oy * bezelR),
                    end = Offset(cx + ox * (bezelR + len), cy + oy * (bezelR + len)),
                    strokeWidth = width
                )
            }

            // Cycle dial: 4 arc segments at radius 67dp
            drawPhaseArcs(cx, cy, radius = 67.dp.toPx(), activeSegment = segment, cycleDone = cycleDone, color = color)

            // Collect ring pulse
            if (collectProgress.value < 1f) {
                val p = collectProgress.value
                val ringR = 67.dp.toPx() + (111.dp.toPx() - 67.dp.toPx()) * p
                drawCircle(
                    color = Ice.copy(alpha = (0.8f * (1f - p)).coerceIn(0f, 1f)),
                    radius = ringR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Edge slab — lit cylinder with profiled milled ridges
            val edgeHalfWidth = R * abs(sinT).toFloat() * 0.11f
            if (edgeHalfWidth > 0.5f) {
                val slabLeft = cx - edgeHalfWidth
                val slabWidth = edgeHalfWidth * 2f
                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to displayColor.shaded(0.62f),
                            0.34f to displayColor.lit(0.34f),
                            0.66f to displayColor,
                            1.0f to displayColor.shaded(0.70f)
                        ),
                        start = Offset(slabLeft, cy),
                        end = Offset(slabLeft + slabWidth, cy)
                    ),
                    topLeft = Offset(slabLeft, cy - R),
                    size = Size(slabWidth, R * 2f)
                )
                val ridgeCount = 15
                for (r in 0 until ridgeCount) {
                    val fx = (r + 0.5f) / ridgeCount
                    val x = slabLeft + slabWidth * fx
                    val nx = (fx - 0.5f) * 2f
                    val profile = sqrt((1f - (nx * nx)).coerceAtLeast(0f))
                    val ridgeHalfHeight = R * profile
                    drawLine(
                        color = Void.copy(alpha = 0.34f),
                        start = Offset(x, cy - ridgeHalfHeight),
                        end = Offset(x, cy + ridgeHalfHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // Coin face — squashed horizontally, embossed strokes
            if (faceAlpha > 0.02f) {
                scaleHorizontal(horizontalScale, Offset(cx, cy)) {
                    emboss(displayColor, faceAlpha) { c, a ->
                        drawCircle(color = c.copy(alpha = a), radius = R, center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    }
                    emboss(displayColor, faceAlpha * 0.5f) { c, a ->
                        drawCircle(color = c.copy(alpha = a), radius = R * 0.74f, center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
                    }
                    // rim ticks — flat, not embossed
                    for (i in 0 until 28) {
                        val angle = 2 * Math.PI * i / 28
                        val ox = cos(angle).toFloat()
                        val oy = sin(angle).toFloat()
                        val outerR = R
                        val innerR = R - 4.dp.toPx()
                        drawLine(
                            color = displayColor.copy(alpha = faceAlpha * 0.35f),
                            start = Offset(cx + ox * innerR, cy + oy * innerR),
                            end = Offset(cx + ox * outerR, cy + oy * outerR),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    // hex glyph — embossed
                    val hexRadius = R * 0.29f
                    val hexPath = Path().apply {
                        for (i in 0..6) {
                            val angle = Math.PI / 3.0 * i - Math.PI / 2.0
                            val x = cx + (hexRadius * cos(angle)).toFloat()
                            val y = cy + (hexRadius * sin(angle)).toFloat()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    emboss(displayColor, faceAlpha) { c, a ->
                        drawPath(path = hexPath, color = c.copy(alpha = a), style = Stroke(2.dp.toPx()))
                    }
                    // specular sweep (replaced by a hard glint during a collect)
                    val sweepFrac = if (collectProgress.value < 1f) (collectProgress.value / 0.6f).coerceAtMost(1f) else sweepAnim.value
                    val sweepAlpha = if (collectProgress.value < 1f) 0.40f else 0.11f
                    val sweepX = cx - R + (2 * R) * sweepFrac
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = sweepAlpha * faceAlpha),
                                Color.White.copy(alpha = 0f)
                            ),
                            start = Offset(sweepX - 30.dp.toPx(), cy - R),
                            end = Offset(sweepX + 30.dp.toPx(), cy + R)
                        ),
                        topLeft = Offset(cx - R, cy - R),
                        size = Size(R * 2f, R * 2f)
                    )
                }
            }

            // Rim flash — the heartbeat, twice per revolution
            if (!reduceMotion && state != ServiceState.DISABLED && abs(cosT) < 0.15) {
                val flashAlpha = (1f - abs(cosT).toFloat() / 0.15f).coerceIn(0f, 1f) * 0.9f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(displayColor.copy(alpha = flashAlpha), displayColor.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = 40.dp.toPx()
                    ),
                    topLeft = Offset(cx - 10.dp.toPx(), cy - R - 7.dp.toPx()),
                    size = Size(20.dp.toPx(), (R + 7.dp.toPx()) * 2f)
                )
            }
        }
    }
}

private fun DrawScope.scaleHorizontal(scaleX: Float, pivot: Offset, block: DrawScope.() -> Unit) {
    androidx.compose.ui.graphics.drawscope.scale(scaleX, 1f, pivot, block)
}

private fun DrawScope.drawPhaseArcs(cx: Float, cy: Float, radius: Float, activeSegment: Int, cycleDone: Int, color: Color) {
    val gapDeg = 7f
    val segmentSpan = 90f - gapDeg
    for (seg in 0 until 4) {
        val startAngle = -90f + seg * 90f + gapDeg / 2f
        val isActive = seg == activeSegment
        val isCompleted = seg <= cycleDone && !isActive
        val (strokeWidth, alpha) = when {
            isActive -> 5.dp.toPx() to 1f
            isCompleted -> 2.5.dp.toPx() to 0.42f
            else -> 2.5.dp.toPx() to 0.13f
        }
        if (isActive) {
            drawArc(
                color = color.copy(alpha = 0.35f),
                startAngle = startAngle,
                sweepAngle = segmentSpan,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeWidth + 6.dp.toPx())
            )
        }
        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = startAngle,
            sweepAngle = segmentSpan,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth)
        )
    }
}
