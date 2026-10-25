package com.txao.phone

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Classic dial-pad key tones, honouring the system "dial pad tones" setting. */
class KeyTones(private val ctx: Context) {
    private var gen: ToneGenerator? = try { ToneGenerator(AudioManager.STREAM_DTMF, 80) } catch (_: RuntimeException) { null }

    private fun enabled() = Settings.System.getInt(ctx.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) != 0

    fun play(key: Char, ms: Int = 120) {
        if (!enabled()) return
        val tone = when (key) {
            '0' -> ToneGenerator.TONE_DTMF_0; '1' -> ToneGenerator.TONE_DTMF_1; '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3; '4' -> ToneGenerator.TONE_DTMF_4; '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6; '7' -> ToneGenerator.TONE_DTMF_7; '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9; '*' -> ToneGenerator.TONE_DTMF_S; '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        gen?.startTone(tone, ms)
    }

    fun release() { gen?.release(); gen = null }
}

@Composable
fun rememberKeyTones(): KeyTones {
    val ctx = LocalContext.current.applicationContext
    val tones = remember { KeyTones(ctx) }
    DisposableEffect(tones) { onDispose { tones.release() } }
    return tones
}
