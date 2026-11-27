package com.wellsey.bants.calls

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Blurs everything behind you in video calls. Each camera frame goes through Google's
 * on-device selfie model (ML Kit), which says which pixels are you; the rest is blurred.
 * Runs entirely on the phone. While a frame is being worked on, newer frames are skipped,
 * so a slow phone gets a lower frame rate rather than a growing delay.
 */
class BackgroundBlur(strong: Boolean) : VideoProcessor {
    /** How much the background is shrunk before being stretched back: bigger = blurrier. */
    @Volatile var factor: Int = if (strong) 20 else 10

    private var sink: VideoSink? = null
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "BantsBlur").apply { priority = Thread.NORM_PRIORITY + 1 } }
    private val busy = AtomicBoolean(false)
    @Volatile private var closed = false
    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build(),
    )
    // The last mask, blended with the new one so edges don't flicker.
    private var lastMask: FloatArray? = null

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    override fun onCapturerStarted(success: Boolean) = Unit
    override fun onCapturerStopped() = Unit

    override fun onFrameCaptured(frame: VideoFrame) {
        val out = sink ?: return
        if (closed) {
            out.onFrame(frame)
            return
        }
        if (!busy.compareAndSet(false, true)) return
        val i420 = frame.buffer.toI420()
        if (i420 == null) {
            busy.set(false)
            return
        }
        val rotation = frame.rotation
        val timestamp = frame.timestampNs
        worker.execute {
            try {
                if (!closed) {
                    val result = process(i420, rotation)
                    val f = VideoFrame(result, 0, timestamp)
                    out.onFrame(f)
                    f.release()
                }
            } catch (_: Throwable) {
                // A bad frame shouldn't end the call.
            } finally {
                i420.release()
                busy.set(false)
            }
        }
    }

    fun dispose() {
        closed = true
        worker.execute { runCatching { segmenter.close() } }
        worker.shutdown()
    }

    private fun process(src: VideoFrame.I420Buffer, rotation: Int): VideoFrame.I420Buffer {
        var bmp = toBitmap(src)
        // Work upright, so the model sees a person the right way up.
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, false)
            bmp.recycle()
            bmp = r
        }
        val w = bmp.width
        val h = bmp.height

        val seg = Tasks.await(segmenter.process(InputImage.fromBitmap(bmp, 0)))
        val mw = seg.width
        val mh = seg.height
        val buf = seg.buffer.order(ByteOrder.nativeOrder())
        buf.rewind()
        val raw = FloatArray(mw * mh)
        buf.asFloatBuffer().get(raw)
        val prev = lastMask
        val mask = if (prev != null && prev.size == raw.size) FloatArray(raw.size) { i -> raw[i] * 0.6f + prev[i] * 0.4f } else raw
        lastMask = mask

        // Blur: shrink a lot, then stretch back in two steps (smoother than one).
        val f = factor
        val tiny = Bitmap.createScaledBitmap(bmp, max(1, w / f), max(1, h / f), true)
        val mid = Bitmap.createScaledBitmap(tiny, max(1, w / 4), max(1, h / 4), true)
        val blurred = Bitmap.createScaledBitmap(mid, w, h, true)
        tiny.recycle()
        mid.recycle()

        val fg = IntArray(w * h)
        val bg = IntArray(w * h)
        bmp.getPixels(fg, 0, w, 0, 0, w, h)
        blurred.getPixels(bg, 0, w, 0, 0, w, h)
        bmp.recycle()
        blurred.recycle()

        // Mix: you from the sharp picture, the rest from the blurred one, with soft edges.
        for (y in 0 until h) {
            val my = min(mh - 1, y * mh / h)
            for (x in 0 until w) {
                val mx = min(mw - 1, x * mw / w)
                var a = mask[my * mw + mx]
                // Smoothstep: confident pixels snap to sharp/blurred, edges stay soft.
                a = ((a - 0.3f) / 0.4f).coerceIn(0f, 1f)
                a = a * a * (3 - 2 * a)
                val i = y * w + x
                if (a >= 0.999f) continue
                val p = fg[i]
                val q = bg[i]
                if (a <= 0.001f) {
                    fg[i] = q
                    continue
                }
                val r = ((p shr 16 and 0xff) * a + (q shr 16 and 0xff) * (1 - a)).toInt()
                val g = ((p shr 8 and 0xff) * a + (q shr 8 and 0xff) * (1 - a)).toInt()
                val bl = ((p and 0xff) * a + (q and 0xff) * (1 - a)).toInt()
                fg[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        return toI420(fg, w, h)
    }

    private fun toBitmap(b: VideoFrame.I420Buffer): Bitmap {
        val w = b.width
        val h = b.height
        val y = b.dataY
        val u = b.dataU
        val v = b.dataV
        val sy = b.strideY
        val su = b.strideU
        val sv = b.strideV
        val px = IntArray(w * h)
        for (row in 0 until h) {
            val yRow = row * sy
            val uvRow = (row shr 1)
            for (col in 0 until w) {
                val yy = (y.get(yRow + col).toInt() and 0xff) - 16
                val uu = (u.get(uvRow * su + (col shr 1)).toInt() and 0xff) - 128
                val vv = (v.get(uvRow * sv + (col shr 1)).toInt() and 0xff) - 128
                val c = 1192 * max(0, yy)
                val r = ((c + 1634 * vv) shr 10).coerceIn(0, 255)
                val g = ((c - 833 * vv - 400 * uu) shr 10).coerceIn(0, 255)
                val bl = ((c + 2066 * uu) shr 10).coerceIn(0, 255)
                px[row * w + col] = (0xff shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun toI420(px: IntArray, w: Int, h: Int): VideoFrame.I420Buffer {
        val out = JavaI420Buffer.allocate(w, h)
        val y: ByteBuffer = out.dataY
        val u: ByteBuffer = out.dataU
        val v: ByteBuffer = out.dataV
        val sy = out.strideY
        val su = out.strideU
        val sv = out.strideV
        for (row in 0 until h) {
            for (col in 0 until w) {
                val p = px[row * w + col]
                val r = p shr 16 and 0xff
                val g = p shr 8 and 0xff
                val b = p and 0xff
                y.put(row * sy + col, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte())
                if (row and 1 == 0 && col and 1 == 0) {
                    val ui = (row shr 1) * su + (col shr 1)
                    val vi = (row shr 1) * sv + (col shr 1)
                    u.put(ui, (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte())
                    v.put(vi, (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte())
                }
            }
        }
        return out
    }
}
