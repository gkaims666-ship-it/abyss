package com.abyss.app.ui

import com.abyss.app.ui.background.abyssObstacle

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.abyss.app.ui.components.AbyssIntro
import androidx.compose.runtime.CompositionLocalProvider
import com.abyss.app.ui.i18n.I18n
import com.abyss.app.ui.i18n.LocalStrings
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.abyss.app.ui.background.AbyssAnimatedBackground
import com.abyss.app.ui.background.AbyssWaveOverlay
import com.abyss.app.ui.background.LocalAbyssField
import com.abyss.app.ui.background.abyssTouchWaves
import com.abyss.app.ui.background.rememberAbyssBackgroundState
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abyss.app.ui.chat.ChatScreen
import com.abyss.app.ui.chat.ChatViewModel
import com.abyss.app.ui.feature.tools.ToolsHub
import com.abyss.app.ui.settings.SettingsHost
import com.abyss.app.ui.theme.AbyssPalette
import com.abyss.app.ui.theme.AbyssTheme
import com.abyss.app.ui.theme.Palettes

enum class Tab { Chat, Tools, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AbyssRoot() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbyssRoot() {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    val palette = Palettes.byId(state.settings.paletteId)
    var tab by remember { mutableStateOf(Tab.Chat) }
    val imeVisible = WindowInsets.isImeVisible
    val bg = rememberAbyssBackgroundState()
    // UI language: "system" follows the phone's locale; otherwise the user's pick.
    val strings = remember(state.settings.language) { I18n.resolve(state.settings.language) }
    SideEffect { I18n.current = strings }

    // System back: from Tools/Settings go to Chat instead of leaving the app.
    // (Nested screens register their own handlers later, so they win first.)
    BackHandler(enabled = tab != Tab.Chat) { tab = Tab.Chat }

    // Status/nav bar icons must contrast with the theme (dark icons on Ivory).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !palette.dark
                isAppearanceLightNavigationBars = !palette.dark
            }
        }
    }

    // Launch intro (cold start only — survives rotation). When the black hole
    // collapses, a wave rolls out of the centre and the UI appears through it.
    var showIntro by rememberSaveable { mutableStateOf(true) }

    // A wave also washes over the screen whenever the theme changes.
    var firstPalette by remember { mutableStateOf(true) }
    LaunchedEffect(palette.id) {
        if (firstPalette) firstPalette = false
        else bg.emitCenter(strength = 1f, reach = 1.5f, sparkCount = 18)
    }

    // Glyphs fully alive on the empty start page; softened under content.
    val dim by animateFloatAsState(
        if (tab == Tab.Chat && state.messages.isEmpty()) 0f else 0.45f, tween(600), label = "dim")

    CompositionLocalProvider(LocalStrings provides strings, LocalAbyssField provides bg) {
    AbyssTheme(palette = palette) {
        Box(Modifier.fillMaxSize().abyssTouchWaves(bg)) {
            // One living background + waves for the whole app, every tab.
            AbyssAnimatedBackground(palette = palette, state = bg, dim = dim)

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    AnimatedVisibility(
                        visible = !imeVisible,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                    ) {
                        PillNav(palette = palette, current = tab, onSelect = { tab = it })
                    }
                },
            ) { inner ->
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(300)) { it / 4 * dir } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(220)) { -it / 4 * dir } + fadeOut(tween(180)))
                    },
                    modifier = Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding()),
                    label = "tab",
                ) { current ->
                    when (current) {
                        Tab.Chat -> ChatScreen(vm = vm, palette = palette, onOpenSettings = { tab = Tab.Settings })
                        Tab.Tools -> Box(Modifier.fillMaxSize().statusBarsPadding()) { ToolsHub(palette = palette) }
                        Tab.Settings -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            SettingsHost(vm = vm, palette = palette)
                        }
                    }
                }
            }

            // Rings above everything (touch-transparent) — visible over any content.
            AbyssWaveOverlay(palette = palette, state = bg)

            if (showIntro) {
                AbyssIntro(
                    palette = palette,
                    onCollapse = {
                        if (bg.viewW > 0f) bg.emit(
                            androidx.compose.ui.geometry.Offset(bg.viewW / 2f, bg.viewH / 2f),
                            strength = 1.25f, reach = 1.45f, sparkCount = 30)
                    },
                    onDone = { showIntro = false },
                )
            }
        }
    }
    }
}

/** Floating capsule navigation: the active tab expands into an accent pill. */
@Composable
private fun PillNav(palette: AbyssPalette, current: Tab, onSelect: (Tab) -> Unit) {
    val s = LocalStrings.current
    val items = listOf(
        Triple(Tab.Chat, Icons.AutoMirrored.Filled.Chat, s.navChat),
        Triple(Tab.Tools, Icons.Filled.Apps, s.navTools),
        Triple(Tab.Settings, Icons.Filled.Settings, s.navSettings),
    )
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(top = 6.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.abyssObstacle(28.dp).clip(RoundedCornerShape(28.dp))
                .background(palette.surface)
                .border(1.dp, palette.outline, RoundedCornerShape(28.dp))
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (t, icon, label) ->
                PillItem(palette, icon, label, selected = t == current) { onSelect(t) }
            }
        }
    }
}

@Composable
private fun PillItem(
    palette: AbyssPalette, icon: ImageVector, label: String,
    selected: Boolean, onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) palette.primary else Color.Transparent, tween(250), label = "bg")
    val fg by animateColorAsState(
        if (selected) palette.onPrimary else palette.onSurface.copy(alpha = 0.6f), tween(250), label = "fg")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier.clip(RoundedCornerShape(23.dp)).background(bg)
            .clickable {
                if (!selected) haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .animateContentSize(spring(dampingRatio = 0.75f, stiffness = 500f))
            .padding(horizontal = if (selected) 18.dp else 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(20.dp))
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
