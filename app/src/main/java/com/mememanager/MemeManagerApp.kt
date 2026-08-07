package com.mememanager

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.VideoFrameDecoder
import coil.request.EventListener
import coil.request.ImageRequest
import coil.request.ImageResult
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MemeManagerApp : Application() {

    companion object {
        private const val TAG = "CoilTrace"
    }

    override fun onCreate() {
        super.onCreate()
        // 全局 ImageLoader：注册 GIF 动画 + 视频帧解码器
        // 附加 EventListener：Logcat 记录每个图片请求的数据源与耗时，
        // 排查"白方块/卡顿"用（filter "CoilTrace"）。排查完可移除。
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .eventListener(object : EventListener() {
                private var startMs = 0L
                override fun onStart(request: ImageRequest) {
                    startMs = System.currentTimeMillis()
                    Log.d(TAG, "start  ${shortData(request)}")
                }

                override fun onSuccess(request: ImageRequest, result: ImageResult) {
                    Log.d(
                        TAG,
                        "OK     ${shortData(request)} src=${result.dataSource} " +
                            "dur=${System.currentTimeMillis() - startMs}ms"
                    )
                }

                override fun onError(request: ImageRequest, throwable: Throwable) {
                    Log.d(
                        TAG,
                        "FAIL   ${shortData(request)} ${throwable.message} " +
                            "dur=${System.currentTimeMillis() - startMs}ms"
                    )
                }
            })
            .build()
        Coil.setImageLoader(imageLoader)
        // 注意：不再启动预热 Jieba——词典 5MB/35 万词条，构建 trie 会引发
        // 频繁 GC 导致全局卡顿 15-20 秒。智能搜索默认关闭，首次打开时才懒加载
        // （搜索页已标注"刚启动时打开可能卡顿"）。
    }

    /** 请求数据截断显示（filePath 太长） */
    private fun shortData(request: ImageRequest): String {
        val s = request.data?.toString() ?: "null"
        return if (s.length > 60) "…${s.takeLast(57)}" else s
    }
}
