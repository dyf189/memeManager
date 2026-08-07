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
        // Jieba 词典加载（约 10 秒）必须在后台提前完成：
        // 懒加载会在导入/搜索时于主线程触发（viewModelScope 默认 Main）→ ANR。
        // 预热放 IO 线程立即开始，期间的 CPU/GC 开销远小于主线程卡死 10 秒。
        CoroutineScope(Dispatchers.IO).launch {
            JiebaTokenizer.warmUp()
        }
    }
}
