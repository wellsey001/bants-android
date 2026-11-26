package com.wellsey.bants

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.wellsey.bants.data.Bants

class BantsApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        Bants.init(this)
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
        .crossfade(true)
        .build()
}
