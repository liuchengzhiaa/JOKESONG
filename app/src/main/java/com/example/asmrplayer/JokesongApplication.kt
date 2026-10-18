package com.example.asmrplayer

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.example.asmrplayer.core.artwork.artworkMemoryCacheBytes
import kotlinx.coroutines.Dispatchers

/** One shared loader; scrolling must not start a decoder per visible poster. */
class JokesongApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizeBytes(artworkMemoryCacheBytes(Runtime.getRuntime().maxMemory()))
                .weakReferencesEnabled(false)
                .build()
        }
        .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(4))
        .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(2))
        .build()
}
