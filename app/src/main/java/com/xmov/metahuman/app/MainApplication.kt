package com.xmov.metahuman.app

import android.app.Application
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainApplication : Application() {

    companion object {
        private const val TAG = "MainApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化 AppSettings
        AppSettings.init(this)

        // PDF 解析初始化（用于导入 PDF 剧本）
        PDFBoxResourceLoader.init(this)

        Log.d(TAG, "TRPG DM Digital Host System initialized")
    }
}
