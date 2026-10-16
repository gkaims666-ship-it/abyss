package com.abyss.app.feature.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.ui.theme.Palettes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen alarm UI with sound, vibration, snooze and dismiss. */
class AlarmRingActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var alarmId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(AlarmReceiver.EXTRA_LABEL).orEmpty()
        val vibrate = intent.getBooleanExtra(AlarmReceiver.EXTRA_VIBRATE, true)

        startRinging(vibrate)

        setContent {
            val palette = Palettes.default
            val transition = rememberInfiniteTransition(label = "pulse")
            val pulse by transition.animateFloat(
                initialValue = 0.9f, targetValue = 1.1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")

            Box(Modifier.fillMaxSize().background(palette.background), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◍", fontSize = 72.sp, color = palette.primary,
                        modifier = Modifier.scale(pulse))
                    Spacer(Modifier.size(24.dp))
                    Text(now(), color = palette.onBackground, fontSize = 64.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    if (label.isNotBlank())
                        Text(label, color = palette.onSurface.copy(alpha = 0.7f), fontSize = 18.sp)
                    Spacer(Modifier.size(48.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActionButton(com.abyss.app.ui.i18n.I18n.current.snooze, palette.surfaceVariant, palette.onSurface,
                            Modifier.weight(1f)) { snooze() }
                        ActionButton(com.abyss.app.ui.i18n.I18n.current.dismiss, palette.primary, palette.onPrimary,
                            Modifier.weight(1f)) { dismiss() }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionButton(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
        Box(modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
            .padding(vertical = 18.dp), Alignment.Center) {
            Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    private fun now(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private fun startRinging(vibrate: Boolean) {
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply { play() }
        }
        if (vibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 600)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stop() {
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(alarmId.toInt())
    }

    private fun dismiss() { stop(); finish() }

    private fun snooze() {
        if (alarmId >= 0) AlarmScheduler.snooze(this, alarmId)
        stop(); finish()
    }

    override fun onDestroy() { stop(); super.onDestroy() }
}
