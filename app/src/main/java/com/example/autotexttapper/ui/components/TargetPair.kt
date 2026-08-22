package com.example.autotexttapper.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.autotexttapper.automation.Target
import com.example.autotexttapper.ui.theme.Amber
import com.example.autotexttapper.ui.theme.BodyTextStyle
import com.example.autotexttapper.ui.theme.Ice
import com.example.autotexttapper.ui.theme.MicroTextStyle
import com.example.autotexttapper.ui.theme.Phosphor
import com.example.autotexttapper.ui.theme.TextGhost
import com.example.autotexttapper.ui.theme.TextPrimary
import com.example.autotexttapper.ui.theme.TextSecondary

@Composable
fun TargetPair(
    foreground: Target,
    running: Boolean,
    fantikInstalled: Boolean,
    tiktokInstalled: Boolean,
    modifier: Modifier = Modifier
) {
    val arrowShift by animateDpAsState(
        targetValue = when {
            foreground == Target.FANTIK -> (-3).dp
            foreground == Target.TIKTOK -> 3.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "arrow-shift"
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TargetSide(
            name = "fantik",
            installed = fantikInstalled,
            isForeground = foreground == Target.FANTIK,
            activeColor = Phosphor,
            running = running
        )
        Text(
            text = "⇄",
            style = BodyTextStyle,
            color = if (running) TextSecondary else TextGhost,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .offset(x = arrowShift)
        )
        TargetSide(
            name = "tiktok",
            installed = tiktokInstalled,
            isForeground = foreground == Target.TIKTOK,
            activeColor = Ice,
            running = running
        )
    }
}

@Composable
private fun TargetSide(
    name: String,
    installed: Boolean,
    isForeground: Boolean,
    activeColor: Color,
    running: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val dotColor = if (isForeground && running) activeColor else TextGhost
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(6.dp).background(dotColor, CircleShape)
        )
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(start = 6.dp)) {
            Text(
                text = name,
                style = BodyTextStyle,
                color = if (isForeground && running) TextPrimary else TextGhost,
                textDecoration = if (!installed) TextDecoration.LineThrough else TextDecoration.None
            )
            if (!installed) {
                Text(text = "absent", style = MicroTextStyle, color = Amber)
            }
        }
    }
}
