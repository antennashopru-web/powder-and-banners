package ru.argeda.powder

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Звуки синтезируются при запуске (шум и тоны, как раньше в WebAudio) и играются через SoundPool. */
class Sfx(ctx: Context) {
    private val rate = 22050
    private val pool = SoundPool.Builder().setMaxStreams(12)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = Array(7) { IntArray(0) }
    private val last = LongArray(7)
    var on = true

    init {
        val dir = File(ctx.cacheDir, "sfx").apply { mkdirs() }
        fun make(kind: Int, variants: Int, gen: () -> FloatArray) {
            ids[kind] = IntArray(variants) { v ->
                val f = File(dir, "s${kind}_$v.wav")
                writeWav(f, gen())
                pool.load(f.path, 1)
            }
        }
        make(S_SHOT, 2) { mix(0.35, { noise(it, 0.0, 0.18, BAND, 1400.0, 0.35); noise(it, 0.0, 0.3, LOW, 500.0, 0.25) }) }
        make(S_CANNON, 2) { mix(0.85, { noise(it, 0.0, 0.8, LOW, 260.0, 0.9); tone(it, 0.0, 90.0, 30.0, 0.6, 0.5, SINE) }) }
        make(S_IMPACT, 2) { mix(0.45, { noise(it, 0.0, 0.4, LOW, 400.0, 0.4) }) }
        make(S_MELEE, 3) { mix(0.08, { tone(it, 0.0, 1800 + Random.nextDouble() * 600, 900.0, 0.06, 0.08, SQUARE) }) }
        make(S_CLICK, 1) { mix(0.06, { tone(it, 0.0, 900.0, 700.0, 0.05, 0.08, TRI) }) }
        make(S_ORDER, 1) { mix(0.09, { tone(it, 0.0, 500.0, 650.0, 0.08, 0.1, TRI) }) }
        make(S_DONE, 1) {
            mix(0.45, {
                tone(it, 0.0, 523.0, 523.0, 0.15, 0.12, TRI); tone(it, 0.12, 659.0, 659.0, 0.2, 0.12, TRI); tone(it, 0.24, 784.0, 784.0, 0.2, 0.12, TRI)
            })
        }
    }

    /** vol 0..1 уже с учётом расстояния до центра экрана */
    fun play(kind: Int, vol: Float) {
        if (!on || vol <= 0.02f) return
        val now = SystemClock.uptimeMillis()
        if (now - last[kind] < (if (kind == S_SHOT) 60 else 100)) return
        last[kind] = now
        val v = ids[kind]
        if (v.isEmpty()) return
        val id = v[Random.nextInt(v.size)]
        pool.play(id, vol, vol, 1, 0, 1f)
    }

    fun release() = pool.release()

    // ------------------------------------------------------------ синтез
    private fun mix(len: Double, body: (FloatArray) -> Unit): FloatArray {
        val buf = FloatArray((len * rate).toInt() + 1)
        body(buf)
        return buf
    }

    /** шум через биквад-фильтр, громкость спадает по экспоненте до 0.001 */
    private fun noise(buf: FloatArray, at: Double, dur: Double, type: Int, freq: Double, gain: Double) {
        val w0 = 2 * PI * freq / rate
        val q = 1.0
        val alpha = sin(w0) / (2 * q)
        val cw = cos(w0)
        val b0: Double
        val b1: Double
        val b2: Double
        if (type == LOW) { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2 } else { b0 = alpha; b1 = 0.0; b2 = -alpha }
        val a0 = 1 + alpha
        val a1 = -2 * cw
        val a2 = 1 - alpha
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        val n = (dur * rate).toInt()
        val s0 = (at * rate).toInt()
        val k = ln(0.001 / gain) / n
        for (i in 0 until n) {
            if (s0 + i >= buf.size) break
            val x = Random.nextDouble() * 2 - 1
            val y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x; y2 = y1; y1 = y
            buf[s0 + i] += (y * gain * exp(k * i)).toFloat()
        }
    }

    private fun tone(buf: FloatArray, at: Double, f0: Double, f1: Double, dur: Double, gain: Double, type: Int) {
        val n = (dur * rate).toInt()
        val s0 = (at * rate).toInt()
        val k = ln(0.001 / gain) / n
        var ph = 0.0
        for (i in 0 until n) {
            if (s0 + i >= buf.size) break
            val f = f0 * (f1 / f0).pow(i.toDouble() / n)
            ph += f / rate
            val p = ph % 1.0
            val w = when (type) {
                SQUARE -> if (p < 0.5) 1.0 else -1.0
                TRI -> 4 * abs(p - 0.5) - 1
                else -> sin(2 * PI * p)
            }
            buf[s0 + i] += (w * gain * exp(k * i)).toFloat()
        }
    }

    private fun writeWav(f: File, s: FloatArray) {
        val data = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in s) data.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + s.size * 2); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(rate); h.putInt(rate * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(s.size * 2)
        FileOutputStream(f).use { it.write(h.array()); it.write(data.array()) }
    }

    companion object {
        const val LOW = 0
        const val BAND = 1
        const val SINE = 0
        const val SQUARE = 1
        const val TRI = 2
    }
}
