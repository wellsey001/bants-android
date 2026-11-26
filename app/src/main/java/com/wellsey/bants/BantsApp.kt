package com.wellsey.bants

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import android.os.Build
import com.wellsey.bants.data.Bants

class BantsApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Bants.init(this)
        Notifications.createChannel(this)
        Sounds.init(this)
        com.wellsey.bants.calls.CallManager.init(this)
        com.wellsey.bants.calls.CallService.createChannel(this)
        com.wellsey.bants.calls.IncomingCallNotification.createChannel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = Bants.onForeground()
            override fun onStop(owner: LifecycleOwner) {
                Bants.inForeground = false
            }
        })
    }

    // Images are fetched with the sign-in token attached, since media is private to chat members.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Bants.api.authedHttp }
        .components {
            // Animated GIFs and stickers.
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .crossfade(true)
        .build()
}
