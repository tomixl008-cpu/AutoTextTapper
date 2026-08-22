package com.example.autotexttapper.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Surfaces
val Void = Color(0xFF040604)
val Surface = Color(0xFF080D09)
val SurfaceRaised = Color(0xFF0C130E)
val Hairline = Color(0x1A00FF88)

// Signal colors (state)
val Phosphor = Color(0xFF00FF88)
val PhosphorDim = Color(0xFF0A9E5C)
val Ice = Color(0xFF00E5FF)
val Amber = Color(0xFFFFB020)
val Crimson = Color(0xFFFF2D55)

// Text
val TextPrimary = Color(0xFFD7FFE9)
val TextSecondary = Color(0xFF8FB39F) // lightened from spec value to clear 4.5:1 on Surface
val TextGhost = Color(0xFF33493D)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val screenPadding = 24.dp
}

object Radius {
    val panel = 2.dp
    val none = 0.dp
}

object TypeSize {
    val display = 26.sp
    val hudValue = 24.sp
    val section = 11.sp
    val body = 13.sp
    val log = 11.sp
    val micro = 9.sp
}
