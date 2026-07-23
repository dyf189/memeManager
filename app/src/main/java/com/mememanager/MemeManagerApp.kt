package com.mememanager

import android.app.Application
import com.mememanager.util.JiebaTokenizer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class MemeManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 后台预热 Jieba 词典，避免首次搜索卡顿
        CoroutineScope(Dispatchers.IO).launch {
            JiebaTokenizer.warmUp()
        }
    }
}
