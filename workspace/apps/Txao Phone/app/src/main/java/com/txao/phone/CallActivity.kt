package com.txao.phone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TxaoTheme { CallScreen(onDone = { finish() }) } }
    }
}

/** Replies offered when declining with a message. */
val DefaultQuickReplies = listOf("Can't talk right now. What's up?", "I'll call you back.", "I'll call you later.", "Can't talk now, text me instead.")

@Composable
private fun CallScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val calls by CallManager.calls.collectAsState()
    val tick by CallManager.tick.collectAsState()
    val audio by CallManager.audio.collectAsState()
    var muted by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    var replies by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(calls) { if (calls.isEmpty()) { delay(600); onDone() } }

    @Suppress("UNUSED_EXPRESSION") tick
    val call = CallManager.primary()
    if (call == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val other = calls.firstOrNull { it != call }
    val number = CallManager.number(call)
    val name = remember(number) { Data.lookupName(ctx, number) }
    val photo = remember(number) { Data.lookupPhoto(ctx, number) }
    val bgUri = remember(number) { Backgrounds.uriForNumber(ctx, number) }
    val bg = rememberPhoto(bgUri, 1280)
    val state = call.state
    val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER
    val ringing = state == Call.STATE_RINGING
    val place = remember(number) { PhoneUtil.countryOf(number) }
    val carrier = remember { carrierName(ctx) }
    var more by remember { mutableStateOf(false) }
    val canMerge = other != null && call.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true

    Box(Modifier.fillMaxSize()) {
    if (bg != null) {
        // Contact's own background image, darkened so text and buttons stay readable.
        Image(bg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x80000000), Color(0x66000000), Color(0xCC000000)))))
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (other != null) {
            Row(Modifier.fillMaxWidth().glass(20.dp).clickable { other.unhold() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SwapCalls, null, tint = Lavender)
                val on = CallManager.number(other)
                Text("  " + (Data.lookupName(ctx, on) ?: PhoneUtil.pretty(on).ifEmpty { "Unknown" }) + " · " + if (other.state == Call.STATE_HOLDING) "on hold, tap to switch" else "active", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        WavyAvatar(name ?: PhoneUtil.pretty(number), 176.dp, photo)
        Spacer(Modifier.height(16.dp))
        Text(name ?: PhoneUtil.pretty(number).ifEmpty { "Unknown" }, fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        if (name != null) Text(PhoneUtil.pretty(number), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (place != null) Text(place, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (name == null && ringing) Text("Not in your contacts", color = Danger, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        val connected = call.details?.connectTimeMillis ?: 0L
        val label = when (state) {
            Call.STATE_RINGING -> "Incoming call via $carrier"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling via $carrier"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DISCONNECTED -> "Call ended"
            else -> if (connected > 0) PhoneUtil.duration(((now - connected) / 1000).coerceAtLeast(0)) else "Connected"
        }
        Text(label, color = Mint, fontSize = 18.sp)

        Spacer(Modifier.weight(1f))

        if (ringing) {
            if (replies) {
                Column(Modifier.fillMaxWidth().glass(24.dp).padding(8.dp)) {
                    quickReplies(ctx).forEach { r ->
                        Text(r, Modifier.fillMaxWidth().clickable { call.reject(true, r) }.padding(14.dp))
                    }
                    TextButton({ replies = false }, Modifier.align(Alignment.End)) { Text("Cancel") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.Filled.CallEnd, Danger, "Decline") { call.reject(false, null) }
                    Toggle(Icons.AutoMirrored.Filled.Message, "Message", false) { replies = true }
                    RoundButton(Icons.Filled.Call, Mint, "Answer", OnMint) { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
                }
            }
        } else {
            if (keypad) {
                DtmfPad { call.playDtmfTone(it); call.stopDtmfTone() }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(Icons.Filled.Dialpad, "Keypad", false, Modifier.weight(1f)) { keypad = true }
                    PillButton(Icons.Filled.MicOff, "Mute", muted, Modifier.weight(1f)) { muted = !muted; CallManager.toggleMute(muted) }
                    PillButton(Icons.Filled.VolumeUp, "Speaker", speaker, Modifier.weight(1f)) { CallManager.setSpeaker(!speaker) }
                    Box(Modifier.weight(1f)) {
                        PillButton(Icons.Filled.MoreVert, "More", more, Modifier.fillMaxWidth()) { more = true }
                        DropdownMenu(more, { more = false }) {
                            val holding = state == Call.STATE_HOLDING
                            DropdownMenuItem({ Text(if (holding) "Resume" else "Hold") }, { more = false; if (holding) call.unhold() else call.hold() })
                            DropdownMenuItem({ Text("Add call") }, {
                                more = false
                                ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            })
                            if (other != null && canMerge) DropdownMenuItem({ Text("Merge calls") }, { more = false; call.conference(other) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (keypad) TextButton({ keypad = false }) { Text("Hide keypad") }
                Box(Modifier.width(112.dp).height(72.dp).clip(RoundedCornerShape(36.dp)).background(Danger).clickable { call.disconnect() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}

fun quickReplies(ctx: Context): List<String> {
    val raw = ctx.getSharedPreferences("txao_phone", Context.MODE_PRIVATE).getString("quick_replies", null)
    return raw?.split('\n')?.filter { it.isNotBlank() }?.ifEmpty { null } ?: DefaultQuickReplies
}

@Composable
private fun RoundButton(icon: ImageVector, color: Color, desc: String, iconTint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = iconTint, modifier = Modifier.size(32.dp)) }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(if (on) Lavender else Color(0x1FFFFFFF)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) OnLavender else Color.White) }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DtmfPad(onKey: (Char) -> Unit) {
    val tones = rememberKeyTones()
    val rows = listOf("123", "456", "789", "*0#")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (r in rows) Row {
            for (k in r) {
                Box(Modifier.padding(6.dp).size(64.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable { tones.play(k); onKey(k) }, contentAlignment = Alignment.Center) {
                    Text(k.toString(), fontSize = 24.sp)
                }
            }
        }
    }
}

/** Carrier name for the "Calling via" line; falls back to a neutral word when the phone does not report one. */
private fun carrierName(ctx: Context): String =
    try { ctx.getSystemService(android.telephony.TelephonyManager::class.java).networkOperatorName.takeIf { it.isNotBlank() } } catch (_: Exception) { null } ?: "phone"

/** A rounded rectangle with a wavy outline: a circle whose radius ripples [lobes] times. */
private class WavyShape(private val lobes: Int = 10, private val depth: Float = 0.045f) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path()
        val cx = size.width / 2; val cy = size.height / 2
        val base = minOf(cx, cy) * (1f - depth)
        val steps = 240
        for (i in 0..steps) {
            val a = (i / steps.toFloat()) * (2 * Math.PI).toFloat()
            val r = base * (1f + depth * kotlin.math.sin(lobes * a))
            val x = cx + r * kotlin.math.cos(a); val y = cy + r * kotlin.math.sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

@Composable
private fun WavyAvatar(name: String, size: androidx.compose.ui.unit.Dp, photoUri: String?) {
    val photo = rememberPhoto(photoUri, 512)
    val tint = avatarTint(name)
    Box(
        Modifier.size(size).clip(WavyShape()).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.6f), tint.copy(alpha = 0.2f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) Image(photo, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.trim().firstOrNull()?.uppercase() ?: "#", fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(if (on) Lavender else Color(0x1FFFFFFF)).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (on) OnLavender else Color.White)
        Text(label, fontSize = 12.sp, color = if (on) OnLavender else Color.White, modifier = Modifier.padding(top = 4.dp))
    }
}
