package com.abyss.app.ui.feature.tools

import com.abyss.app.ui.background.abyssObstacle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.ui.components.Appear
import com.abyss.app.ui.components.BackBar
import com.abyss.app.ui.components.ScreenTitle
import com.abyss.app.ui.feature.alarm.AlarmsScreen
import com.abyss.app.ui.feature.calc.CalculatorScreen
import com.abyss.app.ui.feature.core.CoreScreen
import com.abyss.app.ui.feature.notes.NotesScreen
import com.abyss.app.ui.feature.translate.TranslatorScreen
import com.abyss.app.ui.feature.vpn.VpnScreen
import com.abyss.app.ui.theme.AbyssPalette
import com.abyss.app.ui.i18n.LocalStrings
import com.abyss.app.ui.i18n.Strings

private enum class Tool(val icon: ImageVector) {
    Core(Icons.Filled.AutoAwesome),
    Translator(Icons.Filled.Translate),
    Notes(Icons.Filled.Note),
    Alarms(Icons.Filled.Alarm),
    Calculator(Icons.Filled.Calculate),
    Vpn(Icons.Filled.VpnKey);

    fun title(S: Strings) = when (this) {
        Core -> S.toolCore; Translator -> S.toolTranslator; Notes -> S.toolNotes
        Alarms -> S.toolAlarms; Calculator -> S.toolCalculator; Vpn -> S.toolVpn
    }
    fun blurb(S: Strings) = when (this) {
        Core -> S.toolCoreBlurb; Translator -> S.toolTranslatorBlurb; Notes -> S.toolNotesBlurb
        Alarms -> S.toolAlarmsBlurb; Calculator -> S.toolCalculatorBlurb; Vpn -> S.toolVpnBlurb
    }
}

@Composable
fun ToolsHub(palette: AbyssPalette) {
    var open by remember { mutableStateOf<Tool?>(null) }
    BackHandler(enabled = open != null) { open = null }

    AnimatedContent(
        targetState = open,
        transitionSpec = {
            if (targetState != null)
                (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)))
                    .togetherWith(fadeOut(tween(160)))
            else
                (fadeIn(tween(280)))
                    .togetherWith(slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
        },
        label = "tool",
    ) { current ->
        if (current == null) {
            HubGrid(palette) { open = it }
        } else {
            Column(Modifier.fillMaxSize()) {
                BackBar(current.title(LocalStrings.current), palette) { open = null }
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
    val S = LocalStrings.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle(S.toolsTitle, palette, subtitle = S.toolsSubtitle)
        }
        itemsIndexed(Tool.entries) { i, tool ->
            Appear(delayMs = 60 * i) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 132.dp)
                        .abyssObstacle(20.dp).clip(RoundedCornerShape(20.dp))
                        .background(palette.surface.copy(alpha = if (palette.dark) 0.82f else 0.95f))
                        .border(1.dp, palette.outline, RoundedCornerShape(20.dp))
                        .clickable { onOpen(tool) }
                        .padding(16.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(palette.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(tool.icon, null, tint = palette.primary, modifier = Modifier.size(21.dp)) }
                    Spacer(Modifier.size(14.dp))
                    Text(tool.title(S), color = palette.onSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.size(3.dp))
                    Text(tool.blurb(S), color = palette.onSurface.copy(alpha = 0.55f), fontSize = 12.5.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
