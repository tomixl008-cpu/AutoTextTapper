package com.example.autotexttapper.ui.atmosphere

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autotexttapper.ui.theme.Amber
import com.example.autotexttapper.ui.theme.Crimson
import com.example.autotexttapper.ui.theme.Phosphor
import com.example.autotexttapper.ui.theme.TextGhost
import com.example.autotexttapper.ui.theme.TextPrimary
import com.example.autotexttapper.ui.theme.Void
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private data class BootLine(val prefix: String, val resultText: String, val resultColor: Color)

private const val LABEL_WIDTH = 38

private fun padLabel(label: String): String {
    val dotsNeeded = (LABEL_WIDTH - label.length).coerceAtLeast(1)
    return label + " " + ".".repeat(dotsNeeded)
}

@Composable
fun BootOverlay(
    accessibilityEnabled: Boolean,
    fantikInstalled: Boolean,
    tiktokInstalled: Boolean,
    reduceMotion: Boolean,
    onFinished: () -> Unit
) {
    val bridgeText = if (accessibilityEnabled) "ok" else "offline"
    val bridgeColor = if (accessibilityEnabled) Phosphor else Crimson
    val aText = if (fantikInstalled) "found" else "absent"
    val aColor = if (fantikInstalled) Phosphor else Amber
    val bText = if (tiktokInstalled) "found" else "absent"
    val bColor = if (tiktokInstalled) Phosphor else Amber

    val lines = remember {
        listOf(
            "> coin_collector · boot" to null,
            padLabel("> loading motion layer") to ("ok" to Phosphor),
            padLabel("> checking accessibility bridge") to (bridgeText to bridgeColor),
            padLabel("> probe target_a: com.tikboost.fantik") to (aText to aColor),
            padLabel("> probe target_b: com.zhiliaoapp.musically") to (bText to bColor),
            padLabel("> scan interval 1000ms") to ("ok" to Phosphor),
            "> ready_" to null
        )
    }

    var visibleLineCount by remember { mutableStateOf(0) }
    var currentCharCount by remember { mutableStateOf(0) }
    var skipped by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            onFinished()
            return@LaunchedEffect
        }
        for (lineIndex in lines.indices) {
            if (skipped) break
            visibleLineCount = lineIndex + 1
            val (prefix, result) = lines[lineIndex]
            val fullText = prefix + (result?.first ?: "")
            for (c in 1..fullText.length) {
                if (skipped) break
                currentCharCount = c
                delay(7L)
            }
            currentCharCount = fullText.length
            delay(110L)
        }
        delay(400L)
        alpha.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Void)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                skipped = true
                visibleLineCount = lines.size
            },
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            for (i in 0 until visibleLineCount) {
                val (prefix, result) = lines[i]
                val fullText = prefix + (result?.first ?: "")
                val shownLength = if (i == visibleLineCount - 1 && !skipped) currentCharCount else fullText.length
                val shown = fullText.take(shownLength)

                val prefixShown = shown.take(prefix.length.coerceAtMost(shown.length))
                val resultShown = if (shown.length > prefix.length) shown.substring(prefix.length) else ""

                Box {
                    androidx.compose.foundation.layout.Row {
                        Text(
                            text = prefixShown,
                            color = TextGhost,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Text(
                            text = resultShown,
                            color = result?.second ?: TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
