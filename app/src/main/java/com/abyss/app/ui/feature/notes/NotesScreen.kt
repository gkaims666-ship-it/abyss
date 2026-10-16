package com.abyss.app.ui.feature.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.data.NoteEntity
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

private fun noteTint(index: Int, palette: AbyssPalette): Color {
    val accents = listOf(palette.surfaceVariant, palette.primary, palette.secondary, palette.symbol)
    val base = accents[index % accents.size]
    return base.copy(alpha = 0.22f).compositeOverSurface(palette.surface)
}

private fun Color.compositeOverSurface(surface: Color): Color {
    val a = alpha
    return Color(
        red = red * a + surface.red * (1 - a),
        green = green * a + surface.green * (1 - a),
        blue = blue * a + surface.blue * (1 - a),
        alpha = 1f,
    )
}

@Composable
fun NotesScreen(palette: AbyssPalette) {
    val dao = remember { ServiceLocator.db.noteDao() }
    val notes by remember { dao.observeAll() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }

    val filtered = notes.filter {
        query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true)
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Notes", color = palette.onBackground, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, fontSize = 20.sp)
            Spacer(Modifier.size(10.dp))
            TextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search…", color = palette.onSurface.copy(alpha = 0.4f)) },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(palette), singleLine = true,
            )
            Spacer(Modifier.size(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { note ->
                    NoteCard(note, palette,
                        onClick = { editing = note },
                        onPin = { scope.launch { dao.upsert(note.copy(pinned = !note.pinned)) } })
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(58.dp)
                .clip(RoundedCornerShape(50)).background(palette.primary)
                .clickable { editing = NoteEntity() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, "New note", tint = palette.onPrimary) }

        AnimatedVisibility(
            visible = editing != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            editing?.let { note ->
                NoteEditor(
                    note = note, palette = palette,
                    onDismiss = { editing = null },
                    onSave = { updated ->
                        scope.launch { dao.upsert(updated.copy(updatedAt = System.currentTimeMillis())) }
                        editing = null
                    },
                    onDelete = {
                        scope.launch { if (note.id != 0L) dao.delete(note) }
                        editing = null
                    },
                )
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity, palette: AbyssPalette, onClick: () -> Unit, onPin: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp)).background(noteTint(note.colorIndex, palette))
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(note.title.ifBlank { "Untitled" }, color = palette.onSurface,
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Filled.PushPin, "Pin",
                tint = if (note.pinned) palette.primary else palette.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp).clickable(onClick = onPin))
        }
        Spacer(Modifier.size(6.dp))
        Text(note.body, color = palette.onSurface.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 6)
    }
}

@Composable
private fun NoteEditor(
    note: NoteEntity, palette: AbyssPalette,
    onDismiss: () -> Unit, onSave: (NoteEntity) -> Unit, onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }
    var color by remember { mutableStateOf(note.colorIndex) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(palette.surface).padding(18.dp)
                .clickable(enabled = false) {},
        ) {
            TextField(value = title, onValueChange = { title = it },
                placeholder = { Text("Title", color = palette.onSurface.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(palette), singleLine = true)
            Spacer(Modifier.size(8.dp))
            TextField(value = body, onValueChange = { body = it },
                placeholder = { Text("Note…", color = palette.onSurface.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), colors = fieldColors(palette))
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                (0..3).forEach { i ->
                    Box(Modifier.size(28.dp).padding(end = 8.dp).clip(RoundedCornerShape(8.dp))
                        .background(noteTint(i, palette)).clickable { color = i },
                        contentAlignment = Alignment.Center) {
                        if (color == i) Text("●", color = palette.primary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (note.id != 0L) Icon(Icons.Filled.Delete, "Delete", tint = palette.error,
                    modifier = Modifier.size(24.dp).clickable(onClick = onDelete))
                Spacer(Modifier.size(14.dp))
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(palette.primary)
                    .clickable { onSave(note.copy(title = title, body = body, colorIndex = color)) }
                    .padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Save", color = palette.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun fieldColors(palette: AbyssPalette) = TextFieldDefaults.colors(
    focusedContainerColor = palette.surfaceVariant,
    unfocusedContainerColor = palette.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = palette.primary,
    focusedTextColor = palette.onSurface,
    unfocusedTextColor = palette.onSurface,
)
