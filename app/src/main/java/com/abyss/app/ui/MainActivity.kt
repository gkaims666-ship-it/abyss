package com.abyss.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abyss.app.ui.chat.ChatScreen
import com.abyss.app.ui.chat.ChatViewModel
import com.abyss.app.ui.feature.tools.ToolsHub
import com.abyss.app.ui.settings.SettingsHost
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

@Composable
private fun AbyssRoot() {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    val palette = Palettes.byId(state.settings.paletteId)
    var tab by remember { mutableStateOf(Tab.Chat) }

    AbyssTheme(palette = palette) {
        Scaffold(
            containerColor = palette.background,
            bottomBar = {
                NavigationBar(containerColor = palette.surface.copy(alpha = 0.96f)) {
                    val items = listOf(
                        Triple(Tab.Chat, Icons.AutoMirrored.Filled.Chat, "Chat"),
                        Triple(Tab.Tools, Icons.Filled.Apps, "Tools"),
                        Triple(Tab.Settings, Icons.Filled.Settings, "Settings"),
                    )
                    items.forEach { (t, icon, label) ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(icon, label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.onPrimary,
                                selectedTextColor = palette.primary,
                                indicatorColor = palette.primary,
                                unselectedIconColor = palette.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = palette.onSurface.copy(alpha = 0.6f),
                            ),
                        )
                    }
                }
            },
        ) { inner ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(260)) { it * dir } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(260)) { -it * dir } + fadeOut(tween(260)))
                },
                modifier = Modifier.fillMaxSize().padding(inner),
                label = "tab",
            ) { current ->
                Box(Modifier.fillMaxSize()) {
                    when (current) {
                        Tab.Chat -> ChatScreen(vm = vm, palette = palette,
                            onOpenSettings = { tab = Tab.Settings })
                        Tab.Tools -> ToolsHub(palette = palette)
                        Tab.Settings -> SettingsHost(vm = vm, palette = palette)
                    }
                }
            }
        }
    }
}
