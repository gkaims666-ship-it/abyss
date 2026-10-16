package com.abyss.app.ui.feature.perms

import com.abyss.app.ui.i18n.LocalStrings

import android.app.AlarmManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.abyss.app.core.insights.UsageAnalyzer
import com.abyss.app.ui.theme.AbyssPalette

private data class RuntimePerm(val permission: String, val title: String, val why: String)

private fun runtimePerms(S: com.abyss.app.ui.i18n.Strings) = listOf(
    RuntimePerm(android.Manifest.permission.POST_NOTIFICATIONS, S.permNotifications, S.permNotificationsWhy),
    RuntimePerm(android.Manifest.permission.RECORD_AUDIO, S.permMic, S.permMicWhy),
    RuntimePerm(android.Manifest.permission.CAMERA, S.permCamera, S.permCameraWhy),
    RuntimePerm(android.Manifest.permission.READ_CONTACTS, S.permContacts, S.permContactsWhy),
    RuntimePerm(android.Manifest.permission.READ_PHONE_STATE, S.permPhone, S.permPhoneWhy),
)

@Composable
fun PermissionsScreen(palette: AbyssPalette) {
    val S = LocalStrings.current
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val multi = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val single = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refresh++ }
    val special = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { refresh++ }

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

    // read refresh so statuses recompute after returning
    refresh.let { }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(S.permsIntro,
            color = palette.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
        Spacer(Modifier.size(12.dp))

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.primary)
            .clickable { multi.launch(runtimePerms(S).map { it.permission }.toTypedArray()) }
            .padding(vertical = 14.dp), Alignment.Center) {
            Text(S.requestAll, color = palette.onPrimary,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.size(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(runtimePerms(S)) { p ->
                PermRow(p.title, p.why, granted(p.permission), palette) {
                    single.launch(p.permission)
                }
            }

            // Special-access entries.
            item {
                val ok = UsageAnalyzer(context).hasUsageAccess()
                PermRow(S.permUsage, S.permUsageWhy, ok, palette) {
                    special.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            item {
                val ok = roleHeld(context, RoleManager.ROLE_CALL_SCREENING)
                PermRow(S.permScreening, S.permScreeningWhy, ok, palette) {
                    requestCallScreening(context, special::launch)
                }
            }
            item {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
                PermRow(S.permExactAlarms, S.permExactAlarmsWhy, ok, palette) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        special.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + context.packageName)))
                }
            }
            item {
                PermRow(S.permBattery, S.permBatteryWhy, false, palette) {
                    special.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
    }
}

@Composable
private fun PermRow(title: String, why: String, granted: Boolean, palette: AbyssPalette, onRequest: () -> Unit) {
    val S = LocalStrings.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.surface)
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.onSurface, fontWeight = FontWeight.SemiBold)
            Text(why, color = palette.onSurface.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        if (granted) {
            Icon(Icons.Filled.CheckCircle, S.granted, tint = palette.primary)
        } else {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(palette.surfaceVariant)
                .clickable(onClick = onRequest).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(S.grant, color = palette.primary, fontSize = 13.sp)
            }
        }
    }
}

private fun roleHeld(context: Context, role: String): Boolean {
    val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return false
    return runCatching { rm.isRoleAvailable(role) && rm.isRoleHeld(role) }.getOrDefault(false)
}

private fun requestCallScreening(context: Context, launch: (Intent) -> Unit) {
    val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return
    if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
        launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }
}
