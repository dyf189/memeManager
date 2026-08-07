package com.mememanager

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.VideoFrameDecoder
import com.mememanager.data.settings.SettingsKeys
import com.mememanager.util.JiebaTokenizer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MemeManagerApp : Application() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

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
        // 设置页可关闭"启动时预热"（首次使用时在 IO 线程加载，那次操作会慢约 10 秒）。
        CoroutineScope(Dispatchers.IO).launch {
            val preload = dataStore.data.first()[SettingsKeys.JIEBA_PRELOAD_ENABLED] ?: true
            if (preload) {
                JiebaTokenizer.warmUp()
            } else {
                Log.d("Jieba", "启动预热已关闭——词典将在首次使用时加载（IO 线程）")
            }
        }
    }
}
