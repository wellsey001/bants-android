package com.wellsey.bants.calls

import android.graphics.Bitmap
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
 * Blurs everything behind you in video calls.
 *
 * Kept fast by never leaving the camera's own YUV format: the brightness and colour planes are
 * blurred and blended directly. Google's on-device selfie model (ML Kit) only ever sees a small
 * copy of the frame (it works at about that size anyway), and its mask is smoothed over time and
 * space so your outline doesn't flicker. The background blur is a proper soft blur (area shrink +
 * three box passes ≈ Gaussian, then a smooth stretch back), not a blocky pixelation.
 *
 * While one frame is being worked on, newer ones are skipped, so a slow phone gets fewer frames
 * rather than a growing delay.
 */
class BackgroundBlur(strong: Boolean, private val image: Bitmap? = null) : VideoProcessor {
    /** How far the background is shrunk before blurring: bigger = blurrier. */
    @Volatile var factor: Int = if (strong) 12 else 7

    private var sink: VideoSink? = null
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "BantsBlur").apply { priority = Thread.MAX_PRIORITY - 1 } }
    private val busy = AtomicBoolean(false)
    @Volatile private var closed = false
    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build(),
    )

    // The mask from last time (frame orientation, small), blended with the new one so edges stay steady.
    private var lastMask: FloatArray? = null
    private var lastMaskW = 0
    private var lastMaskH = 0

    // Reused between frames so we don't churn memory 30 times a second.
    private var yBytes = ByteArray(0)
    private var uBytes = ByteArray(0)
    private var vBytes = ByteArray(0)

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
                    // Same rotation as the camera gave us: we never turned the picture itself.
                    val f = VideoFrame(result, rotation, timestamp)
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

    // ───────────────────────── the work ─────────────────────────

    private fun process(src: VideoFrame.I420Buffer, rotation: Int): VideoFrame.I420Buffer {
        val w = src.width
        val h = src.height
        val cw = (w + 1) / 2
        val ch = (h + 1) / 2
        yBytes = readPlane(src.dataY, src.strideY, w, h, yBytes)
        uBytes = readPlane(src.dataU, src.strideU, cw, ch, uBytes)
        vBytes = readPlane(src.dataV, src.strideV, cw, ch, vBytes)

        // 1. Who's the person? Ask the model about a small copy.
        val mask = personMask(w, h, cw, ch, rotation)
        val mw = lastMaskW
        val mh = lastMaskH

        val out = JavaI420Buffer.allocate(w, h)
        if (image != null) {
            // A picture behind you instead of a blur.
            val bg = backgroundPlanes(w, h, cw, ch, rotation)
            blendFull(yBytes, w, h, bg.first, mask, mw, mh, out.dataY, out.strideY)
            blendFull(uBytes, cw, ch, bg.second, mask, mw, mh, out.dataU, out.strideU)
            blendFull(vBytes, cw, ch, bg.third, mask, mw, mh, out.dataV, out.strideV)
            return out
        }

        // 2. A soft blurred copy of each plane, kept small.
        val d = max(2, factor)
        val by = blurredSmall(yBytes, w, h, d)
        val bu = blurredSmall(uBytes, cw, ch, d)
        val bv = blurredSmall(vBytes, cw, ch, d)

        // 3. Blend: you from the sharp frame, the rest from the blur, soft at the edges.
        blend(yBytes, w, h, by, mask, mw, mh, out.dataY, out.strideY)
        blend(uBytes, cw, ch, bu, mask, mw, mh, out.dataU, out.strideU)
        blend(vBytes, cw, ch, bv, mask, mw, mh, out.dataV, out.strideV)
        return out
    }

    /** Copies a plane out of the (possibly strided) buffer into a tight array. */
    private fun readPlane(buf: ByteBuffer, stride: Int, w: Int, h: Int, reuse: ByteArray): ByteArray {
        val out = if (reuse.size == w * h) reuse else ByteArray(w * h)
        val b = buf.duplicate()
        if (stride == w) {
            b.position(0)
            b.get(out, 0, w * h)
        } else {
            for (row in 0 until h) {
                b.position(row * stride)
                b.get(out, row * w, w)
            }
        }
        return out
    }

    private class Small(val px: FloatArray, val w: Int, val h: Int)

    /** Shrinks by averaging (no aliasing), then three box-blur passes, which look like a Gaussian. */
    private fun blurredSmall(plane: ByteArray, w: Int, h: Int, d: Int): Small {
        val sw = max(1, (w + d - 1) / d)
        val sh = max(1, (h + d - 1) / d)
        val sum = FloatArray(sw * sh)
        val cnt = IntArray(sw * sh)
        val col = IntArray(w) { it / d }
        for (y in 0 until h) {
            val sy = (y / d) * sw
            val row = y * w
            for (x in 0 until w) {
                val i = sy + col[x]
                sum[i] += (plane[row + x].toInt() and 0xff).toFloat()
                cnt[i]++
            }
        }
        for (i in sum.indices) sum[i] = sum[i] / max(1, cnt[i]).toFloat()
        val tmp = FloatArray(sum.size)
        repeat(3) {
            boxH(sum, tmp, sw, sh, 2)
            boxV(tmp, sum, sw, sh, 2)
        }
        return Small(sum, sw, sh)
    }

    private fun boxH(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val n = 2 * r + 1
        for (y in 0 until h) {
            val o = y * w
            var acc = 0f
            for (k in -r..r) acc += src[o + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[o + x] = acc / n
                acc += src[o + min(w - 1, x + r + 1)] - src[o + max(0, x - r)]
            }
        }
    }

    private fun boxV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val n = 2 * r + 1
        for (x in 0 until w) {
            var acc = 0f
            for (k in -r..r) acc += src[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = acc / n
                acc += src[min(h - 1, y + r + 1) * w + x] - src[max(0, y - r) * w + x]
            }
        }
    }

    /**
     * The person mask, in the frame's own orientation, as a small float grid (1 = you).
     * The model sees an upright ~256px picture; its answer is turned back to match the frame.
     */
    private fun personMask(w: Int, h: Int, cw: Int, ch: Int, rotation: Int): FloatArray {
        // A small RGB copy straight from YUV (nearest pixel is plenty for the model).
        val target = 256
        val s = max(1, max(w, h) / target)
        val tw = max(1, w / s)
        val th = max(1, h / s)
        val px = IntArray(tw * th)
        for (ty in 0 until th) {
            val y = ty * s
            val cy = (y shr 1) * cw
            for (tx in 0 until tw) {
                val x = tx * s
                val yy = (yBytes[y * w + x].toInt() and 0xff) - 16
                val uu = (uBytes[cy + (x shr 1)].toInt() and 0xff) - 128
                val vv = (vBytes[cy + (x shr 1)].toInt() and 0xff) - 128
                val c = 1192 * max(0, yy)
                val r = ((c + 1634 * vv) shr 10).coerceIn(0, 255)
                val g = ((c - 833 * vv - 400 * uu) shr 10).coerceIn(0, 255)
                val b = ((c + 2066 * uu) shr 10).coerceIn(0, 255)
                px[ty * tw + tx] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        // Turn it upright for the model.
        val (upright, uw, uh) = rotateInts(px, tw, th, rotation)
        val bmp = Bitmap.createBitmap(upright, uw, uh, Bitmap.Config.ARGB_8888)
        val seg = try {
            Tasks.await(segmenter.process(InputImage.fromBitmap(bmp, 0)))
        } finally {
            bmp.recycle()
        }
        val sw = seg.width
        val sh = seg.height
        val raw = FloatArray(sw * sh)
        seg.buffer.order(ByteOrder.nativeOrder()).also { it.rewind() }.asFloatBuffer().get(raw)

        // Back to the frame's orientation.
        val (m, mw, mh) = rotateFloats(raw, sw, sh, (360 - rotation) % 360)

        // A touch of spatial smoothing, then blend with last time so edges don't shimmer.
        val tmp = FloatArray(m.size)
        boxH(m, tmp, mw, mh, 1)
        boxV(tmp, m, mw, mh, 1)
        val prev = lastMask
        if (prev != null && prev.size == m.size && lastMaskW == mw) {
            for (i in m.indices) m[i] = m[i] * 0.65f + prev[i] * 0.35f
        }
        lastMask = m
        lastMaskW = mw
        lastMaskH = mh
        return m
    }

    /**
     * out = sharp × a + blurred × (1 − a), where a comes from the mask.
     * Both the blur and the mask are small, so they're sampled with bilinear filtering for smooth edges.
     */
    private fun blend(sharp: ByteArray, w: Int, h: Int, blur: Small, mask: FloatArray, mw: Int, mh: Int, dst: ByteBuffer, dstStride: Int) {
        val bw = blur.w
        val bh = blur.h
        val bp = blur.px
        // Per-column lookups, worked out once per plane.
        val bx0 = IntArray(w); val bx1 = IntArray(w); val bfx = FloatArray(w)
        val mx0 = IntArray(w); val mx1 = IntArray(w); val mfx = FloatArray(w)
        for (x in 0 until w) {
            val fx = (x + 0.5f) * bw / w - 0.5f
            val ix = fx.toInt().coerceIn(0, bw - 1)
            bx0[x] = ix; bx1[x] = min(bw - 1, ix + 1); bfx[x] = (fx - ix).coerceIn(0f, 1f)
            val gx = (x + 0.5f) * mw / w - 0.5f
            val jx = gx.toInt().coerceIn(0, mw - 1)
            mx0[x] = jx; mx1[x] = min(mw - 1, jx + 1); mfx[x] = (gx - jx).coerceIn(0f, 1f)
        }
        val row = ByteArray(w)
        val d = dst.duplicate()
        for (y in 0 until h) {
            val fy = (y + 0.5f) * bh / h - 0.5f
            val iy = fy.toInt().coerceIn(0, bh - 1)
            val by0 = iy * bw
            val by1 = min(bh - 1, iy + 1) * bw
            val bfy = (fy - iy).coerceIn(0f, 1f)
            val gy = (y + 0.5f) * mh / h - 0.5f
            val jy = gy.toInt().coerceIn(0, mh - 1)
            val my0 = jy * mw
            val my1 = min(mh - 1, jy + 1) * mw
            val mfy = (gy - jy).coerceIn(0f, 1f)
            val o = y * w
            for (x in 0 until w) {
                // Mask value, then a smoothstep so you're crisp and the background fully blurred.
                val m0 = mask[my0 + mx0[x]] + (mask[my0 + mx1[x]] - mask[my0 + mx0[x]]) * mfx[x]
                val m1 = mask[my1 + mx0[x]] + (mask[my1 + mx1[x]] - mask[my1 + mx0[x]]) * mfx[x]
                var a = m0 + (m1 - m0) * mfy
                a = ((a - 0.35f) * 3.3333f).coerceIn(0f, 1f)
                val s = sharp[o + x].toInt() and 0xff
                if (a >= 0.995f) {
                    row[x] = s.toByte()
                    continue
                }
                a = a * a * (3 - 2 * a)
                val b0 = bp[by0 + bx0[x]] + (bp[by0 + bx1[x]] - bp[by0 + bx0[x]]) * bfx[x]
                val b1 = bp[by1 + bx0[x]] + (bp[by1 + bx1[x]] - bp[by1 + bx0[x]]) * bfx[x]
                val bv = b0 + (b1 - b0) * bfy
                row[x] = (s * a + bv * (1 - a) + 0.5f).toInt().coerceIn(0, 255).toByte()
            }
            d.position(y * dstStride)
            d.put(row, 0, w)
        }
    }

    // ───────────────────────── picture backgrounds ─────────────────────────

    private var bgKey = ""
    private var bgPlanes: Triple<ByteArray, ByteArray, ByteArray>? = null

    /**
     * The background picture as Y, U and V planes the size of the frame, in the frame's own
     * orientation (so it looks upright once the frame is turned for display). Made once per size.
     */
    private fun backgroundPlanes(w: Int, h: Int, cw: Int, ch: Int, rotation: Int): Triple<ByteArray, ByteArray, ByteArray> {
        val key = "$w x $h r $rotation"
        bgPlanes?.let { if (bgKey == key) return it }
        val img = image!!
        // Upright size of the frame, then crop the picture to fill it (like "cover").
        val uw = if (rotation % 180 == 0) w else h
        val uh = if (rotation % 180 == 0) h else w
        val scale = maxOf(uw.toFloat() / img.width, uh.toFloat() / img.height)
        val sw = (img.width * scale).toInt().coerceAtLeast(uw)
        val sh = (img.height * scale).toInt().coerceAtLeast(uh)
        val scaled = Bitmap.createScaledBitmap(img, sw, sh, true)
        val upright = Bitmap.createBitmap(scaled, (sw - uw) / 2, (sh - uh) / 2, uw, uh)
        // Turn it back the other way so it matches the camera's raw orientation.
        val m = android.graphics.Matrix().apply { postRotate(((360 - rotation) % 360).toFloat()) }
        val framed = if (rotation != 0) Bitmap.createBitmap(upright, 0, 0, uw, uh, m, true) else upright
        val px = IntArray(w * h)
        framed.getPixels(px, 0, w, 0, 0, minOf(w, framed.width), minOf(h, framed.height))
        val y = ByteArray(w * h)
        val u = ByteArray(cw * ch)
        val v = ByteArray(cw * ch)
        for (row in 0 until h) for (col in 0 until w) {
            val p = px[row * w + col]
            val r = p shr 16 and 0xff
            val g = p shr 8 and 0xff
            val b = p and 0xff
            y[row * w + col] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
            if (row and 1 == 0 && col and 1 == 0) {
                val i = (row shr 1) * cw + (col shr 1)
                u[i] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte()
                v[i] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte()
            }
        }
        val out = Triple(y, u, v)
        bgPlanes = out
        bgKey = key
        return out
    }

    /** Like [blend], but the background is a full-size plane (the picture) rather than a small blur. */
    private fun blendFull(sharp: ByteArray, w: Int, h: Int, bg: ByteArray, mask: FloatArray, mw: Int, mh: Int, dst: ByteBuffer, dstStride: Int) {
        val mx0 = IntArray(w); val mx1 = IntArray(w); val mfx = FloatArray(w)
        for (x in 0 until w) {
            val gx = (x + 0.5f) * mw / w - 0.5f
            val jx = gx.toInt().coerceIn(0, mw - 1)
            mx0[x] = jx; mx1[x] = min(mw - 1, jx + 1); mfx[x] = (gx - jx).coerceIn(0f, 1f)
        }
        val row = ByteArray(w)
        val d = dst.duplicate()
        for (y in 0 until h) {
            val gy = (y + 0.5f) * mh / h - 0.5f
            val jy = gy.toInt().coerceIn(0, mh - 1)
            val my0 = jy * mw
            val my1 = min(mh - 1, jy + 1) * mw
            val mfy = (gy - jy).coerceIn(0f, 1f)
            val o = y * w
            for (x in 0 until w) {
                val m0 = mask[my0 + mx0[x]] + (mask[my0 + mx1[x]] - mask[my0 + mx0[x]]) * mfx[x]
                val m1 = mask[my1 + mx0[x]] + (mask[my1 + mx1[x]] - mask[my1 + mx0[x]]) * mfx[x]
                var a = ((m0 + (m1 - m0) * mfy - 0.35f) * 3.3333f).coerceIn(0f, 1f)
                a = a * a * (3 - 2 * a)
                val s = sharp[o + x].toInt() and 0xff
                val bv = bg[o + x].toInt() and 0xff
                row[x] = (s * a + bv * (1 - a) + 0.5f).toInt().coerceIn(0, 255).toByte()
            }
            d.position(y * dstStride)
            d.put(row, 0, w)
        }
    }

    // ───────────────────────── turning small grids ─────────────────────────

    /** Rotates clockwise by 0/90/180/270. */
    private fun rotateInts(src: IntArray, w: Int, h: Int, deg: Int): Triple<IntArray, Int, Int> = when (deg) {
        90 -> Triple(IntArray(w * h).also { d -> for (y in 0 until h) for (x in 0 until w) d[x * h + (h - 1 - y)] = src[y * w + x] }, h, w)
        180 -> Triple(IntArray(w * h).also { d -> for (i in src.indices) d[src.size - 1 - i] = src[i] }, w, h)
        270 -> Triple(IntArray(w * h).also { d -> for (y in 0 until h) for (x in 0 until w) d[(w - 1 - x) * h + y] = src[y * w + x] }, h, w)
        else -> Triple(src, w, h)
    }

    private fun rotateFloats(src: FloatArray, w: Int, h: Int, deg: Int): Triple<FloatArray, Int, Int> = when (deg) {
        90 -> Triple(FloatArray(w * h).also { d -> for (y in 0 until h) for (x in 0 until w) d[x * h + (h - 1 - y)] = src[y * w + x] }, h, w)
        180 -> Triple(FloatArray(w * h).also { d -> for (i in src.indices) d[src.size - 1 - i] = src[i] }, w, h)
        270 -> Triple(FloatArray(w * h).also { d -> for (y in 0 until h) for (x in 0 until w) d[(w - 1 - x) * h + y] = src[y * w + x] }, h, w)
        else -> Triple(src.copyOf(), w, h)
    }
}
