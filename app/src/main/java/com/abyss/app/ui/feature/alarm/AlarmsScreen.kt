package com.abyss.app.ui.feature.alarm

import com.abyss.app.ui.background.abyssObstacle

import com.abyss.app.ui.i18n.LocalStrings

import com.abyss.app.ui.components.blockTaps
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.data.AlarmEntity
import com.abyss.app.feature.alarm.AlarmScheduler
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.launch


@Composable
fun AlarmsScreen(palette: AbyssPalette) {
    val S = LocalStrings.current
    val dao = remember { ServiceLocator.db.alarmDao() }
    val alarms by remember { dao.observeAll() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<AlarmEntity?>(null) }
    androidx.activity.compose.BackHandler(enabled = com.abyss.app.ui.components.LocalTabActive.current && (editing != null)) { editing = null }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.size(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmRow(alarm, palette,
                        onToggle = {
                            scope.launch {
                                val updated = alarm.copy(enabled = !alarm.enabled)
                                dao.update(updated)
                                if (updated.enabled) AlarmScheduler.schedule(context, updated)
                                else AlarmScheduler.cancel(context, updated.id)
                            }
                        },
                        onClick = { editing = alarm })
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(58.dp)
                .abyssObstacle(28.dp).clip(RoundedCornerShape(50)).background(palette.primary)
                .clickable { editing = AlarmEntity(hour = 7, minute = 0, days = "1,2,3,4,5") },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, S.newAlarm, tint = palette.onPrimary) }

        AnimatedVisibility(visible = editing != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }) {
            editing?.let { alarm ->
                AlarmEditor(alarm, palette,
                    onDismiss = { editing = null },
                    onSave = { a ->
                        scope.launch {
                            val id = if (a.id == 0L) dao.insert(a) else { dao.update(a); a.id }
                            val saved = a.copy(id = id)
                            if (saved.enabled) AlarmScheduler.schedule(context, saved)
                            else AlarmScheduler.cancel(context, saved.id)
                        }
                        editing = null
                    },
                    onDelete = {
                        scope.launch { if (alarm.id != 0L) { AlarmScheduler.cancel(context, alarm.id); dao.delete(alarm) } }
                        editing = null
                    })
            }
        }
    }
}

@Composable
private fun AlarmRow(alarm: AlarmEntity, palette: AbyssPalette, onToggle: () -> Unit, onClick: () -> Unit) {
    val S = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().abyssObstacle(18.dp).clip(RoundedCornerShape(18.dp))
            .background(palette.surface.copy(alpha = if (palette.dark) 0.86f else 0.96f))
            .border(1.dp, if (alarm.enabled) palette.primary.copy(alpha = 0.45f) else palette.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(String.format("%02d:%02d", alarm.hour, alarm.minute),
                color = if (alarm.enabled) palette.onSurface else palette.onSurface.copy(alpha = 0.4f),
                fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            val daysText = AlarmScheduler.parseDays(alarm.days).sorted()
                .joinToString(" ") { S.weekdaysShort[it - 1] }.ifBlank { S.once }
            Text(buildString {
                append(daysText)
                if (alarm.label.isNotBlank()) append(" · ${alarm.label}")
                if (alarm.smartVolume) append(" · ${S.smart}")
            }, color = palette.onSurface.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        Switch(checked = alarm.enabled, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun AlarmEditor(
    alarm: AlarmEntity, palette: AbyssPalette,
    onDismiss: () -> Unit, onSave: (AlarmEntity) -> Unit, onDelete: () -> Unit,
) {
    val S = LocalStrings.current
    var hour by remember { mutableStateOf(alarm.hour) }
    var minute by remember { mutableStateOf(alarm.minute) }
    var label by remember { mutableStateOf(alarm.label) }
    var days by remember { mutableStateOf(AlarmScheduler.parseDays(alarm.days).toMutableSet()) }
    var vibrate by remember { mutableStateOf(alarm.vibrate) }
    var smart by remember { mutableStateOf(alarm.smartVolume) }
    var skipSunday by remember { mutableStateOf(alarm.skipSunday) }
    var baseVol by remember { mutableStateOf(alarm.baseVolumePercent.toFloat()) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .abyssObstacle(22.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(palette.surface).padding(18.dp).blockTaps(),
        ) {
            // Time steppers
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Stepper(hour, palette, onUp = { hour = (hour + 1) % 24 }, onDown = { hour = (hour + 23) % 24 })
                Text(":", color = palette.onSurface, fontSize = 40.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 12.dp))
                Stepper(minute, palette, onUp = { minute = (minute + 1) % 60 }, onDown = { minute = (minute + 59) % 60 })
            }
            Spacer(Modifier.size(14.dp))

            // Day toggles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d ->
                    val on = d in days
                    Box(Modifier.weight(1f).abyssObstacle(20.dp).clip(CircleShape)
                        .background(if (on) palette.primary else palette.surfaceVariant)
                        .clickable { days = days.toMutableSet().apply { if (on) remove(d) else add(d) } }
                        .padding(vertical = 10.dp), Alignment.Center) {
                        // Full short label: one-letter initials collide in many languages.
                        Text(S.weekdaysShort[d - 1], maxLines = 1, softWrap = false,
                            color = if (on) palette.onPrimary else palette.onSurface, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            TextField(value = label, onValueChange = { label = it },
                placeholder = { Text(S.alarmLabel, color = palette.onSurface.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = palette.surfaceVariant,
                    unfocusedContainerColor = palette.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = palette.primary,
                    focusedTextColor = palette.onSurface, unfocusedTextColor = palette.onSurface))

            Spacer(Modifier.size(10.dp))
            ToggleRow(S.vibrate, vibrate, palette) { vibrate = it }
            ToggleRow(S.skipSundays, skipSunday, palette) { skipSunday = it }
            ToggleRow(S.smartVolume, smart, palette) { smart = it }

            Text(S.baseVolume(baseVol.toInt()), color = palette.onSurface.copy(alpha = 0.7f),
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Slider(value = baseVol, onValueChange = { baseVol = it }, valueRange = 10f..100f)

            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (alarm.id != 0L) Icon(Icons.Filled.Delete, S.delete, tint = palette.error,
                    modifier = Modifier.size(24.dp).clickable(onClick = onDelete))
                Spacer(Modifier.weight(1f))
                Box(Modifier.abyssObstacle(12.dp).clip(RoundedCornerShape(12.dp)).background(palette.primary)
                    .clickable {
                        onSave(alarm.copy(
                            hour = hour, minute = minute, label = label,
                            days = days.sorted().joinToString(","),
                            vibrate = vibrate, smartVolume = smart, skipSunday = skipSunday,
                            baseVolumePercent = baseVol.toInt(), enabled = true))
                    }.padding(horizontal = 22.dp, vertical = 12.dp)) {
                    Text(S.save, color = palette.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, palette: AbyssPalette, onUp: () -> Unit, onDown: () -> Unit) {
    val S = LocalStrings.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.KeyboardArrowUp, S.up, tint = palette.primary,
            modifier = Modifier.size(34.dp).clickable(onClick = onUp))
        Text(String.format("%02d", value), color = palette.onSurface, fontSize = 40.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Icon(Icons.Filled.KeyboardArrowDown, S.down, tint = palette.primary,
            modifier = Modifier.size(34.dp).clickable(onClick = onDown))
    }
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, palette: AbyssPalette, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = palette.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
