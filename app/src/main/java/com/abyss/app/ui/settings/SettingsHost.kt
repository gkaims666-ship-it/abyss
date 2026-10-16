package com.abyss.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abyss.app.ui.chat.ChatViewModel
import com.abyss.app.ui.feature.perms.PermissionsScreen
import com.abyss.app.ui.theme.AbyssPalette

private enum class SettingsRoute { Main, Permissions }

@Composable
fun SettingsHost(vm: ChatViewModel, palette: AbyssPalette) {
    var route by remember { mutableStateOf(SettingsRoute.Main) }
    androidx.activity.compose.BackHandler(enabled = route != SettingsRoute.Main) { route = SettingsRoute.Main }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            if (targetState == SettingsRoute.Permissions)
                (slideInHorizontally(tween(240)) { it } + fadeIn())
                    .togetherWith(slideOutHorizontally(tween(240)) { -it / 3 } + fadeOut())
            else
                (slideInHorizontally(tween(240)) { -it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally(tween(240)) { it } + fadeOut())
        },
        label = "settings",
    ) { current ->
        when (current) {
            SettingsRoute.Main -> SettingsScreen(vm, palette,
                onOpenPermissions = { route = SettingsRoute.Permissions })
            SettingsRoute.Permissions -> Column(Modifier.fillMaxSize()) {
                com.abyss.app.ui.components.BackBar("Permissions", palette) { route = SettingsRoute.Main }
                PermissionsScreen(palette)
            }
        }
    }
}
