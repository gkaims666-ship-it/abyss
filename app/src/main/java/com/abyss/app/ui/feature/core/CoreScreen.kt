package com.abyss.app.ui.feature.core

import com.abyss.app.ui.i18n.LocalStrings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.data.MemoryEntryEntity
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CoreScreen(palette: AbyssPalette) {
    val S = LocalStrings.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.settings.collectAsState(initial = null)
    val memCount by ServiceLocator.memory.count.collectAsState(initial = 0)
    val dlProgress by ServiceLocator.localModel.progress.collectAsState(initial = null)
    val scroll = rememberScrollState()

    var recent by remember { mutableStateOf<List<MemoryEntryEntity>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var modelUrl by remember(settings?.localModelUrl) { mutableStateOf(settings?.localModelUrl ?: "") }
    var models by remember { mutableStateOf<List<File>>(emptyList()) }

    suspend fun refresh() {
        recent = ServiceLocator.memory.recent(12)
        models = ServiceLocator.localModel.listModels()
    }
    LaunchedEffectRefresh { refresh() }

    val improvement = settings?.improvementCount ?: 0
    val version = "v1." + improvement.toString().padStart(6, '0')

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        Text(S.coreSubtitle, color = palette.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)

        Spacer(Modifier.size(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(S.knowledge, "$memCount", palette, Modifier.weight(1f))
            StatCard(S.improvements, version, palette, Modifier.weight(1f))
        }

        Spacer(Modifier.size(16.dp))
        // Self-improvement controls
        SectionTitle(S.selfImprovement, palette)
        ToggleRow(S.enableBgLearning, settings?.backgroundHarvest == true, palette) {
            scope.launch { ServiceLocator.settings.setHarvest(it) }
        }
        Text(S.bgLearningDesc,
            color = palette.onSurface.copy(alpha = 0.55f), fontSize = 11.sp)

        Spacer(Modifier.size(10.dp))
        PrimaryButton(if (running) S.working else S.runCycle, palette, enabled = !running) {
            running = true; status = null
            scope.launch {
                val h = runCatching { ServiceLocator.harvester.runOnce(settings?.harvestPerRun ?: 3) }.getOrNull()
                val r = runCatching { ServiceLocator.reflection.runOnce() }.getOrNull()
                refresh()
                running = false
                status = S.cycleResult(
                    harvested = h?.stored ?: 0,
                    needKeys = h?.skippedNoKeys == true,
                    reflection = if (r?.stored == true) (if (r.usedLocal) 1 else 2) else 0,
                )
            }
        }
        status?.let { Text(it, color = palette.primary, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp)) }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.size(16.dp))
            SectionTitle(S.recentKnowledge, palette)
            recent.forEach { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(12.dp)).background(palette.surface).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (e.kind == "reflection") "◆ ${S.reflectionTag}" else "✦ ${e.source}",
                            color = palette.secondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        Text(e.topic, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(e.content.take(220), color = palette.onSurface.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.size(20.dp))
        // Local model
        SectionTitle(S.localModelTitle, palette)
        val nativeOk = ServiceLocator.localModel.nativeAvailable()
        Text(if (nativeOk) S.nativePresent else S.nativeMissing,
            color = if (nativeOk) palette.primary else palette.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(S.ggufHelp,
            color = palette.onSurface.copy(alpha = 0.55f), fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.size(8.dp))
        TextField(value = modelUrl, onValueChange = { modelUrl = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("https://huggingface.co/…/model.Q4_K_M.gguf",
                color = palette.onSurface.copy(alpha = 0.4f)) },
            shape = RoundedCornerShape(12.dp), colors = fieldColors(palette))
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(S.saveLink, palette, Modifier.weight(1f)) {
                scope.launch { ServiceLocator.settings.setLocalModelUrl(modelUrl) }
            }
            PrimaryButton(S.download, palette, Modifier.weight(1f), enabled = modelUrl.isNotBlank()) {
                scope.launch {
                    ServiceLocator.localModel.download(modelUrl, modelUrl.substringAfterLast('/'))
                    refresh()
                }
            }
        }
        dlProgress?.let { p ->
            Spacer(Modifier.size(8.dp))
            if (p.error != null) Text("⚠ ${p.error}", color = palette.error, fontSize = 12.sp)
            else {
                val frac = if (p.total > 0) p.bytes.toFloat() / p.total else 0f
                LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = palette.primary)
                Text("${p.bytes / 1_000_000} / ${p.total / 1_000_000} ${S.mb}" + if (p.done) " · ${S.done}" else "",
                    color = palette.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }

        if (models.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            Text(S.downloadedModels, color = palette.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
            models.forEach { file ->
                val active = settings?.localModelActivePath == file.absolutePath
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (active) palette.surfaceVariant else palette.surface)
                    .clickable {
                        scope.launch {
                            status = S.loadingModel(file.name)
                            val ok = ServiceLocator.localModel.load(file)
                            if (ok) ServiceLocator.settings.setLocalModelActive(file.absolutePath)
                            status = if (ok) S.loadedModel(file.name) else S.loadFailed
                        }
                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, color = palette.onSurface, fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace)
                        Text("${file.length() / 1_000_000} ${S.mb}", color = palette.onSurface.copy(alpha = 0.5f),
                            fontSize = 11.sp)
                    }
                    if (active) Text(S.active, color = palette.primary, fontSize = 11.sp)
                }
                Spacer(Modifier.size(6.dp))
            }
        }

        Spacer(Modifier.size(60.dp))
    }
}

@Composable
private fun LaunchedEffectRefresh(block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}

@Composable
private fun StatCard(label: String, value: String, palette: AbyssPalette, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(16.dp)) {
        Text(value, color = palette.primary, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, fontSize = 22.sp)
        Text(label, color = palette.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitle(text: String, palette: AbyssPalette) {
    com.abyss.app.ui.components.SectionLabel(text, palette)
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, palette: AbyssPalette, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = palette.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PrimaryButton(
    text: String, palette: AbyssPalette, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    Box(modifier.clip(RoundedCornerShape(12.dp))
        .background(if (enabled) palette.primary else palette.surfaceVariant)
        .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) palette.onPrimary else palette.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun fieldColors(palette: AbyssPalette) = TextFieldDefaults.colors(
    focusedContainerColor = palette.surface,
    unfocusedContainerColor = palette.surface,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = palette.primary,
    focusedTextColor = palette.onSurface,
    unfocusedTextColor = palette.onSurface,
)
