package com.abyss.app.ui.feature.translate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.translate.Languages
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.launch

@Composable
fun TranslatorScreen(palette: AbyssPalette) {
    val settings by ServiceLocator.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var detected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var langMenu by remember { mutableStateOf(false) }

    val target = settings?.targetLang ?: "en"
    val engine = settings?.translateEngine ?: "mlkit"
    val scroll = rememberScrollState()

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(scroll).padding(16.dp),
    ) {
        Text(
            "On-device · works offline" + if (engine == "llm") " · using your model" else "",
            color = palette.onSurface.copy(alpha = 0.55f), fontSize = 12.sp)
        Spacer(Modifier.size(16.dp))

        // Source card
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(palette.surface).padding(4.dp)) {
            TextField(
                value = input,
                onValueChange = { input = it; error = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("Enter text…", color = palette.onSurface.copy(alpha = 0.4f)) },
                colors = transparentFieldColors(palette),
            )
        }
        detected?.let {
            Text("Detected: ${Languages.name(it)}", color = palette.primary.copy(alpha = 0.8f),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp, start = 6.dp))
        }

        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(palette.surfaceVariant).clickable { langMenu = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.SwapVert, null, tint = palette.primary)
                    Spacer(Modifier.size(6.dp))
                    Text("to ${Languages.name(target)}", color = palette.onSurface, fontSize = 13.sp)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = langMenu, onDismissRequest = { langMenu = false }) {
                    Languages.list.forEach { (code, name) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                scope.launch { ServiceLocator.settings.setTargetLang(code) }
                                langMenu = false
                            })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TranslateButton(palette, busy) {
                val s = settings ?: return@TranslateButton
                if (input.isBlank()) return@TranslateButton
                busy = true; error = null
                scope.launch {
                    val res = ServiceLocator.translate.translate(input, s)
                    busy = false
                    res.onSuccess { output = it.text; detected = it.sourceLang }
                        .onFailure { error = it.message }
                }
            }
        }

        Spacer(Modifier.size(16.dp))
        AnimatedVisibility(visible = output.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().animateContentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.assistantBubble).padding(16.dp)) {
                Column {
                    Text(output, color = palette.onSurface, fontSize = 16.sp)
                    Spacer(Modifier.size(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (engine == "llm") "Translated by your model" else "Translated on-device",
                            color = palette.onSurface.copy(alpha = 0.5f), fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ContentCopy, "Copy",
                            tint = palette.primary,
                            modifier = Modifier.size(18.dp)
                                .clickable { clipboard.setText(AnnotatedString(output)) })
                    }
                }
            }
        }
        error?.let {
            Text("⚠ $it", color = palette.error, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun TranslateButton(palette: AbyssPalette, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(palette.primary)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(
            color = palette.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Text("Translate", color = palette.onPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun transparentFieldColors(palette: AbyssPalette) = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = palette.primary,
    focusedTextColor = palette.onSurface,
    unfocusedTextColor = palette.onSurface,
)
