package com.abyss.app.ui.feature.tools

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.ui.feature.alarm.AlarmsScreen
import com.abyss.app.ui.feature.calc.CalculatorScreen
import com.abyss.app.ui.feature.core.CoreScreen
import com.abyss.app.ui.feature.notes.NotesScreen
import com.abyss.app.ui.feature.translate.TranslatorScreen
import com.abyss.app.ui.feature.vpn.VpnScreen
import com.abyss.app.ui.theme.AbyssPalette

private enum class Tool(val title: String, val icon: ImageVector) {
    Core("Abyss Core", Icons.Filled.AutoAwesome),
    Calculator("Calculator", Icons.Filled.Calculate),
    Notes("Notes", Icons.Filled.Note),
    Alarms("Alarms", Icons.Filled.Alarm),
    Translator("Translator", Icons.Filled.Translate),
    Vpn("VPN", Icons.Filled.VpnKey),
}

@Composable
fun ToolsHub(palette: AbyssPalette) {
    var open by remember { mutableStateOf<Tool?>(null) }

    AnimatedContent(
        targetState = open,
        transitionSpec = {
            if (targetState != null)
                (slideInHorizontally(tween(260)) { it } + fadeIn())
                    .togetherWith(slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut())
            else
                (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally(tween(260)) { it } + fadeOut())
        },
        label = "tool",
    ) { current ->
        if (current == null) {
            HubGrid(palette) { open = it }
        } else {
            Column(Modifier.fillMaxSize().background(palette.background)) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.onBackground,
                        modifier = Modifier.size(40.dp).clickable { open = null }.padding(8.dp))
                }
                Box(Modifier.fillMaxSize()) {
                    when (current) {
                        Tool.Core -> CoreScreen(palette)
                        Tool.Calculator -> CalculatorScreen(palette)
                        Tool.Notes -> NotesScreen(palette)
                        Tool.Alarms -> AlarmsScreen(palette)
                        Tool.Translator -> TranslatorScreen(palette)
                        Tool.Vpn -> VpnScreen(palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubGrid(palette: AbyssPalette, onOpen: (Tool) -> Unit) {
    Column(Modifier.fillMaxSize().background(palette.background).padding(16.dp)) {
        Text("Tools", color = palette.onBackground, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, fontSize = 22.sp)
        Spacer(Modifier.size(16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(Tool.entries.toTypedArray()) { tool ->
                Column(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                        .background(palette.surface).clickable { onOpen(tool) }.padding(18.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tool.icon, tool.title, tint = palette.primary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(tool.title, color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
