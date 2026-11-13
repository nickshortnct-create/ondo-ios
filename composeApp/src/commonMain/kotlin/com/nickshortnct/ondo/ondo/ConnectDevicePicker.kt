package com.nickshortnct.ondo.ondo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Devices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Spotify Connect's green: marks the device music is playing on when it isn't this phone. */
val ConnectGreen = Color(0xFF1ED760)

/** The device music is playing on when it isn't this phone, else null. */
@Composable
fun connectPlayingOnName(): String? {
    val connect = koinInject<OndoConnect>()
    val selected by connect.selected.collectAsState()
    val devices by connect.devices.collectAsState()
    return selected?.let { id -> devices.find { it.id == id }?.name ?: "another device" }
}

/** True while music plays on another Ondo Connect device (the Now Playing device button turns green). */
@Composable
fun connectPlayingElsewhere(): Boolean {
    val connect = koinInject<OndoConnect>()
    val selected by connect.selected.collectAsState()
    return selected != null
}

/**
 * Spotify-style device sheet: where music is playing, every Connect device with what it's
 * playing, and one tap to move playback there (or back to this phone). Sets Connect up in
 * one tap with the music-server account when it isn't yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectDeviceSheet(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val connect = koinInject<OndoConnect>()
    val settings = koinInject<OndoSettings>()
    val enabled by connect.enabled.collectAsState()
    val online by connect.connected.collectAsState()
    val devices by connect.devices.collectAsState()
    val selected by connect.selected.collectAsState()
    val snapshots by connect.snapshots.collectAsState()
    val error by connect.error.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val signedIn = settings.get("Connect").key.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Play on", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if(!enabled || !signedIn) {
                Text("Ondo Connect plays music on one device at a time and lets every other device control it, like Spotify Connect.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { scope.launch {
                        busy = true; message = null
                        try { connect.signInWithServerAccount() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message }
                        busy = false
                    } },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = ConnectGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if(busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black) else Text("Turn on Ondo Connect", fontWeight = FontWeight.SemiBold)
                }
            } else {
                DeviceRow(
                    name = "This phone",
                    detail = if(selected == null) "Playing here" else null,
                    current = selected == null,
                    onClick = { connect.playOn(null); onDismiss() }
                )
                devices.forEach { device ->
                    val snap = snapshots[device.id]
                    val track = snap?.currentIndex?.let { snap.queue.getOrNull(it) }
                    DeviceRow(
                        name = device.name,
                        detail = track?.let { "${if(snap?.playing == true) "Playing" else "Paused"} · ${it.title} — ${it.artist}" },
                        current = selected == device.id,
                        onClick = { connect.playOn(device.id); onDismiss() }
                    )
                }
                if(!online) Text(error ?: "Connecting…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                else if(devices.isEmpty()) Text("No other devices online. Open Ondo on your computer with Connect turned on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            TextButton(onClick = { onDismiss(); onOpenSettings() }, modifier = Modifier.align(Alignment.End)) { Text("Connect settings") }
        }
    }
}

@Composable
private fun DeviceRow(name: String, detail: String?, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .background(if(current) ConnectGreen.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if(current) ConnectGreen else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Devices, contentDescription = null, tint = if(current) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, color = if(current) ConnectGreen else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
