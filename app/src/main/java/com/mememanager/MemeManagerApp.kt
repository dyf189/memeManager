package com.mememanager

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp

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
        // 注意：不再启动预热 Jieba——词典 5MB/35 万词条，构建 trie 会引发
        // 频繁 GC 导致全局卡顿 15-20 秒。智能搜索默认关闭，首次打开时才懒加载
        // （搜索页已标注"刚启动时打开可能卡顿"）。
    }
}
