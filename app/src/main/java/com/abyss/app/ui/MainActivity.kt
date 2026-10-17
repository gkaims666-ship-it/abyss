package com.abyss.app.ui

import com.abyss.app.ui.components.fadeLayer

import com.abyss.app.ui.background.abyssObstacle

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import com.abyss.app.ui.components.AbyssMark
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import com.abyss.app.ui.components.LocalTabActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.abyss.app.ui.components.AbyssIntro
import androidx.compose.runtime.CompositionLocalProvider
import com.abyss.app.ui.i18n.I18n
import com.abyss.app.ui.i18n.LocalStrings
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.abyss.app.ui.background.AbyssAnimatedBackground
import com.abyss.app.ui.background.AbyssBackgroundState
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
    // Only what the root needs: re-running the whole app shell on every streamed
    // token (≈30×/s) was a steady source of dropped frames.
    val settings by remember(vm) { vm.state.map { it.settings }.distinctUntilChanged() }
        .collectAsState(vm.state.value.settings)
    val chatEmpty by remember(vm) { vm.state.map { it.messages.isEmpty() }.distinctUntilChanged() }
        .collectAsState(vm.state.value.messages.isEmpty())
    val palette = Palettes.byId(settings.paletteId)
    var tab by remember { mutableStateOf(Tab.Chat) }
    val imeVisible = WindowInsets.isImeVisible
    val bg = rememberAbyssBackgroundState()
    // UI language: "system" follows the phone's locale; otherwise the user's pick.
    val strings = remember(settings.language) { I18n.resolve(settings.language) }
    SideEffect { I18n.current = strings }

    // System back: from Tools/Settings go to Chat instead of leaving the app.
    // (Nested screens register their own handlers later, so they win first.)
    BackHandler(enabled = com.abyss.app.ui.components.LocalTabActive.current && (tab != Tab.Chat)) { tab = Tab.Chat }

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
        if (tab == Tab.Chat && chatEmpty) 0f else 0.45f, tween(600), label = "dim")

    // Tab position as a float (0 = Chat, 1 = Tools, 2 = Settings). A critically
    // damped spring: interrupt it mid-way and it simply re-aims, no jump.
    val pos = remember { Animatable(0f) }
    LaunchedEffect(tab) {
        bg.transitioning = true
        try { pos.animateTo(tab.ordinal.toFloat(), spring(dampingRatio = 1f, stiffness = 380f)) }
        finally { bg.transitioning = false }
    }
    // Tabs are composed once and kept alive. Chat now; the others shortly after
    // launch, while nothing is moving, so the first switch is already instant.
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1600); warm = true }
    var visited by remember { mutableStateOf(setOf(Tab.Chat)) }
    LaunchedEffect(tab) { visited = visited + tab }

    // Power-saving mode: store → background state (the Settings toggle takes this
    // instant path). The black-hole tap sets it mid-animation and persists it.
    LaunchedEffect(settings.powerSave) { bg.powerSave = settings.powerSave }

    // The black-hole collapse / expand animation. Tapping the mark runs it: the
    // hole swells ~4×, the living field is sucked in (or spat back out), then the
    // hole returns to rest. At the midpoint the power-saving mode flips.
    val holeAnim = remember { Animatable(0f) }
    LaunchedEffect(bg.holeToggleReq) {
        if (bg.holeToggleReq == 0) return@LaunchedEffect
        val toSave = !bg.powerSave
        bg.holeExpand = !toSave
        bg.holeActive = true
        holeAnim.snapTo(0f)
        holeAnim.animateTo(0.5f, tween(460, easing = FastOutSlowInEasing)) { bg.holePhase = value }
        bg.powerSave = toSave
        vm.setPowerSave(toSave)
        // Peak: a luminescent shockwave rolls out of the hole through the real
        // wave engine — the swallow pulse on collapse, the burst on restore.
        if (bg.holeCenter != Offset.Unspecified)
            bg.emit(bg.holeCenter, strength = 1.5f, reach = 1.9f, sparkCount = 40)
        holeAnim.animateTo(1f, tween(540, easing = FastOutSlowInEasing)) { bg.holePhase = value }
        bg.holePhase = 0f
        bg.holeActive = false
    }

    CompositionLocalProvider(LocalStrings provides strings, LocalAbyssField provides bg) {
    AbyssTheme(palette = palette) {
        Box(Modifier.fillMaxSize().then(if (bg.powerSave && !bg.holeActive) Modifier else Modifier.abyssTouchWaves(bg))) {
            // One living background + waves for the whole app, every tab. In
            // power-saving mode it is not composed at all (no sim, no glyphs),
            // except while the collapse/expand animation is playing.
            if (!bg.powerSave || bg.holeActive) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    if (bg.holeActive) {
                        val p = bg.holePhase
                        val k = if (bg.holeExpand) ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
                                else (1f - (p / 0.5f).coerceIn(0f, 1f))
                        alpha = k
                        val sc = 0.14f + 0.86f * k
                        scaleX = sc; scaleY = sc
                        rotationZ = (1f - k) * (if (bg.holeExpand) 26f else -26f)
                        val c = bg.holeCenter
                        if (c != Offset.Unspecified && size.width > 0f && size.height > 0f) {
                            transformOrigin = TransformOrigin(
                                (c.x / size.width).coerceIn(0f, 1f), (c.y / size.height).coerceIn(0f, 1f))
                        }
                    }
                }) {
                    AbyssAnimatedBackground(palette = palette, state = bg, dim = dim)
                }
            }

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
                Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
                    for (t in Tab.entries) {
                        key(t) {
                            if (warm || t in visited) {
                                TabPage(t, current = tab, pos = pos) {
                                    when (t) {
                                        Tab.Chat -> ChatScreen(vm = vm, palette = palette, onOpenSettings = { tab = Tab.Settings })
                                        Tab.Tools -> Box(Modifier.fillMaxSize().statusBarsPadding()) { ToolsHub(palette = palette) }
                                        Tab.Settings -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                                            SettingsHost(vm = vm, palette = palette)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Rings/crests above content (touch-transparent). Gated and sucked
            // in with the same transform as the background during the animation.
            if (!bg.powerSave || bg.holeActive) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    if (bg.holeActive) {
                        val p = bg.holePhase
                        val k = if (bg.holeExpand) ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
                                else (1f - (p / 0.5f).coerceIn(0f, 1f))
                        alpha = k
                        val sc = 0.14f + 0.86f * k
                        scaleX = sc; scaleY = sc
                        val c = bg.holeCenter
                        if (c != Offset.Unspecified && size.width > 0f && size.height > 0f) {
                            transformOrigin = TransformOrigin(
                                (c.x / size.width).coerceIn(0f, 1f), (c.y / size.height).coerceIn(0f, 1f))
                        }
                    }
                }) {
                    AbyssWaveOverlay(palette = palette, state = bg)
                }
            }

            // The black hole itself: swells over everything while the field is
            // sucked in / spat out, then returns to rest.
            BlackHoleOverlay(state = bg, palette = palette)

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

/**
 * The black hole that drives power-saving. While the animation plays it swells
 * (~4×) over a soft scrim and spins, anchored at the chat mark's position, then
 * shrinks back to rest. The living field is sucked in / spat out behind it.
 */
@Composable
private fun BlackHoleOverlay(state: AbyssBackgroundState, palette: AbyssPalette) {
    if (!state.holeActive) return
    val p = state.holePhase
    val swell = sin(p * PI).toFloat()        // 0 → 1 → 0 across the play
    val hs = 1f + 3f * swell                  // rest 1× → peak 4× → rest 1×
    val markSize = 128.dp
    val halfPx = with(LocalDensity.current) { markSize.toPx() } / 2f
    val c = state.holeCenter
    Box(Modifier.fillMaxSize().background(palette.background.copy(alpha = 0.5f * swell))) {
        val place = if (c != Offset.Unspecified)
            Modifier.offset { IntOffset((c.x - halfPx).roundToInt(), (c.y - halfPx).roundToInt()) }
        else Modifier.align(Alignment.Center)
        AbyssMark(
            palette, size = markSize, speedMs = 900, pulse = true, detailed = true,
            modifier = place.graphicsLayer {
                scaleX = hs; scaleY = hs
                rotationZ = (if (state.holeExpand) -1f else 1f) * 120f * p
            },
        )
    }
}

/**
 * One kept-alive tab. The switch animation touches only this layer's
 * translation and alpha, read inside graphicsLayer — so it runs on the render
 * thread every frame without recomposing, re-measuring or re-recording the
 * screen. Hidden tabs are skipped when drawing, swallow touches, and tell
 * their content (back handlers, water obstacles) that they are inactive.
 */
@Composable
private fun TabPage(t: Tab, current: Tab, pos: Animatable<Float, *>, content: @Composable () -> Unit) {
    val active = t == current
    val idx = t.ordinal.toFloat()
    val onScreen by remember { derivedStateOf { abs(pos.value - idx) < 0.995f } }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(if (active) 1f else 0f)
            .graphicsLayer {
                val d = pos.value - idx
                alpha = (1f - abs(d) * 1.4f).coerceIn(0f, 1f)
                translationX = -d * size.width * 0.18f
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawWithContent { if (onScreen) drawContent() }
            .then(if (active) Modifier else Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }),
    ) {
        CompositionLocalProvider(LocalTabActive provides active) { content() }
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
