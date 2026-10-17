package com.abyss.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.debug.CodeReviewer
import com.abyss.app.core.debug.PerfMonitor
import com.abyss.app.ui.i18n.LocalStrings
import com.abyss.app.ui.theme.AbyssFonts
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The self-review surface. One tap runs [CodeReviewer]: the best coding model
 * available reads the app's source from GitHub, is given this device's live
 * frame metrics, and streams back a ranked list of flaws and proposed changes.
 */
@Composable
fun DebugReviewScreen(palette: AbyssPalette) {
    val S = LocalStrings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scroll = rememberScrollState()

    // A live snapshot of the current frame performance, refreshed on compose.
    val perf = remember(running, output.length) { PerfMonitor.report() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Live performance card.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(palette.surface.copy(alpha = if (palette.dark) 0.86f else 0.96f))
                .padding(14.dp),
        ) {
            Text(perf, color = palette.onSurface.copy(alpha = 0.85f),
                fontSize = 12.sp, fontFamily = AbyssFonts.Code)
        }

        Spacer(Modifier.size(12.dp))

        // Run / stop + copy.
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(palette.primary)
                    .clickable(enabled = !running) {
                        output = ""
                        running = true
                        job = scope.launch {
                            CodeReviewer.review(ServiceLocator.keyStore)
                                .catch { output += "\n\n[error] ${it.message}" }
                                .collect { output += it }
                            running = false
                        }
                    }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running) {
                    CircularProgressIndicator(color = palette.onPrimary, strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(S.reviewRunning, color = palette.onPrimary, fontSize = 14.sp)
                } else {
                    Icon(Icons.Filled.PlayArrow, null, tint = palette.onPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(S.reviewRun, color = palette.onPrimary, fontSize = 14.sp)
                }
            }
            if (output.isNotBlank()) {
                Icon(Icons.Filled.ContentCopy, S.reviewCopy, tint = palette.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceVariant).padding(13.dp).size(18.dp)
                        .clickable { clipboard.setText(AnnotatedString(output)) })
            }
        }

        Spacer(Modifier.size(14.dp))

        // Streamed review.
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))
                .background(palette.surface.copy(alpha = if (palette.dark) 0.7f else 0.96f))
                .padding(14.dp).verticalScroll(scroll),
        ) {
            Text(
                output.ifBlank { S.reviewIdle },
                color = if (output.isBlank()) palette.onSurface.copy(alpha = 0.5f) else palette.onSurface,
                fontSize = 13.sp,
                fontFamily = if (output.isBlank()) AbyssFonts.Display else AbyssFonts.Code,
            )
        }
    }

    // Keep the newest output in view while streaming.
    LaunchedEffectScrollToEnd(output, scroll)
}

@Composable
private fun LaunchedEffectScrollToEnd(key: String, scroll: androidx.compose.foundation.ScrollState) {
    androidx.compose.runtime.LaunchedEffect(key) {
        if (key.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }
}
