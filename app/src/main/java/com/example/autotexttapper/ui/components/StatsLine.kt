package com.example.autotexttapper.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.autotexttapper.ui.theme.BodyTextStyle
import com.example.autotexttapper.ui.theme.HudValueTextStyle
import com.example.autotexttapper.ui.theme.Ice
import com.example.autotexttapper.ui.theme.MicroTextStyle
import com.example.autotexttapper.ui.theme.PhosphorDim
import com.example.autotexttapper.ui.theme.TextGhost
import com.example.autotexttapper.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun StatsLine(
    likeCount: Int,
    skipCount: Int,
    sessionStart: Long?,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    val collectedColor = if (running) Ice else TextGhost
    val skippedColor = if (running) PhosphorDim else TextGhost
    val wordColor = if (running) TextGhost else TextGhost

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumber(value = likeCount, color = collectedColor)
            Text(text = " collected ", style = BodyTextStyle, color = wordColor)
            Text(text = "·", style = BodyTextStyle, color = wordColor)
            RollingNumber(value = skipCount, color = skippedColor, prefix = " ")
            Text(text = " skipped", style = BodyTextStyle, color = wordColor)
        }
        Text(
            text = uptimeText(sessionStart),
            style = MicroTextStyle.copy(letterSpacing = 0.18.em),
            color = if (running) TextSecondary else TextGhost
        )
    }
}

@Composable
private fun RollingNumber(value: Int, color: androidx.compose.ui.graphics.Color, prefix: String = "") {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically(tween(240)) { it / 2 } + fadeIn(tween(240))) togetherWith
                (slideOutVertically(tween(240)) { -it / 2 } + fadeOut(tween(240)))
        },
        label = "rolling-number"
    ) { animatedValue ->
        Text(text = "$prefix$animatedValue", style = HudValueTextStyle, color = color)
    }
}

private fun formatUptime(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
private fun uptimeText(sessionStart: Long?): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sessionStart) {
        while (sessionStart != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return if (sessionStart == null) "00:00:00" else formatUptime(now - sessionStart)
}
