package com.example.mygallery

import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

/**
 * A shared Coil ImageLoader configured with video frame decoding.
 * Coil's default loader only knows how to decode image formats — without
 * VideoFrameDecoder registered, requesting a video Uri silently produces no
 * thumbnail. Built once and reused everywhere thumbnails are shown.
 */
object AppImageLoader {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components { add(VideoFrameDecoder.Factory()) }
                .build()
                .also { instance = it }
        }
    }
}