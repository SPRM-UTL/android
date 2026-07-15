package com.example.android.core.view

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest

object LucideLoader {

    private var imageLoader: ImageLoader? = null

    private fun getImageLoader(context: Context): ImageLoader {
        if (imageLoader == null) {
            synchronized(this) {
                if (imageLoader == null) {
                    imageLoader = ImageLoader.Builder(context.applicationContext)
                        .components {
                            add(SvgDecoder.Factory())
                        }
                        .build()
                }
            }
        }
        return imageLoader!!
    }


    fun cargarIcono(imageView: ImageView, iconName: String) {
        val contexto = imageView.context
        val url = "https://unpkg.com/lucide-static@latest/icons/$iconName.svg"

        val request = ImageRequest.Builder(contexto)
            .data(url)
            .target(imageView)
            .crossfade(true)
            .build()

        getImageLoader(contexto).enqueue(request)
    }
}
