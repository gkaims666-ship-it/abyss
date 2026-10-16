package com.abyss.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.provider.LlmProvider
import com.abyss.app.core.provider.ProviderRegistry
import com.abyss.app.core.translate.Languages
import com.abyss.app.ui.chat.ChatViewModel
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: ChatViewModel,
    palette: AbyssPalette,
    onOpenPermissions: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val keyStore = ServiceLocator.keyStore
    val settingsStore = ServiceLocator.settings
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val keyFields = remember {
        mutableStateMapOf<String, String>().apply {
            ProviderRegistry.all.forEach { put(it.id, keyStore.getKey(it.id) ?: "") }
        }
    }
    var langMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
            Text("Settings", color = palette.onBackground, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, fontSize = 22.sp,
                modifier = Modifier.padding(top = 8.dp))

            // Permissions entry
            Spacer(Modifier.size(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(palette.surface).clickable(onClick = onOpenPermissions).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Permissions", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Grant notifications, mic, contacts, call screening…",
                        color = palette.onSurface.copy(alpha = 0.55f), fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = palette.onSurface)
            }

            Section("API keys", palette)
            Text("Entered manually, stored encrypted on device.",
                color = palette.onSurface.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            ProviderRegistry.all.filter { it.id != "local" }.forEach { provider ->
                KeyRow(provider, keyFields[provider.id] ?: "", palette,
                    onChange = { keyFields[provider.id] = it },
                    onSave = { keyStore.setKey(provider.id, keyFields[provider.id] ?: "") })
            }

            Section("Default provider & model", palette)
            ProviderRegistry.all.forEach { provider ->
                val selected = state.settings.providerId == provider.id
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (selected) palette.surfaceVariant else palette.surface)
                    .clickable {
                        vm.setProvider(provider.id)
                        vm.setModel(provider.models.firstOrNull() ?: "")
                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(provider.displayName, color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(provider.models.joinToString(", ").take(60),
                            color = palette.onSurface.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    if (selected) Icon(Icons.Filled.Check, null, tint = palette.primary)
                }
                Spacer(Modifier.size(6.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text("Model", color = palette.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
            ProviderRegistry.byId(state.settings.providerId)?.models?.forEach { model ->
                val sel = state.settings.model == model
                Row(Modifier.fillMaxWidth().clickable { vm.setModel(model) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(model, color = if (sel) palette.primary else palette.onSurface,
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (sel) Icon(Icons.Filled.Check, null, tint = palette.primary)
                }
            }

            // Translator (X-style)
            Section("Translator", palette)
            Box {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(palette.surface).clickable { langMenu = true }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Reading language", color = palette.onSurface, modifier = Modifier.weight(1f))
                    Text(Languages.name(state.settings.targetLang), color = palette.primary, fontSize = 13.sp)
                }
                DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                    Languages.list.forEach { (code, name) ->
                        DropdownMenuItem(text = { Text(name) },
                            onClick = { scope.launch { settingsStore.setTargetLang(code) }; langMenu = false })
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
            ToggleRow("Auto-translate foreign messages", state.settings.autoTranslate, palette) {
                scope.launch { settingsStore.setAutoTranslate(it) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Engine", color = palette.onSurface, modifier = Modifier.weight(1f))
                EngineChip("On-device", state.settings.translateEngine == "mlkit", palette) {
                    scope.launch { settingsStore.setTranslateEngine("mlkit") }
                }
                Spacer(Modifier.size(8.dp))
                EngineChip("My model", state.settings.translateEngine == "llm", palette) {
                    scope.launch { settingsStore.setTranslateEngine("llm") }
                }
            }

            // Call scam guard
            Section("Call scam guard", palette)
            ToggleRow("Warn about unknown / scam callers", state.settings.guardEnabled, palette) {
                scope.launch { settingsStore.setGuardEnabled(it) }
            }
            Text("Needs the call-screening role (grant it in Permissions). " +
                "Optional data source URL with {number} improves accuracy.",
                color = palette.onSurface.copy(alpha = 0.55f), fontSize = 11.sp)
            var guardUrl by remember(state.settings.guardLookupUrl) { mutableStateOf(state.settings.guardLookupUrl) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(value = guardUrl, onValueChange = { guardUrl = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("https://…/{number}", color = palette.onSurface.copy(alpha = 0.4f)) },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors(palette))
                Spacer(Modifier.size(8.dp))
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(palette.primary)
                    .clickable { scope.launch { settingsStore.setGuardUrl(guardUrl) } }
                    .padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Save", color = palette.onPrimary, fontSize = 13.sp)
                }
            }

            // Generation
            Section("Generation", palette)
            Text("Temperature: ${"%.2f".format(state.settings.temperature)}",
                color = palette.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
            androidx.compose.material3.Slider(
                value = state.settings.temperature,
                onValueChange = { scope.launch { settingsStore.setTemperature(it) } },
                valueRange = 0f..2f)
            var sysPrompt by remember(state.settings.systemPrompt) { mutableStateOf(state.settings.systemPrompt) }
            Text("Custom system prompt (prepended to every chat)",
                color = palette.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(value = sysPrompt, onValueChange = { sysPrompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. Always answer in Russian…",
                        color = palette.onSurface.copy(alpha = 0.4f)) },
                    shape = RoundedCornerShape(12.dp), colors = fieldColors(palette))
                Spacer(Modifier.size(8.dp))
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(palette.primary)
                    .clickable { scope.launch { settingsStore.setSystemPrompt(sysPrompt) } }
                    .padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Save", color = palette.onPrimary, fontSize = 13.sp)
                }
            }

            // Self-improvement
            Section("Self-improvement", palette)
            ToggleRow("Background knowledge harvesting (spends tokens)",
                state.settings.backgroundHarvest, palette) { vm.toggleHarvest(it) }

            Spacer(Modifier.size(60.dp))
        }
    }
}

@Composable
private fun EngineChip(label: String, selected: Boolean, palette: AbyssPalette, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50))
        .background(if (selected) palette.primary else palette.surfaceVariant)
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = if (selected) palette.onPrimary else palette.onSurface, fontSize = 12.sp)
    }
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, palette: AbyssPalette, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = palette.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Section(title: String, palette: AbyssPalette) {
    Spacer(Modifier.size(22.dp))
    Text(title, color = palette.primary, fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun KeyRow(
    provider: LlmProvider, value: String, palette: AbyssPalette,
    onChange: (String) -> Unit, onSave: () -> Unit,
) {
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(provider.displayName, color = palette.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(value = value, onValueChange = { onChange(it); saved = false },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Paste key…", color = palette.onSurface.copy(alpha = 0.4f)) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                shape = RoundedCornerShape(12.dp), colors = fieldColors(palette))
            Spacer(Modifier.size(8.dp))
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(palette.primary)
                .clickable { onSave(); saved = true }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(if (saved) "Saved" else "Save", color = palette.onPrimary, fontSize = 13.sp)
            }
        }
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
