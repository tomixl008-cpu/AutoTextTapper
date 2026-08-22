package com.example.autotexttapper.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.autotexttapper.automation.ServiceState
import com.example.autotexttapper.ui.theme.Amber
import com.example.autotexttapper.ui.theme.BodyTextStyle
import com.example.autotexttapper.ui.theme.Crimson
import com.example.autotexttapper.ui.theme.Phosphor

private fun colorFor(state: ServiceState) = when (state) {
    ServiceState.DISABLED -> Crimson
    ServiceState.READY -> Amber
    ServiceState.RUNNING -> Phosphor
    ServiceState.ERROR -> Crimson
}

/** A single centred line under the dial — no panel, no border, no background. */
@Composable
fun PhaseLine(state: ServiceState, phase: String, modifier: Modifier = Modifier) {
    val color = colorFor(state)
    val infinite = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1060
                1f at 0
                1f at 529
                0f at 530
                0f at 1059
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "cursor-alpha"
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = phase, style = BodyTextStyle, color = color)
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(width = 5.dp, height = 11.dp)
                .background(color.copy(alpha = cursorAlpha))
        )
    }
}
