package com.example.autotexttapper.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.autotexttapper.automation.ServiceState
import com.example.autotexttapper.ui.theme.Amber
import com.example.autotexttapper.ui.theme.Crimson
import com.example.autotexttapper.ui.theme.MicroTextStyle
import com.example.autotexttapper.ui.theme.Phosphor
import com.example.autotexttapper.ui.theme.TextGhost

private fun labelFor(state: ServiceState) = when (state) {
    ServiceState.DISABLED -> "offline"
    ServiceState.READY -> "armed"
    ServiceState.RUNNING -> "active"
    ServiceState.ERROR -> "fault"
}

/** A quiet 16dp top row — not a hero wordmark, the vertical space belongs to the dial. */
@Composable
fun HeaderStrip(state: ServiceState, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val jitter = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (reduceMotion) return@LaunchedEffect
        jitter.snapTo(1f)
        jitter.animateTo(0f, tween(160, easing = LinearEasing))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "coin collector", style = MicroTextStyle, color = TextGhost)
        Box {
            val j = jitter.value
            if (j > 0.01f) {
                Text(
                    text = labelFor(state),
                    style = MicroTextStyle,
                    color = Crimson.copy(alpha = j),
                    modifier = Modifier.graphicsLayer { translationX = -1.5.dp.toPx() * j }
                )
                Text(
                    text = labelFor(state),
                    style = MicroTextStyle,
                    color = Amber.copy(alpha = j),
                    modifier = Modifier.graphicsLayer { translationX = 1.5.dp.toPx() * j }
                )
            }
            Text(text = labelFor(state), style = MicroTextStyle, color = TextGhost)
        }
    }
}
