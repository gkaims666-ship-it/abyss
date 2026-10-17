package com.abyss.app.ui.chat

import com.abyss.app.ui.components.fadeLayer

import com.abyss.app.ui.background.abyssObstacle

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import com.abyss.app.ui.i18n.LocalStrings
import com.abyss.app.ui.i18n.Strings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.agent.AgentRegistry
import com.abyss.app.core.provider.Role
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.abyss.app.ui.components.AbyssMark
import com.abyss.app.ui.components.Appear
import com.abyss.app.ui.feature.camera.CameraCaptureScreen
import com.abyss.app.ui.theme.AbyssFonts
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    palette: AbyssPalette,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showPalettes by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    val empty = state.messages.isEmpty()
    val strings = LocalStrings.current

    // System back closes overlays first instead of leaving the app.
    BackHandler(enabled = com.abyss.app.ui.components.LocalTabActive.current && (drawer.isOpen)) { scope.launch { drawer.close() } }
    BackHandler(enabled = com.abyss.app.ui.components.LocalTabActive.current && (showPalettes)) { showPalettes = false }
    BackHandler(enabled = com.abyss.app.ui.components.LocalTabActive.current && (showCamera)) { showCamera = false }

    ModalNavigationDrawer(
        drawerState = drawer,
        // Open only from the menu button (no left→right swipe-to-open); swipe
        // still closes it while it is open.
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ConversationDrawer(
                palette = palette,
                conversations = state.conversations,
                current = state.conversationId,
                onNew = { vm.newConversation(); scope.launch { drawer.close() } },
                onPick = { vm.switchConversation(it); scope.launch { drawer.close() } },
                onDelete = { vm.deleteConversation(it) },
                onThemes = { showPalettes = true; scope.launch { drawer.close() } },
                onSettings = { onOpenSettings(); scope.launch { drawer.close() } },
            )
        },
    ) {
        // Background + waves are drawn app-wide in MainActivity.
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                TopBar(
                    palette = palette,
                    agentId = AgentRegistry.byId(state.settings.agentId).id,
                    routed = state.routedAgent,
                    showNewChat = !empty,
                    onMenu = { scope.launch { drawer.open() } },
                    onSelectAgent = vm::setAgent,
                    onNewChat = vm::newConversation,
                    onThemes = { showPalettes = true },
                )

                AnimatedContent(
                    targetState = empty,
                    transitionSpec = {
                        slideInVertically(tween(380)) { it / 12 }
                            .togetherWith(slideOutVertically(tween(220)) { 0 })
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "chat-mode",
                ) { isEmpty ->
                    Box(Modifier.fillMaxSize().then(fadeLayer(380, 220))) {
                        if (isEmpty) {
                            Hero(state = state, palette = palette, vm = vm,
                                onCamera = { showCamera = true }, onModel = onOpenSettings)
                        } else {
                            Conversation(state = state, palette = palette, vm = vm,
                                onCamera = { showCamera = true }, onModel = onOpenSettings)
                        }
                    }
                }
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
                        vm.analyzeCapturedMedia(path, mime, kind, strings.describeMediaPrompt)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- top bar

@Composable
private fun TopBar(
    palette: AbyssPalette,
    agentId: String,
    routed: String?,
    showNewChat: Boolean,
    onMenu: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onNewChat: () -> Unit,
    onThemes: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val S = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Filled.Menu, S.chats, palette, onMenu)
        Spacer(Modifier.weight(1f))
        Box {
            Row(
                Modifier.abyssObstacle(20.dp).clip(RoundedCornerShape(50)).clickable { menu = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.agentName(agentId), color = palette.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                AnimatedVisibility(routed != null && agentId == "auto") {
                    Text("  · ${routed?.let { S.agentName(it) } ?: ""}", color = palette.primary, fontSize = 14.sp)
                }
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = palette.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                modifier = Modifier.background(palette.surface)) {
                AgentRegistry.selectable.forEach { a ->
                    DropdownMenuItem(
                        text = {
                            Column(Modifier.padding(vertical = 2.dp)) {
                                Text(S.agentName(a.id), color = palette.onSurface, fontWeight = FontWeight.Medium)
                                Text(S.agentTagline(a.id), color = palette.onSurface.copy(alpha = 0.55f), fontSize = 12.sp)
                            }
                        },
                        onClick = { onSelectAgent(a.id); menu = false },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (showNewChat) IconBtn(Icons.Filled.Edit, S.newChat, palette, onNewChat)
        else IconBtn(Icons.Filled.Palette, S.themes, palette, onThemes)
    }
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, palette: AbyssPalette, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).abyssObstacle(22.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = palette.onBackground.copy(alpha = 0.85f), modifier = Modifier.size(22.dp)) }
}

// ---------------------------------------------------------------- hero (start page)

private data class Suggestion(val label: String, val icon: ImageVector, val prompt: String)

private fun suggestions(S: Strings) = listOf(
    Suggestion(S.sugWrite, Icons.Filled.Edit, S.sugWritePrompt),
    Suggestion(S.sugLearn, Icons.Filled.School, S.sugLearnPrompt),
    Suggestion(S.sugCode, Icons.Filled.Code, S.sugCodePrompt),
    Suggestion(S.sugLife, Icons.Filled.LocalCafe, S.sugLifePrompt),
    Suggestion(S.sugSurprise, Icons.Filled.AutoAwesome, S.sugSurprisePrompt),
)

private fun greeting(S: Strings, name: String): String {
    val bucket = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> 0
        in 12..17 -> 1
        in 18..22 -> 2
        else -> 3
    }
    return S.greeting(bucket, name)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(
    state: ChatUiState,
    palette: AbyssPalette,
    vm: ChatViewModel,
    onCamera: () -> Unit,
    onModel: () -> Unit,
) {
    val S = LocalStrings.current
    val text = remember(state.settings.userName, S) { greeting(S, state.settings.userName) }
    val sugs = remember(S) { suggestions(S) }
    val focus = remember { FocusRequester() }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        for (i in 1..text.length) { shown = i; delay(32) }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val field = com.abyss.app.ui.background.LocalAbyssField.current
        Row(Modifier.abyssObstacle(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Tap the black hole to enter / leave power-saving mode (with the
            // collapse / expand animation). It also reports its position so the
            // animation is anchored here.
            AbyssMark(palette, size = 36.dp, modifier = Modifier
                .onGloballyPositioned { field?.holeCenter = it.boundsInRoot().center }
                .clip(CircleShape)
                .clickable { field?.requestHoleToggle() })
            Spacer(Modifier.width(12.dp))
            Text(
                text.take(shown),
                color = palette.onBackground,
                fontFamily = AbyssFonts.Display,
                fontSize = 34.sp,
                lineHeight = 40.sp,
            )
        }
        Spacer(Modifier.size(30.dp))
        Composer(state = state, palette = palette, vm = vm, hero = true,
            onCamera = onCamera, onModel = onModel, focusRequester = focus)
        Spacer(Modifier.size(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            sugs.forEachIndexed { i, s ->
                Appear(delayMs = 260 + i * 60) {
                    Row(
                        Modifier.abyssObstacle(12.dp).clip(RoundedCornerShape(12.dp))
                            .border(1.dp, palette.outline, RoundedCornerShape(12.dp))
                            .background(palette.surface.copy(alpha = 0.55f))
                            .clickable {
                                vm.onInputChange(s.prompt)
                                runCatching { focus.requestFocus() }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(s.icon, null, tint = palette.onSurface.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(s.label, color = palette.onSurface.copy(alpha = 0.85f), fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.size(56.dp))
    }
}

// ---------------------------------------------------------------- conversation

@Composable
private fun Conversation(
    state: ChatUiState,
    palette: AbyssPalette,
    vm: ChatViewModel,
    onCamera: () -> Unit,
    onModel: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Ids that already played their entrance — scrolling back must not replay it.
    val seen = remember { mutableSetOf<Long>() }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    LaunchedEffect(state.messages.lastOrNull()?.content?.length) {
        val last = state.messages.lastIndex
        val visible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (last >= 0 && visible >= last - 1) listState.scrollToItem(last, 100_000)
    }

    val scope = rememberCoroutineScope()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || (last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 40)
        }
    }

    Column(Modifier.fillMaxSize()) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = listState,
            // Messages dissolve into the top bar / composer instead of hard-cutting.
            modifier = Modifier.fillMaxSize().edgeFade(top = 28.dp, bottom = 22.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                val firstTime = remember(msg.id) { seen.add(msg.id) }
                Box(Modifier.animateItem()) {
                    Appear(enabled = firstTime) {
                        if (msg.role == Role.USER) UserMessage(msg, palette, onDelete = { vm.deleteMessage(msg.id) })
                        else AssistantMessage(
                            msg, palette,
                            onCopy = {},
                            onRegenerate = { vm.regenerate() },
                            onDelete = { vm.deleteMessage(msg.id) },
                            onTranslate = { vm.translateMessage(msg.id) },
                            onToggleOriginal = { vm.toggleOriginal(msg.id) },
                        )
                    }
                }
            }
        }
        // Jump-to-latest, only when scrolled away from the bottom.
        JumpToLatest(
            visible = !atBottom, palette = palette,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        ) {
            scope.launch {
                if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex, 100_000)
            }
        }
      }
        state.mediaStatus?.let {
            Text(it, color = palette.primary, fontSize = 12.sp,
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp))
        }
        Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
            Composer(state = state, palette = palette, vm = vm, hero = false,
                onCamera = onCamera, onModel = onModel)
        }
    }
}

/**
 * Lives at top level on purpose: calling AnimatedVisibility inside a Box that
 * sits in a Column picks ColumnScope.AnimatedVisibility and fails to compile.
 */
@Composable
private fun JumpToLatest(visible: Boolean, palette: AbyssPalette, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + androidx.compose.animation.scaleIn(tween(220), initialScale = 0.7f),
        exit = fadeOut(tween(160)) + androidx.compose.animation.scaleOut(tween(160), targetScale = 0.7f),
        modifier = modifier,
    ) {
        Box(
            Modifier.size(38.dp).abyssObstacle(19.dp).clip(CircleShape)
                .background(palette.surface)
                .border(1.dp, palette.outline, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, LocalStrings.current.latest, tint = palette.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessage(msg: UiMessage, palette: AbyssPalette, onDelete: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier.widthIn(max = 310.dp)
                .abyssObstacle(20.dp).clip(RoundedCornerShape(20.dp))
                .background(palette.userBubble)
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(msg.content, color = palette.onSurface, fontSize = 16.sp, lineHeight = 23.sp)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(LocalStrings.current.copy) }, onClick = {
                    clipboard.setText(AnnotatedString(msg.content)); menu = false })
                DropdownMenuItem(text = { Text(LocalStrings.current.delete) }, onClick = { onDelete(); menu = false })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantMessage(
    msg: UiMessage,
    palette: AbyssPalette,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onTranslate: () -> Unit,
    onToggleOriginal: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val showingTranslation = msg.showTranslated && msg.translatedText != null
    val body = if (showingTranslation) msg.translatedText.orEmpty() else msg.content
    var copied by remember { mutableStateOf(false) }
    val S = LocalStrings.current
    LaunchedEffect(copied) { if (copied) { delay(1400); copied = false } }

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.abyssObstacle(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Only the live reply animates its mark — dozens of spinning marks
            // in a long chat would just burn frames on a 120 Hz screen.
            AbyssMark(palette, size = 20.dp, pulse = msg.streaming, speedMs = 1600, animated = msg.streaming)
            if (msg.agentLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(S.agentName(msg.agentLabel), color = palette.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.size(8.dp))

        Box(Modifier.fillMaxWidth().abyssObstacle(10.dp).combinedClickable(onClick = {}, onLongClick = { menu = true })) {
            when {
                msg.error -> Text(body, color = palette.error, fontSize = 15.sp)
                msg.streaming && body.isBlank() -> Thinking(palette)
                else -> MarkdownText(body, palette)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(S.copy) }, onClick = {
                    clipboard.setText(AnnotatedString(body)); menu = false })
                DropdownMenuItem(text = { Text(S.regenerate) }, onClick = { onRegenerate(); menu = false })
                DropdownMenuItem(text = { Text(S.delete) }, onClick = { onDelete(); menu = false })
            }
        }

        if (!msg.streaming && !msg.error) {
            Spacer(Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallAction(
                    if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, palette,
                    tint = if (copied) palette.primary else null,
                ) { clipboard.setText(AnnotatedString(body)); copied = true; onCopy() }
                SmallAction(Icons.Filled.Refresh, palette, onClick = onRegenerate)
                Spacer(Modifier.width(6.dp))
                when {
                    msg.translating -> Hint(S.translating, palette)
                    showingTranslation -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Hint(S.translatedFrom(langName(S, msg.sourceLang)), palette)
                        Text(S.showOriginal, color = palette.primary, fontSize = 12.sp,
                            modifier = Modifier.clickable(onClick = onToggleOriginal))
                    }
                    msg.translatedText != null -> Text(S.showTranslation, color = palette.primary,
                        fontSize = 12.sp, modifier = Modifier.clickable(onClick = onToggleOriginal))
                    msg.offerTranslate -> Text(S.translate, color = palette.primary, fontSize = 12.sp,
                        modifier = Modifier.clickable(onClick = onTranslate))
                }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, palette: AbyssPalette, tint: Color? = null, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).abyssObstacle(16.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        androidx.compose.animation.Crossfade(targetState = icon, label = "act") { ic ->
            Icon(ic, null, tint = tint ?: palette.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Hint(text: String, palette: AbyssPalette) {
    Text(text, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
}

private fun langName(S: Strings, tag: String?): String =
    tag?.let { S.languageNameInline(it) } ?: S.anotherLanguage

@Composable
private fun Thinking(palette: AbyssPalette) {
    val tr = rememberInfiniteTransition(label = "think")
    val a by tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(LocalStrings.current.thinking, color = palette.onSurface.copy(alpha = a), fontFamily = AbyssFonts.Reading,
            fontSize = 16.sp)
        Spacer(Modifier.width(4.dp))
        repeat(3) { i ->
            val d by tr.animateFloat(0f, 1f,
                infiniteRepeatable(tween(900, delayMillis = i * 150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "d$i")
            Box(Modifier.padding(horizontal = 2.dp).size(5.dp)
                .graphicsLayer { translationY = -6f * d }
                .clip(CircleShape).background(palette.primary.copy(alpha = 0.5f + 0.5f * d)))
        }
    }
}

// ---------------------------------------------------------------- composer

@Composable
private fun Composer(
    state: ChatUiState,
    palette: AbyssPalette,
    vm: ChatViewModel,
    hero: Boolean,
    onCamera: () -> Unit,
    onModel: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    // Local field value so programmatic text (suggestions, transcripts) puts the
    // cursor at the END instead of the start.
    var field by remember { mutableStateOf(TextFieldValue(state.input, TextRange(state.input.length))) }
    LaunchedEffect(state.input) {
        if (state.input != field.text) field = TextFieldValue(state.input, TextRange(state.input.length))
    }
    val border by animateColorAsState(
        if (focused) palette.primary.copy(alpha = 0.45f) else palette.outline, tween(250), label = "border")
    // A light glint that travels around the border while the field is focused.
    // Read only in the draw phase, so it never recomposes the composer.
    val glintAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(300), label = "ga")
    // The infinite glint clock only exists while it's visible — no idle frame cost.
    val glint = if (focused || glintAlpha > 0.01f) {
        rememberInfiniteTransition(label = "glint")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(2600)), label = "g")
    } else null

    Column(
        Modifier.fillMaxWidth()
            .abyssObstacle(24.dp).clip(RoundedCornerShape(24.dp))
            .background(palette.surface.copy(alpha = if (palette.dark) 0.94f else 1f))
            .border(1.dp, border, RoundedCornerShape(24.dp))
            .drawWithContent {
                drawContent()
                if (glint != null && glintAlpha > 0.01f) {
                    val w = size.width
                    val x = -w * 0.5f + glint.value * w * 2f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to palette.secondary.copy(alpha = 0.9f * glintAlpha),
                            1f to Color.Transparent,
                            start = Offset(x - w * 0.35f, 0f),
                            end = Offset(x + w * 0.35f, size.height),
                        ),
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
            .animateContentSize(),
    ) {
        if (state.recording) {
            RecordingRow(palette, onCancel = vm::cancelRecording, onSend = vm::stopRecordingAndSend)
            return@Column
        }
        if (state.transcribing) {
            Text(LocalStrings.current.transcribing, color = palette.primary, fontSize = 12.sp,
                modifier = Modifier.padding(start = 18.dp, top = 12.dp))
        }
        Box(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp)) {
            if (state.input.isEmpty()) {
                Text(
                    if (hero) LocalStrings.current.composerHint else LocalStrings.current.replyHint,
                    color = palette.onSurface.copy(alpha = 0.42f), fontSize = 16.sp,
                )
            }
            BasicTextField(
                value = field,
                onValueChange = { field = it; vm.onInputChange(it.text) },
                textStyle = TextStyle(color = palette.onSurface, fontSize = 16.sp, lineHeight = 23.sp),
                cursorBrush = SolidColor(palette.primary),
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().heightIn(min = if (hero) 48.dp else 24.dp)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerIcon(Icons.Filled.Add, palette, onCamera)
            ComposerIcon(Icons.Filled.Mic, palette, vm::startRecording)
            Spacer(Modifier.weight(1f))
            Text(
                prettyModel(state.settings.model),
                color = palette.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp).abyssObstacle(8.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onModel).padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(6.dp))
            val canSend = state.input.isNotBlank() || state.isBusy
            val sendBg by animateColorAsState(
                if (canSend) palette.primary else palette.primary.copy(alpha = 0.35f), tween(200), label = "send")
            val haptic = LocalHapticFeedback.current
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val sendScale by animateFloatAsState(
                if (pressed) 0.86f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "ss")
            Box(
                Modifier.size(36.dp).graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                    .abyssObstacle(10.dp).clip(RoundedCornerShape(10.dp)).background(sendBg)
                    .clickable(interactionSource = press, indication = null, enabled = canSend) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (state.isBusy) vm.stop() else vm.send()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (state.isBusy) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                    if (state.isBusy) LocalStrings.current.stop else LocalStrings.current.send, tint = palette.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun prettyModel(m: String): String =
    m.substringAfterLast('/').replace('-', ' ').split(' ')
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

@Composable
private fun ComposerIcon(icon: ImageVector, palette: AbyssPalette, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).abyssObstacle(10.dp).clip(RoundedCornerShape(10.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = palette.onSurface.copy(alpha = 0.75f), modifier = Modifier.size(18.dp)) }
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun RecordingRow(palette: AbyssPalette, onCancel: () -> Unit, onSend: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "rec")
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Live "waveform" bars.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(palette.error))
            Spacer(Modifier.width(10.dp))
            repeat(18) { i ->
                val h by tr.animateFloat(0.2f, 1f,
                    infiniteRepeatable(tween(420 + (i * 53) % 300), RepeatMode.Reverse), label = "b$i")
                Box(Modifier.padding(horizontal = 1.5.dp).width(3.dp)
                    .height((5f + 17f * h).dp).clip(RoundedCornerShape(2.dp))
                    .background(palette.primary.copy(alpha = 0.8f)))
            }
        }
        Text(LocalStrings.current.cancel, color = palette.onSurface.copy(alpha = 0.7f), fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCancel).padding(8.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(36.dp).abyssObstacle(10.dp).clip(RoundedCornerShape(10.dp)).background(palette.primary)
            .clickable(onClick = onSend), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ArrowUpward, LocalStrings.current.send, tint = palette.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------- drawer

@Composable
private fun ConversationDrawer(
    palette: AbyssPalette,
    conversations: List<com.abyss.app.core.data.ConversationSummary>,
    current: String,
    onNew: () -> Unit,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onThemes: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = palette.background, drawerContentColor = palette.onBackground) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                AbyssMark(palette, size = 26.dp, animated = false)
                Spacer(Modifier.width(10.dp))
                Text("Abyss", color = palette.onBackground, fontFamily = AbyssFonts.Display, fontSize = 24.sp)
            }
            Spacer(Modifier.size(18.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onNew)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(palette.primary), Alignment.Center) {
                    Icon(Icons.Filled.Add, null, tint = palette.onPrimary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(LocalStrings.current.newChat, color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.size(14.dp))
            Text(LocalStrings.current.recents, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 12.sp,
                modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
            if (conversations.isEmpty()) {
                Text(LocalStrings.current.chatsEmpty, color = palette.onSurface.copy(alpha = 0.4f), fontSize = 13.sp,
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(conversations, key = { it.conversationId }) { c ->
                    DrawerChatRow(c, selected = c.conversationId == current, palette = palette,
                        onPick = { onPick(c.conversationId) }, onDelete = { onDelete(c.conversationId) })
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(palette.outline))
            DrawerItem(Icons.Filled.Palette, LocalStrings.current.themes, palette, onThemes)
            DrawerItem(Icons.Filled.Settings, LocalStrings.current.settings, palette, onSettings)
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, palette: AbyssPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = palette.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = palette.onSurface, fontSize = 14.sp)
    }
}

private fun conversationLabel(S: Strings, c: com.abyss.app.core.data.ConversationSummary): String {
    val t = c.title?.trim()?.replace('\n', ' ')
    if (!t.isNullOrEmpty()) return t
    val ms = c.conversationId.removePrefix("conv_").toLongOrNull() ?: c.lastAt
    return S.chatFallback + " · " + SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(S.locale, "dMMMHHmm"), S.locale).format(Date(ms))
}

private fun relativeTime(S: Strings, ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    val min = diff / 60_000
    return when {
        min < 1 -> S.timeNow
        min < 60 -> S.minutesShort(min)
        min < 60 * 24 -> S.hoursShort(min / 60)
        min < 60 * 24 * 7 -> S.daysShort(min / (60 * 24))
        else -> SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(S.locale, "dMMM"), S.locale).format(Date(ms))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerChatRow(
    c: com.abyss.app.core.data.ConversationSummary, selected: Boolean, palette: AbyssPalette,
    onPick: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val S = LocalStrings.current
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(if (selected) palette.surfaceVariant else Color.Transparent)
                .combinedClickable(onClick = onPick, onLongClick = { menu = true })
                .padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ChatBubbleOutline, null,
                tint = if (selected) palette.primary else palette.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(conversationLabel(S, c), color = palette.onSurface, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(relativeTime(S, c.lastAt), color = palette.onSurface.copy(alpha = 0.38f), fontSize = 11.sp)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(S.deleteChat, color = palette.error) },
                onClick = { menu = false; onDelete() })
        }
    }
}

/**
 * Fades the content to transparent over [top] at the top edge and [bottom] at
 * the bottom edge, so scrolling text dissolves rather than being hard-clipped.
 */
private fun Modifier.edgeFade(top: Dp, bottom: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val t = top.toPx()
        val b = bottom.toPx()
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = t),
            size = Size(size.width, t),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent,
                startY = size.height - b, endY = size.height),
            topLeft = Offset(0f, size.height - b),
            size = Size(size.width, b),
            blendMode = BlendMode.DstIn,
        )
    }
