package com.txao.phone

import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TxaoTheme { CallScreen(onDone = { finish() }) } }
    }
}

@Composable
private fun CallScreen(onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val calls by CallManager.calls.collectAsState()
    val tick by CallManager.tick.collectAsState()
    val audio by CallManager.audio.collectAsState()
    var muted by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(calls) { if (calls.isEmpty()) { delay(600); onDone() } }

    @Suppress("UNUSED_EXPRESSION") tick
    val call = CallManager.primary()
    if (call == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val number = CallManager.number(call)
    val name = remember(number) { Data.lookupName(ctx, number) }
    val state = call.state
    val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER
    val ringing = state == Call.STATE_RINGING

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(name ?: PhoneUtil.pretty(number).ifEmpty { "Unknown" }, fontSize = 32.sp, fontWeight = FontWeight.Black)
        if (name != null) Text(PhoneUtil.pretty(number), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val connected = call.details?.connectTimeMillis ?: 0L
        val label = when (state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DISCONNECTED -> "Call ended"
            else -> if (connected > 0) PhoneUtil.duration(((now - connected) / 1000).coerceAtLeast(0)) else "Connected"
        }
        Text(label, color = Accent, fontSize = 18.sp)

        Spacer(Modifier.weight(1f))

        if (ringing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton(Icons.Filled.CallEnd, Danger, "Decline") { call.reject(false, null) }
                RoundButton(Icons.Filled.Call, Accent, "Answer") { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
            }
        } else {
            if (keypad) {
                DtmfPad { call.playDtmfTone(it); call.stopDtmfTone() }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Toggle(Icons.Filled.MicOff, "Mute", muted) { muted = !muted; CallManager.toggleMute(muted) }
                    Toggle(Icons.Filled.Dialpad, "Keypad", false) { keypad = true }
                    Toggle(Icons.Filled.VolumeUp, "Speaker", speaker) { CallManager.setSpeaker(!speaker) }
                    val holding = state == Call.STATE_HOLDING
                    Toggle(Icons.Filled.Pause, "Hold", holding) { if (holding) call.unhold() else call.hold() }
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                if (keypad) TextButton({ keypad = false }) { Text("Hide keypad") }
                RoundButton(Icons.Filled.CallEnd, Danger, "End") { call.disconnect() }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RoundButton(icon: ImageVector, color: Color, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(32.dp)) }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(if (on) Color.White else Glass),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Color.Black else Color.White) }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DtmfPad(onKey: (Char) -> Unit) {
    val rows = listOf("123", "456", "789", "*0#")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (r in rows) Row {
            for (k in r) {
                Box(Modifier.padding(6.dp).size(64.dp).clip(CircleShape).background(Glass).clickable { onKey(k) }, contentAlignment = Alignment.Center) {
                    Text(k.toString(), fontSize = 24.sp)
                }
            }
        }
    }
}
