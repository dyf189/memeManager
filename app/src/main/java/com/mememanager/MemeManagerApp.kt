package com.mememanager

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.VideoFrameDecoder
import com.mememanager.util.JiebaTokenizer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class MemeManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局 ImageLoader：注册 GIF 动画 + 视频帧解码器
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .build()
        Coil.setImageLoader(imageLoader)
        CoroutineScope(Dispatchers.IO).launch {
            JiebaTokenizer.warmUp()
        }
    }
}
