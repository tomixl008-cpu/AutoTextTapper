package com.example.autotexttapper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.autotexttapper.ui.theme.BodyTextStyle
import com.example.autotexttapper.ui.theme.Crimson
import com.example.autotexttapper.ui.theme.Radius
import com.example.autotexttapper.ui.theme.SectionTextStyle
import com.example.autotexttapper.ui.theme.Spacing

@Composable
fun AccessBanner(visible: Boolean, onGrantClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 4 },
        exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(Radius.panel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(shape)
                .background(Crimson.copy(alpha = 0.06f))
                .border(1.dp, Crimson.copy(alpha = 0.35f), shape)
                .clickable(onClick = onGrantClick)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "[!]", style = BodyTextStyle, color = Crimson)
            Box(modifier = Modifier.padding(start = Spacing.xs).weight(1f)) {
                Text(text = "accessibility access required", style = BodyTextStyle, color = Crimson)
            }
            Text(
                text = "GRANT",
                style = SectionTextStyle,
                color = Crimson,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}
