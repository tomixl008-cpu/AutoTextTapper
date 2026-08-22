package com.example.autotexttapper.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.autotexttapper.automation.ServiceState
import com.example.autotexttapper.ui.theme.Crimson
import com.example.autotexttapper.ui.theme.Phosphor
import com.example.autotexttapper.ui.theme.Radius
import com.example.autotexttapper.ui.theme.SectionTextStyle

private data class PowerVisual(val glyph: String, val label: String, val color: Color, val dashed: Boolean, val brackets: Boolean)

private fun visualFor(state: ServiceState): PowerVisual = when (state) {
    ServiceState.DISABLED -> PowerVisual("[►]", "Start", Crimson, dashed = true, brackets = false)
    ServiceState.READY -> PowerVisual("[►]", "Start", Phosphor, dashed = false, brackets = true)
    ServiceState.RUNNING -> PowerVisual("[■]", "Stop", Crimson, dashed = false, brackets = true)
    ServiceState.ERROR -> PowerVisual("[►]", "Start", Crimson, dashed = false, brackets = true)
}

/** One physical control that morphs between Start and Stop, the way a real power switch does. */
@Composable
fun PowerButton(
    state: ServiceState,
    onStartRequested: () -> Unit,
    onStopRequested: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = visualFor(state)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val bracketOffset by animateFloatAsState(
        targetValue = if (pressed) 3f else 0f,
        animationSpec = tween(110),
        label = "bracket-offset"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else if (state == ServiceState.DISABLED) 0.4f else 0.7f,
        animationSpec = tween(110),
        label = "border-alpha"
    )
    val scaleValue by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(90),
        label = "press-scale"
    )

    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.panel)
    val description = when (state) {
        ServiceState.DISABLED -> "Open accessibility settings"
        ServiceState.READY -> "Start automation"
        ServiceState.RUNNING -> "Stop automation"
        ServiceState.ERROR -> "Start automation"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .scale(scaleValue)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    when (state) {
                        ServiceState.DISABLED -> onOpenAccessibilitySettings()
                        ServiceState.RUNNING -> onStopRequested()
                        else -> onStartRequested()
                    }
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.5.dp.toPx()
            drawRoundRect(
                color = visual.color.copy(alpha = borderAlpha.coerceIn(0f, 1f)),
                cornerRadius = CornerRadius(Radius.panel.toPx()),
                style = if (visual.dashed) {
                    Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                } else {
                    Stroke(width = strokeWidth)
                }
            )

            if (visual.brackets) {
                val bracketLen = 12.dp.toPx()
                val inset = bracketOffset.dp.toPx()
                val bw = 2.dp.toPx()
                val c = visual.color
                drawLine(c, Offset(-inset, bracketLen - inset), Offset(-inset, -inset), bw)
                drawLine(c, Offset(-inset, -inset), Offset(bracketLen - inset, -inset), bw)
                drawLine(c, Offset(size.width - bracketLen + inset, -inset), Offset(size.width + inset, -inset), bw)
                drawLine(c, Offset(size.width + inset, -inset), Offset(size.width + inset, bracketLen - inset), bw)
                drawLine(c, Offset(-inset, size.height - bracketLen + inset), Offset(-inset, size.height + inset), bw)
                drawLine(c, Offset(-inset, size.height + inset), Offset(bracketLen - inset, size.height + inset), bw)
                drawLine(c, Offset(size.width - bracketLen + inset, size.height + inset), Offset(size.width + inset, size.height + inset), bw)
                drawLine(c, Offset(size.width + inset, size.height - bracketLen + inset), Offset(size.width + inset, size.height + inset), bw)
            }
        }

        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = visual.glyph to visual.label,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "power-content"
            ) { (glyph, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = glyph, style = SectionTextStyle, color = visual.color)
                    Box(modifier = Modifier.width(11.dp))
                    Text(text = label, style = SectionTextStyle, color = visual.color)
                }
            }
        }
    }
}
