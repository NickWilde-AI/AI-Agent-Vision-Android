package com.tencent.edgeagent

import android.app.Application
import timber.log.Timber

/**
 * Application 类
 * 
 * 职责：
 * 1. 初始化 Timber 日志库
 * 2. 全局配置
 */
class EdgeAgentApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // 初始化 Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("EdgeAgentApplication 初始化完成")
    }
}
