package com.abyss.app.ui.feature.vpn

import android.net.VpnService
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.abyss.app.feature.vpn.SubscriptionParser
import com.abyss.app.feature.vpn.VpnBridge
import com.abyss.app.feature.vpn.VpnController
import com.abyss.app.feature.vpn.VpnServer
import com.abyss.app.feature.vpn.VpnStatus
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.launch

@Composable
fun VpnScreen(palette: AbyssPalette) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = ServiceLocator.vpnStore
    val servers by store.servers.collectAsState(initial = emptyList())
    val selectedId by store.selectedId.collectAsState(initial = null)
    val savedSub by store.subscriptionUrl.collectAsState(initial = "")
    val status by VpnController.status.collectAsState()
    val scroll = rememberScrollState()

    var sub by remember(savedSub) { mutableStateOf(savedSub) }
    var pasteLinks by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<VpnServer?>(null) }
    var busyMsg by remember { mutableStateOf<String?>(null) }

    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pending?.let { VpnController.connect(context, it) }
        }
        pending = null
    }

    fun connect(server: VpnServer) {
        scope.launch { store.setSelected(server.id) }
        val prepare = VpnService.prepare(context)
        if (prepare != null) { pending = server; vpnConsent.launch(prepare) }
        else VpnController.connect(context, server)
    }

    Column(Modifier.fillMaxSize()
        .verticalScroll(scroll).padding(16.dp)) {
        Text(if (VpnBridge.available) "Xray-core engine ready"
            else "Engine not in this APK — build with -PwithVpn to enable",
            color = if (VpnBridge.available) palette.primary else palette.onSurface.copy(alpha = 0.6f),
            fontSize = 12.sp)

        Spacer(Modifier.size(14.dp))
        StatusCard(status, palette) { VpnController.disconnect(context) }

        Spacer(Modifier.size(16.dp))
        Text("Subscription URL", color = palette.primary, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(value = sub, onValueChange = { sub = it }, modifier = Modifier.weight(1f),
                singleLine = true, placeholder = { Text("https://…/sub",
                    color = palette.onSurface.copy(alpha = 0.4f)) },
                shape = RoundedCornerShape(12.dp), colors = fieldColors(palette))
            Spacer(Modifier.size(8.dp))
            Btn("Import", palette) {
                scope.launch {
                    busyMsg = "Fetching…"
                    store.setSubscriptionUrl(sub)
                    val parsed = SubscriptionParser.fetchSubscription(sub)
                    if (parsed.isNotEmpty()) store.saveServers(merge(servers, parsed))
                    busyMsg = "Imported ${parsed.size} servers"
                }
            }
        }

        Spacer(Modifier.size(12.dp))
        Text("Or paste links (vmess/vless/trojan/ss)", color = palette.onSurface.copy(alpha = 0.7f),
            fontSize = 12.sp)
        TextField(value = pasteLinks, onValueChange = { pasteLinks = it },
            modifier = Modifier.fillMaxWidth(), placeholder = { Text("one per line",
                color = palette.onSurface.copy(alpha = 0.4f)) },
            shape = RoundedCornerShape(12.dp), colors = fieldColors(palette))
        Spacer(Modifier.size(8.dp))
        Btn("Add pasted", palette) {
            val parsed = SubscriptionParser.parseMany(pasteLinks)
            if (parsed.isNotEmpty()) scope.launch { store.saveServers(merge(servers, parsed)); pasteLinks = "" }
            busyMsg = "Added ${parsed.size}"
        }
        busyMsg?.let { Text(it, color = palette.primary, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp)) }

        Spacer(Modifier.size(16.dp))
        Text("Servers (${servers.size})", color = palette.primary, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Spacer(Modifier.size(8.dp))
        servers.forEach { s ->
            ServerRow(s, selected = s.id == selectedId,
                connected = (status as? VpnStatus.Connected)?.label == s.label(),
                palette = palette,
                onConnect = { connect(s) },
                onDelete = { scope.launch { store.saveServers(servers.filterNot { it.id == s.id }) } })
            Spacer(Modifier.size(8.dp))
        }
        Spacer(Modifier.size(40.dp))
    }
}

private fun merge(old: List<VpnServer>, new: List<VpnServer>): List<VpnServer> {
    val map = LinkedHashMap<String, VpnServer>()
    (old + new).forEach { map[it.id] = it }
    return map.values.toList()
}

@Composable
private fun StatusCard(status: VpnStatus, palette: AbyssPalette, onDisconnect: () -> Unit) {
    val (text, color) = when (status) {
        is VpnStatus.Connected -> "Connected · ${status.label}" to palette.primary
        VpnStatus.Connecting -> "Connecting…" to palette.secondary
        is VpnStatus.Error -> "Error: ${status.message}" to palette.error
        VpnStatus.Disconnected -> "Disconnected" to palette.onSurface.copy(alpha = 0.6f)
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(10.dp))
        Text(text, color = palette.onSurface, modifier = Modifier.weight(1f), fontSize = 14.sp)
        if (status is VpnStatus.Connected || status is VpnStatus.Connecting) {
            Btn("Disconnect", palette, onDisconnect)
        }
    }
}

@Composable
private fun ServerRow(
    s: VpnServer, selected: Boolean, connected: Boolean, palette: AbyssPalette,
    onConnect: () -> Unit, onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (connected) palette.surfaceVariant else palette.surface)
        .clickable(onClick = onConnect).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.label(), color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("${s.protocol.name.lowercase()} · ${s.host}:${s.port}",
                color = palette.onSurface.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        if (connected) Text("●", color = palette.primary, fontSize = 16.sp)
        Spacer(Modifier.size(10.dp))
        Text("✕", color = palette.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.clickable(onClick = onDelete).padding(6.dp))
    }
}

@Composable
private fun Btn(text: String, palette: AbyssPalette, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(palette.primary)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text, color = palette.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun fieldColors(palette: AbyssPalette) = TextFieldDefaults.colors(
    focusedContainerColor = palette.surface,
    unfocusedContainerColor = palette.surface,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = palette.primary,
    focusedTextColor = palette.onSurface,
    unfocusedTextColor = palette.onSurface,
)
