package com.wellsey.bants.calls

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Silences the microphone between words, so keyboards, clicks and background chatter
 * don't come through when you're not talking. Opens instantly when you speak, then
 * fades out gently a moment after you stop, so word endings aren't clipped.
 * Works on the raw 16-bit audio straight from the mic, in place.
 */
class NoiseGate {
    /** Loudness (0..1 RMS) you need to open it. */
    @Volatile var threshold = 0.02f
    private var gain = 1f
    private var holdSamples = 0

    fun reset() {
        gain = 1f
        holdSamples = 0
    }

    fun process(buffer: ByteBuffer, bytesRead: Int) {
        val n = bytesRead / 2
        if (n <= 0) return
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val start = buffer.position()
        var sum = 0.0
        for (i in 0 until n) {
            val v = b.getShort(start + i * 2) / 32768.0
            sum += v * v
        }
        val level = sqrt(sum / n).toFloat()
        if (level > threshold) holdSamples = 16_000 / 3 // stay open ~a third of a second after you stop
        else holdSamples = (holdSamples - n).coerceAtLeast(0)
        val target = if (holdSamples > 0) 1f else 0f
        // Open fast, close slow.
        val step = if (target > gain) 0.02f else 0.0012f
        for (i in 0 until n) {
            gain = if (target > gain) minOf(target, gain + step) else maxOf(target, gain - step)
            if (gain >= 0.999f) continue
            val idx = start + i * 2
            val v = b.getShort(idx)
            b.putShort(idx, (v * gain).toInt().toShort())
        }
    }
}
