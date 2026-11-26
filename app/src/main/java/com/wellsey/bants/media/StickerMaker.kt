package com.wellsey.bants.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Cuts the person or thing out of a photo and gives it a white die-cut border,
 * like a real sticker. Runs on the phone (Google's on-device cut-out model).
 */
object StickerMaker {
    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build(),
        )
    }

    /** The cut-out on a transparent background, or null if nothing stood out in the photo. */
    suspend fun cutOut(photo: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        segmenter.process(InputImage.fromBitmap(photo, 0))
            .addOnSuccessListener { cont.resume(it.foregroundBitmap) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Trims empty space, adds the white border, and fits it in a 512×512 square. */
    fun dieCut(cut: Bitmap, size: Int = 512): Bitmap? {
        val bounds = opaqueBounds(cut) ?: return null
        val trimmed = Bitmap.createBitmap(cut, bounds.left, bounds.top, bounds.width(), bounds.height())
        val border = size * 0.035f
        val inner = size - 2 * border - 8
        val scale = inner / max(trimmed.width, trimmed.height)
        val w = (trimmed.width * scale).toInt().coerceAtLeast(1)
        val h = (trimmed.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(trimmed, w, h, true)

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val left = (size - w) / 2f
        val top = (size - h) / 2f
        // The border: the cut-out's shape in white, stamped in a ring around it.
        val white = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
        val steps = 32
        for (ring in listOf(border, border * 0.5f)) {
            for (i in 0 until steps) {
                val a = 2 * Math.PI * i / steps
                canvas.drawBitmap(scaled, left + (cos(a) * ring).toFloat(), top + (sin(a) * ring).toFloat(), white)
            }
        }
        canvas.drawBitmap(scaled, left, top, white)
        canvas.drawBitmap(scaled, left, top, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** The smallest box holding everything that isn't see-through. */
    private fun opaqueBounds(b: Bitmap): android.graphics.Rect? {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) > 24) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        // Ignore specks: a "subject" smaller than 3% of the photo isn't really one.
        if ((maxX - minX) * (maxY - minY) < w * h * 0.03) return null
        return android.graphics.Rect(minX, minY, maxX + 1, maxY + 1)
    }
}
