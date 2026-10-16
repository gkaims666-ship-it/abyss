package com.abyss.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.agent.AgentRegistry
import com.abyss.app.core.provider.Role
import com.abyss.app.ui.background.AbyssAnimatedBackground
import com.abyss.app.ui.feature.camera.CameraCaptureScreen
import com.abyss.app.ui.theme.AbyssPalette
import com.abyss.app.ui.theme.Palettes

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    palette: AbyssPalette,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    var showPalettes by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Box(Modifier.fillMaxSize()) {
        AbyssAnimatedBackground(palette = palette)

        Column(Modifier.fillMaxSize()) {
            TopBar(
                palette = palette,
                agentName = AgentRegistry.byId(state.settings.agentId).name,
                routed = state.routedAgent,
                conversations = state.conversations,
                currentConversation = state.conversationId,
                onSelectAgent = vm::setAgent,
                onTogglePalettes = { showPalettes = !showPalettes },
                onOpenSettings = onOpenSettings,
                onNewChat = vm::newConversation,
                onSwitch = vm::switchConversation,
            )

            if (state.messages.isEmpty()) {
                EmptyState(palette, Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(msg, palette,
                            onTranslate = { vm.translateMessage(msg.id) },
                            onToggleOriginal = { vm.toggleOriginal(msg.id) },
                            onDelete = { vm.deleteMessage(msg.id) },
                            onRegenerate = { vm.regenerate() })
                    }
                }
            }

            state.mediaStatus?.let {
                Text(it, color = palette.primary, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))
            }
            InputBar(
                value = state.input,
                busy = state.isBusy,
                recording = state.recording,
                transcribing = state.transcribing,
                palette = palette,
                onChange = vm::onInputChange,
                onSend = vm::send,
                onStop = vm::stop,
                onStartRec = vm::startRecording,
                onStopRec = vm::stopRecordingAndSend,
                onCancelRec = vm::cancelRecording,
                onCamera = { showCamera = true },
            )
        }

        if (showPalettes) {
            PalettePicker(
                current = palette.id,
                onPick = { vm.setPalette(it); showPalettes = false },
                onDismiss = { showPalettes = false },
            )
        }

        if (showCamera) {
            CameraCaptureScreen(
                palette = palette,
                onClose = { showCamera = false },
                onCaptured = { path, mime, kind ->
                    showCamera = false
                    vm.analyzeCapturedMedia(path, mime, kind,
                        "Describe in detail what is happening and what is said or heard.")
                },
            )
        }
    }
}

@Composable
private fun TopBar(
    palette: AbyssPalette,
    agentName: String,
    routed: String?,
    conversations: List<String>,
    currentConversation: String,
    onSelectAgent: (String) -> Unit,
    onTogglePalettes: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit,
    onSwitch: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var convMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Abyss",
            color = palette.onBackground,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(12.dp))

        // Agent selector
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.surfaceVariant.copy(alpha = 0.7f))
                    .clickable { menu = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(agentName, color = palette.primary, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace)
                if (routed != null && agentName == "Auto") {
                    Text("  → $routed", color = palette.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Icon(Icons.Filled.ArrowDropDown, null, tint = palette.onSurface)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                AgentRegistry.selectable.forEach { a ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(a.name, fontWeight = FontWeight.SemiBold)
                                Text(a.tagline, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        },
                        onClick = { onSelectAgent(a.id); menu = false },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        IconButton(onClick = onNewChat) {
            Icon(Icons.Filled.Add, "New chat", tint = palette.onBackground)
        }
        Box {
            IconButton(onClick = { convMenu = true }) {
                Icon(Icons.Filled.History, "Conversations", tint = palette.onBackground)
            }
            DropdownMenu(expanded = convMenu, onDismissRequest = { convMenu = false }) {
                if (conversations.isEmpty()) DropdownMenuItem(text = { Text("No saved chats") }, onClick = {})
                conversations.forEach { id ->
                    val label = if (id == "default") "Main" else "Chat " + id.takeLast(4)
                    DropdownMenuItem(
                        text = { Text(label + if (id == currentConversation) "  •" else "") },
                        onClick = { onSwitch(id); convMenu = false })
                }
            }
        }
        IconButton(onClick = onTogglePalettes) {
            Icon(Icons.Filled.Palette, "Themes", tint = palette.onBackground)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, "Settings", tint = palette.onBackground)
        }
    }
}

@Composable
private fun EmptyState(palette: AbyssPalette, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("◍", fontSize = 64.sp, color = palette.primary.copy(alpha = 0.8f))
            Spacer(Modifier.size(12.dp))
            Text(
                "Ask anything. Pick an agent, or let Auto route it.",
                color = palette.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: UiMessage,
    palette: AbyssPalette,
    onTranslate: () -> Unit,
    onToggleOriginal: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val isUser = msg.role == Role.USER
    val showingTranslation = msg.showTranslated && msg.translatedText != null
    val bodyText = if (showingTranslation) msg.translatedText!! else msg.content
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().animateContentSize(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (!isUser && msg.agentLabel != null) {
            Text(msg.agentLabel, color = palette.primary.copy(alpha = 0.75f),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
        }
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp))
                .background(
                    when {
                        msg.error -> palette.error.copy(alpha = 0.18f)
                        isUser -> palette.userBubble
                        else -> palette.assistantBubble.copy(alpha = 0.92f)
                    }
                )
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = bodyText + if (msg.streaming) " ▍" else "",
                color = if (msg.error) palette.error else palette.onSurface,
                fontSize = 15.sp,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Copy") }, onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(bodyText)); menu = false })
                if (!isUser) DropdownMenuItem(text = { Text("Regenerate") },
                    onClick = { onRegenerate(); menu = false })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(); menu = false })
            }
        }

        // Inline translation affordance (X-style).
        if (!isUser && !msg.error && !msg.streaming) {
            when {
                msg.translating -> TranslateHint("Translating…", palette)
                showingTranslation -> Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)) {
                    Text("Translated from ${langName(msg.sourceLang)} · ",
                        color = palette.onSurface.copy(alpha = 0.5f), fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace)
                    Text("Show original", color = palette.primary, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable(onClick = onToggleOriginal))
                }
                msg.translatedText != null -> TranslateLink("Show translation", palette) { onToggleOriginal() }
                msg.offerTranslate -> TranslateLink("Translate", palette, onTranslate)
            }
        }
    }
}

@Composable
private fun TranslateLink(text: String, palette: AbyssPalette, onClick: () -> Unit) {
    Text(text, color = palette.primary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp).clickable(onClick = onClick))
}

@Composable
private fun TranslateHint(text: String, palette: AbyssPalette) {
    Text(text, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 11.sp,
        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
}

private fun langName(tag: String?): String =
    tag?.let { com.abyss.app.core.translate.Languages.name(it) } ?: "another language"

@Composable
private fun InputBar(
    value: String,
    busy: Boolean,
    recording: Boolean,
    transcribing: Boolean,
    palette: AbyssPalette,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onStartRec: () -> Unit,
    onStopRec: () -> Unit,
    onCancelRec: () -> Unit,
    onCamera: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().imePadding().padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (recording) {
            RecordingBar(palette, onCancel = onCancelRec, onStop = onStopRec)
            return@Column
        }
        if (transcribing) {
            Text("Transcribing…", color = palette.primary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCamera) {
                Icon(Icons.Filled.PhotoCamera, "Camera", tint = palette.onBackground.copy(alpha = 0.7f))
            }
            IconButton(onClick = onStartRec) {
                Icon(Icons.Filled.Mic, "Voice", tint = palette.onBackground.copy(alpha = 0.7f))
            }
            TextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Abyss…", color = palette.onSurface.copy(alpha = 0.4f)) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = palette.surface.copy(alpha = 0.85f),
                    unfocusedContainerColor = palette.surface.copy(alpha = 0.85f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = palette.primary,
                    focusedTextColor = palette.onSurface,
                    unfocusedTextColor = palette.onSurface,
                ),
                maxLines = 6,
            )
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(palette.primary)
                    .clickable { if (busy) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (busy) Icons.Filled.Stop else Icons.Filled.Send,
                    contentDescription = if (busy) "Stop" else "Send", tint = palette.onPrimary)
            }
        }
    }
}

@Composable
private fun RecordingBar(palette: AbyssPalette, onCancel: () -> Unit, onStop: () -> Unit) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "rec")
    val a by transition.animateFloat(
        0.3f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(600),
            androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(palette.surface.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(50))
            .background(palette.error.copy(alpha = a)))
        Spacer(Modifier.size(10.dp))
        Text("Recording…", color = palette.onSurface, modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace)
        Text("Cancel", color = palette.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.clickable(onClick = onCancel).padding(8.dp))
        Spacer(Modifier.size(6.dp))
        Box(Modifier.clip(RoundedCornerShape(50)).background(palette.primary)
            .clickable(onClick = onStop).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Icon(Icons.Filled.Send, "Send", tint = palette.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}
