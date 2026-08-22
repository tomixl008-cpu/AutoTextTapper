package com.example.autotexttapper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.autotexttapper.automation.LogEntry
import com.example.autotexttapper.automation.LogTag
import com.example.autotexttapper.ui.theme.Amber
import com.example.autotexttapper.ui.theme.Crimson
import com.example.autotexttapper.ui.theme.Hairline
import com.example.autotexttapper.ui.theme.Ice
import com.example.autotexttapper.ui.theme.LogTextStyle
import com.example.autotexttapper.ui.theme.MicroTextStyle
import com.example.autotexttapper.ui.theme.Phosphor
import com.example.autotexttapper.ui.theme.PhosphorDim
import com.example.autotexttapper.ui.theme.SectionTextStyle
import com.example.autotexttapper.ui.theme.Spacing
import com.example.autotexttapper.ui.theme.SurfaceRaised
import com.example.autotexttapper.ui.theme.TextGhost
import com.example.autotexttapper.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun tagSymbol(tag: LogTag): String = when (tag) {
    LogTag.TICK -> "[·]"
    LogTag.ACTION -> "[»]"
    LogTag.WARN -> "[!]"
    LogTag.FAIL -> "[×]"
    LogTag.OK -> "[✓]"
}

private fun tagColor(tag: LogTag): Color = when (tag) {
    LogTag.TICK -> TextSecondary
    LogTag.ACTION -> Ice
    LogTag.WARN -> Amber
    LogTag.FAIL -> Crimson
    LogTag.OK -> Phosphor
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
fun LogConsole(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= entries.size - 1
        }
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty() && isAtBottom) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(SurfaceRaised)
            .border(1.dp, Hairline)
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "LIVE_LOG", style = SectionTextStyle, color = PhosphorDim)
                Text(text = "${entries.size} lines", style = MicroTextStyle, color = TextGhost)
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hairline))

            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(Spacing.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.timestamp.toString() + entry.message.hashCode() }) { _, entry ->
                        LogLine(entry)
                    }
                }

                AnimatedVisibility(
                    visible = !isAtBottom && entries.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .background(SurfaceRaised)
                            .border(1.dp, Hairline)
                            .clickable {
                                scope.launch {
                                    if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
                                }
                            }
                            .padding(horizontal = Spacing.sm, vertical = 4.dp)
                    ) {
                        Text(text = "▼ latest", style = MicroTextStyle, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    var displayedLength by remember(entry.timestamp, entry.message) { mutableStateOf(0) }
    LaunchedEffect(entry.timestamp, entry.message) {
        for (i in 1..entry.message.length) {
            displayedLength = i
            kotlinx.coroutines.delay(6L)
        }
    }
    val shown = entry.message.take(displayedLength)
    val time = remember(entry.timestamp) { timeFormat.format(Date(entry.timestamp)) }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(text = "[$time]", style = LogTextStyle, color = TextGhost)
        Box(modifier = Modifier.padding(start = Spacing.xs)) {
            Text(text = tagSymbol(entry.tag), style = LogTextStyle, color = tagColor(entry.tag))
        }
        Box(modifier = Modifier.padding(start = Spacing.xs)) {
            Text(text = shown, style = LogTextStyle, color = TextSecondary)
        }
    }
}
