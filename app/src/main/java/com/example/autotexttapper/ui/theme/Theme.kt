package com.example.autotexttapper.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/** Display role: heavy, wide-tracked, uppercase — the wordmark and big HUD numbers. */
val DisplayTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = TypeSize.display,
    letterSpacing = 0.28.em
)

val HudValueTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = TypeSize.hudValue,
    letterSpacing = 0.04.em
)

/** Utility role: normal weight, tight tracking — labels, log lines, status text. */
val SectionTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = TypeSize.section,
    letterSpacing = 0.22.em
)

val BodyTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = TypeSize.body,
    letterSpacing = 0.02.em
)

val LogTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = TypeSize.log
)

val MicroTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = TypeSize.micro,
    letterSpacing = 0.14.em
)

private val CollectorColorScheme = darkColorScheme(
    primary = Phosphor,
    onPrimary = Void,
    secondary = Ice,
    onSecondary = Void,
    error = Crimson,
    background = Void,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary
)

@Composable
fun CoinCollectorTheme(content: @Composable () -> Unit) {
    val selectionColors = TextSelectionColors(
        handleColor = Phosphor,
        backgroundColor = Phosphor.copy(alpha = 0.3f)
    )
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        MaterialTheme(
            colorScheme = CollectorColorScheme,
            typography = Typography(bodyLarge = BodyTextStyle),
            content = content
        )
    }
}
