package com.abyss.app.ui.settings

import com.abyss.app.ui.background.abyssObstacle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.provider.LlmProvider
import com.abyss.app.core.provider.ProviderRegistry
import com.abyss.app.core.translate.Languages
import com.abyss.app.ui.i18n.I18n
import com.abyss.app.ui.i18n.LocalStrings
import com.abyss.app.ui.i18n.Strings
import com.abyss.app.ui.chat.ChatViewModel
import com.abyss.app.ui.chat.PalettePicker
import com.abyss.app.ui.components.Appear
import com.abyss.app.ui.components.ScreenTitle
import com.abyss.app.ui.components.SectionLabel
import com.abyss.app.ui.theme.AbyssFonts
import com.abyss.app.ui.theme.AbyssPalette
import com.abyss.app.ui.theme.Palettes
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: ChatViewModel,
    palette: AbyssPalette,
    onOpenPermissions: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val s = state.settings
    val keyStore = ServiceLocator.keyStore
    val store = ServiceLocator.settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val keyFields = remember {
        mutableStateMapOf<String, String>().apply {
            ProviderRegistry.all.forEach { put(it.id, keyStore.getKey(it.id) ?: "") }
        }
    }
    val keySet = remember {
        mutableStateMapOf<String, Boolean>().apply {
            ProviderRegistry.all.forEach { put(it.id, keyStore.hasKey(it.id)) }
        }
    }
    var openKey by remember { mutableStateOf<String?>(null) }
    var showThemes by remember { mutableStateOf(false) }
    var langMenu by remember { mutableStateOf(false) }
    var uiLangMenu by remember { mutableStateOf(false) }
    val S = LocalStrings.current
    BackHandler(enabled = showThemes) { showThemes = false }

    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            ScreenTitle(S.settings, palette, subtitle = S.settingsSubtitle,
                modifier = Modifier.padding(top = 14.dp))

            // ---------------------------------------------------------------- you
            Appear(0) {
                Column {
                    SectionLabel(S.secYou, palette)
                    Group(palette) {
                        var name by remember(s.userName) { mutableStateOf(s.userName) }
                        InputRow(
                            icon = Icons.Filled.Person, palette = palette,
                            title = S.yourName, value = name, placeholder = S.yourNameHint,
                            onValueChange = { name = it },
                            onCommit = { scope.launch { store.setUserName(name) } },
                            dirty = name.trim() != s.userName,
                        )
                        Divider(palette)
                        NavRow(Icons.Filled.Palette, S.theme, palette,
                            value = Palettes.byId(s.paletteId).displayName) { showThemes = true }
                        Divider(palette)
                        Box {
                            val systemLang = remember { I18n.fromLocale(java.util.Locale.getDefault()) }
                            NavRow(Icons.Filled.Language, S.language, palette,
                                value = if (s.language == "system") S.languageSystem(systemLang.nativeName)
                                else I18n.all.firstOrNull { it.code == s.language }?.nativeName ?: s.language,
                            ) { uiLangMenu = true }
                            DropdownMenu(expanded = uiLangMenu, onDismissRequest = { uiLangMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(S.languageSystem(systemLang.nativeName),
                                        fontWeight = if (s.language == "system") FontWeight.SemiBold else FontWeight.Normal) },
                                    onClick = { vm.setLanguage("system"); uiLangMenu = false },
                                )
                                I18n.all.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang.nativeName,
                                            fontWeight = if (s.language == lang.code) FontWeight.SemiBold else FontWeight.Normal) },
                                        onClick = { vm.setLanguage(lang.code); uiLangMenu = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- model
            Appear(60) {
                Column {
                    SectionLabel(S.secModel, palette)
                    Group(palette) {
                        ProviderRegistry.all.forEachIndexed { i, p ->
                            if (i > 0) Divider(palette)
                            val selected = s.providerId == p.id
                            RadioRow(palette, title = p.displayName,
                                subtitle = if (selected) s.model else statusText(S, p, keySet[p.id] == true),
                                selected = selected) {
                                vm.setProvider(p.id)
                                vm.setModel(p.models.firstOrNull() ?: "")
                            }
                            AnimatedVisibility(selected, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        p.models.forEach { m ->
                                            Chip(m, selected = s.model == m, palette = palette) { vm.setModel(m) }
                                        }
                                    }
                                    Spacer(Modifier.size(10.dp))
                                    var custom by remember(p.id) { mutableStateOf("") }
                                    CompactField(custom, { custom = it }, S.anyModelId, palette,
                                        action = S.use, enabled = custom.isNotBlank()) {
                                        vm.setModel(custom.trim()); custom = ""
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- keys
            Appear(120) {
                Column {
                    SectionLabel(S.secKeys, palette)
                    Group(palette) {
                        ProviderRegistry.all.filter { it.id != "local" }.forEachIndexed { i, p ->
                            if (i > 0) Divider(palette)
                            val has = keySet[p.id] == true
                            NavRow(Icons.Filled.Key, p.displayName, palette,
                                trailing = { StatusDot(has, palette) },
                                expanded = openKey == p.id) {
                                openKey = if (openKey == p.id) null else p.id
                            }
                            AnimatedVisibility(openKey == p.id, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                    CompactField(
                                        keyFields[p.id] ?: "", { keyFields[p.id] = it }, S.pasteKey(p.displayName),
                                        palette, action = S.save, enabled = !(keyFields[p.id].isNullOrBlank()),
                                        secret = true,
                                    ) {
                                        keyStore.setKey(p.id, keyFields[p.id] ?: "")
                                        keySet[p.id] = keyStore.hasKey(p.id)
                                        openKey = null
                                    }
                                    if (has) {
                                        Text(S.removeKey, color = palette.error, fontSize = 13.sp,
                                            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    keyStore.clearKey(p.id)
                                                    keyFields[p.id] = ""
                                                    keySet[p.id] = false
                                                }.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                    Footnote(S.keysFootnote, palette)
                }
            }

            // ---------------------------------------------------------------- generation
            Appear(180) {
                Column {
                    SectionLabel(S.secGeneration, palette)
                    Group(palette) {
                        // Local value while dragging; persisted once on release.
                        var temp by remember(s.temperature) { mutableFloatStateOf(s.temperature) }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RowIcon(Icons.Filled.Tune, palette)
                                Text(S.creativity, color = palette.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text("%.2f".format(temp), color = palette.primary, fontSize = 14.sp, fontFamily = AbyssFonts.Code)
                            }
                            Slider(
                                value = temp, onValueChange = { temp = it }, valueRange = 0f..2f,
                                onValueChangeFinished = { scope.launch { store.setTemperature(temp) } },
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.primary, activeTrackColor = palette.primary,
                                    inactiveTrackColor = palette.surfaceVariant),
                            )
                            Row {
                                Text(S.precise, color = palette.onSurface.copy(alpha = 0.45f), fontSize = 11.sp)
                                Spacer(Modifier.weight(1f))
                                Text(S.wild, color = palette.onSurface.copy(alpha = 0.45f), fontSize = 11.sp)
                            }
                        }
                        Divider(palette)
                        var sys by remember(s.systemPrompt) { mutableStateOf(s.systemPrompt) }
                        Column(Modifier.padding(16.dp)) {
                            Text(S.customInstructions, color = palette.onSurface, fontSize = 15.sp)
                            Text(S.customInstructionsSub, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                            Spacer(Modifier.size(10.dp))
                            MultilineField(sys, { sys = it }, S.customInstructionsHint, palette)
                            AnimatedVisibility(sys != s.systemPrompt) {
                                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                                    PillButton(S.save, palette) { scope.launch { store.setSystemPrompt(sys) } }
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- translator
            Appear(240) {
                Column {
                    SectionLabel(S.secTranslator, palette)
                    Group(palette) {
                        Box {
                            NavRow(Icons.Filled.Translate, S.readingLanguage, palette,
                                value = S.languageName(s.targetLang)) { langMenu = true }
                            DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                                Languages.list.forEach { (code, _) ->
                                    DropdownMenuItem(text = { Text(S.languageName(code)) }, onClick = {
                                        scope.launch { store.setTargetLang(code) }; langMenu = false
                                    })
                                }
                            }
                        }
                        Divider(palette)
                        SwitchRow(null, S.autoTranslate, S.autoTranslateSub, s.autoTranslate, palette) {
                            scope.launch { store.setAutoTranslate(it) }
                        }
                        Divider(palette)
                        Column(Modifier.padding(16.dp)) {
                            Text(S.engine, color = palette.onSurface, fontSize = 15.sp)
                            Spacer(Modifier.size(10.dp))
                            Segmented(
                                options = listOf("mlkit" to S.engineOnDevice, "llm" to S.engineMyModel),
                                selected = s.translateEngine, palette = palette,
                            ) { scope.launch { store.setTranslateEngine(it) } }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- privacy & power
            Appear(300) {
                Column {
                    SectionLabel(S.secPrivacy, palette)
                    Group(palette) {
                        NavRow(Icons.Filled.Lock, S.permissions, palette,
                            value = S.manage, onClick = onOpenPermissions)
                        Divider(palette)
                        SwitchRow(Icons.Filled.Shield, S.scamGuard, S.scamGuardSub,
                            s.guardEnabled, palette) { scope.launch { store.setGuardEnabled(it) } }
                        AnimatedVisibility(s.guardEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            var url by remember(s.guardLookupUrl) { mutableStateOf(s.guardLookupUrl) }
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                CompactField(url, { url = it }, S.lookupUrlHint, palette,
                                    action = S.save, enabled = url != s.guardLookupUrl) {
                                    scope.launch { store.setGuardUrl(url) }
                                }
                            }
                        }
                        Divider(palette)
                        SwitchRow(Icons.Filled.AutoAwesome, S.backgroundLearning, S.backgroundLearningSub,
                            s.backgroundHarvest, palette) { vm.toggleHarvest(it) }
                    }
                }
            }

            // ---------------------------------------------------------------- footer
            Column(Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Abyss", color = palette.onSurface.copy(alpha = 0.55f), fontFamily = AbyssFonts.Display, fontSize = 18.sp)
                if (version.isNotBlank())
                    Text("v$version", color = palette.onSurface.copy(alpha = 0.35f), fontSize = 12.sp, fontFamily = AbyssFonts.Code)
            }
        }

        if (showThemes) {
            PalettePicker(current = s.paletteId, onPick = { vm.setPalette(it); showThemes = false },
                onDismiss = { showThemes = false })
        }
    }
}

// ==================================================================== building blocks

private fun statusText(S: Strings, p: LlmProvider, hasKey: Boolean): String = when {
    p.id == "local" -> S.providerOnDevice
    hasKey -> S.keyConnected
    else -> S.noKeyYet
}

@Composable
private fun Group(palette: AbyssPalette, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().abyssObstacle(18.dp).clip(RoundedCornerShape(18.dp))
            .background(palette.surface.copy(alpha = if (palette.dark) 0.86f else 0.96f))
            .border(1.dp, palette.outline, RoundedCornerShape(18.dp)),
        content = content,
    )
}

@Composable
private fun Divider(palette: AbyssPalette) {
    Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(palette.outline.copy(alpha = 0.7f)))
}

@Composable
private fun RowIcon(icon: ImageVector, palette: AbyssPalette) {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = palette.primary, modifier = Modifier.size(16.dp))
    }
    Spacer(Modifier.width(12.dp))
}

@Composable
private fun NavRow(
    icon: ImageVector, title: String, palette: AbyssPalette,
    value: String? = null, expanded: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, palette)
        Text(title, color = palette.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = palette.onSurface.copy(alpha = 0.55f), fontSize = 14.sp)
        if (trailing != null) trailing()
        Spacer(Modifier.width(6.dp))
        val rot by animateFloatAsState(if (expanded) 90f else 0f, tween(220), label = "chev")
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = palette.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rot })
    }
}

@Composable
private fun RadioRow(palette: AbyssPalette, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) palette.primary else palette.outline, tween(200), label = "r")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(palette.primary))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.onSurface, fontSize = 15.sp)
            Text(subtitle, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Icon(Icons.Filled.Cloud, null, tint = palette.onSurface.copy(alpha = 0.18f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector?, title: String, subtitle: String?, checked: Boolean,
    palette: AbyssPalette, onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RowIcon(icon, palette) else Spacer(Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.onSurface, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onPrimary, checkedTrackColor = palette.primary,
                uncheckedThumbColor = palette.onSurface.copy(alpha = 0.7f), uncheckedTrackColor = palette.surfaceVariant,
                uncheckedBorderColor = palette.outline, checkedBorderColor = palette.primary,
            ),
        )
    }
}

@Composable
private fun InputRow(
    icon: ImageVector, palette: AbyssPalette, title: String, value: String, placeholder: String,
    onValueChange: (String) -> Unit, onCommit: () -> Unit, dirty: Boolean,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon, palette)
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.onSurface.copy(alpha = 0.55f), fontSize = 12.sp)
            Box {
                if (value.isEmpty()) Text(placeholder, color = palette.onSurface.copy(alpha = 0.35f), fontSize = 15.sp)
                BasicTextField(value, onValueChange, singleLine = true,
                    textStyle = TextStyle(color = palette.onSurface, fontSize = 15.sp),
                    cursorBrush = SolidColor(palette.primary), modifier = Modifier.fillMaxWidth())
            }
        }
        AnimatedVisibility(dirty) { PillButton(LocalStrings.current.save, palette, onClick = onCommit) }
    }
}

@Composable
private fun CompactField(
    value: String, onValueChange: (String) -> Unit, placeholder: String, palette: AbyssPalette,
    action: String, enabled: Boolean, secret: Boolean = false, onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().abyssObstacle(12.dp).clip(RoundedCornerShape(12.dp)).background(palette.surfaceVariant)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = palette.onSurface.copy(alpha = 0.38f), fontSize = 14.sp)
            BasicTextField(value, onValueChange, singleLine = true,
                textStyle = TextStyle(color = palette.onSurface, fontSize = 14.sp,
                    fontFamily = if (secret) AbyssFonts.Code else AbyssFonts.Ui),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                cursorBrush = SolidColor(palette.primary), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(8.dp))
        PillButton(action, palette, enabled = enabled, onClick = onAction)
    }
}

@Composable
private fun MultilineField(value: String, onValueChange: (String) -> Unit, placeholder: String, palette: AbyssPalette) {
    Box(Modifier.fillMaxWidth().heightIn(min = 84.dp).abyssObstacle(12.dp).clip(RoundedCornerShape(12.dp))
        .background(palette.surfaceVariant).padding(14.dp)) {
        if (value.isEmpty()) Text(placeholder, color = palette.onSurface.copy(alpha = 0.38f), fontSize = 14.sp)
        BasicTextField(value, onValueChange,
            textStyle = TextStyle(color = palette.onSurface, fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(palette.primary), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PillButton(text: String, palette: AbyssPalette, enabled: Boolean = true, onClick: () -> Unit) {
    val bg by animateColorAsState(if (enabled) palette.primary else palette.primary.copy(alpha = 0.3f), tween(200), label = "pb")
    Box(Modifier.abyssObstacle(10.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = palette.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, palette: AbyssPalette, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) palette.primary.copy(alpha = 0.16f) else Color.Transparent, tween(200), label = "cb")
    val br by animateColorAsState(if (selected) palette.primary else palette.outline, tween(200), label = "cr")
    Row(
        Modifier.abyssObstacle(10.dp).clip(RoundedCornerShape(10.dp)).background(bg).border(1.dp, br, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = palette.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = if (selected) palette.primary else palette.onSurface.copy(alpha = 0.8f),
            fontSize = 12.5.sp, fontFamily = AbyssFonts.Code)
    }
}

@Composable
private fun Segmented(
    options: List<Pair<String, String>>, selected: String, palette: AbyssPalette, onSelect: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth().abyssObstacle(12.dp).clip(RoundedCornerShape(12.dp)).background(palette.surfaceVariant).padding(3.dp)) {
        options.forEach { (id, label) ->
            val on = id == selected
            val bg by animateColorAsState(if (on) palette.surface else Color.Transparent, tween(220), label = "sg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                    .clickable { onSelect(id) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) palette.onSurface else palette.onSurface.copy(alpha = 0.55f),
                    fontSize = 13.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun StatusDot(on: Boolean, palette: AbyssPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (on) Color(0xFF5DBB7B) else palette.outline))
        Spacer(Modifier.width(6.dp))
        Text(if (on) LocalStrings.current.connected else LocalStrings.current.notSet, color = palette.onSurface.copy(alpha = 0.55f), fontSize = 13.sp)
    }
}

@Composable
private fun Footnote(text: String, palette: AbyssPalette) {
    Text(text, color = palette.onSurface.copy(alpha = 0.42f), fontSize = 12.sp, lineHeight = 16.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}
