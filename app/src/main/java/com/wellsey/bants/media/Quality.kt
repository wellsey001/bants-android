package com.wellsey.bants.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How big photos and videos are when you send them. */
enum class Quality(val key: String, val label: String, val about: String) {
    Auto("auto", "Auto", "Full quality on Wi-Fi, smaller on mobile data"),
    Standard("standard", "Standard", "Smaller and quicker to send"),
    HD("hd", "HD", "Sharper, bigger files"),
    Original("original", "Original", "Exactly as it is on your phone, up to 4K");

    companion object {
        fun of(key: String?) = values().firstOrNull { it.key == key } ?: Auto
    }
}

object MediaQuality {
    /** Wi-Fi or Ethernet, or any connection that isn't charged by the megabyte. */
    fun onUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Auto becomes Original on Wi-Fi and Standard on mobile data. */
    fun resolve(context: Context, q: Quality): Quality = when (q) {
        Quality.Auto -> if (onUnmetered(context)) Quality.Original else Quality.Standard
        else -> q
    }

    /** Longest side for photos, or null to send the original file. */
    fun photoMaxSide(q: Quality): Int? = when (q) {
        Quality.Standard -> 2048
        Quality.HD -> 4096
        else -> null
    }

    /** Short side for videos (720p / 1080p), or null to send the original. */
    fun videoShortSide(q: Quality): Int? = when (q) {
        Quality.Standard -> 720
        Quality.HD -> 1080
        else -> null
    }

    /**
     * Shrinks a video so its short side is [shortSide] pixels, as H.264 MP4.
     * Returns null if it's already that small (send the original instead).
     */
    suspend fun shrinkVideo(context: Context, uri: Uri, shortSide: Int, out: File): File? {
        val (w, h) = withContext(Dispatchers.IO) { displaySize(context, uri) } ?: return null
        val short = minOf(w, h)
        if (short <= shortSide) return null
        val scale = shortSide.toFloat() / short
        val tw = (w * scale).toInt() / 2 * 2
        val th = (h * scale).toInt() / 2 * 2
        // Transformer has to be driven from a thread with a Looper.
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (cont.isActive) cont.resume(out)
                        }

                        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                            out.delete()
                            if (cont.isActive) cont.resumeWithException(exception)
                        }
                    })
                    .build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(tw, th, Presentation.LAYOUT_SCALE_TO_FIT))))
                    .build()
                transformer.start(item, out.absolutePath)
                cont.invokeOnCancellation { transformer.cancel(); out.delete() }
            }
        }
    }

    /** Width and height as the video is shown (rotation applied). */
    private fun displaySize(context: Context, uri: Uri): Pair<Int, Int>? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) h to w else w to h
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}
