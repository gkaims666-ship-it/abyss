package com.abyss.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.ui.theme.AbyssPalette
import com.abyss.app.ui.theme.Palettes

/** A tap-anywhere-to-dismiss sheet of palette swatches. */
@Composable
fun PalettePicker(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val sheet = Palettes.byId(current)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(sheet.surface)
                .navigationBarsPadding()
                .padding(20.dp)
                // Consume clicks so tapping the sheet doesn't dismiss.
                .clickable(enabled = false) {},
        ) {
            Text("Themes", color = sheet.onSurface, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, fontSize = 16.sp)
            Spacer(Modifier.size(14.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Palettes.all, key = { it.id }) { p ->
                    PaletteCard(p, selected = p.id == current, onClick = { onPick(p.id) })
                }
            }
        }
    }
}

@Composable
private fun PaletteCard(p: AbyssPalette, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) p.primary else p.outline,
                shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(p.primary); Spacer(Modifier.size(6.dp))
        Swatch(p.secondary); Spacer(Modifier.size(6.dp))
        Swatch(p.symbol); Spacer(Modifier.size(10.dp))
        Text(p.displayName, color = p.onBackground, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(16.dp).clip(CircleShape).background(color))
}
