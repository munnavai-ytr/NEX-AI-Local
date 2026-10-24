package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.example.inference.EngineManager

class NEXApplication : Application() {

    companion object {
        private const val TAG = "NEXApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NEXApplication initialized.")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "onTrimMemory level: $level")
        // When system is under severe memory pressure or app is going to background, unload engine safely
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            Log.w(TAG, "Releasing engine memory on trim level $level")
            EngineManager.getInstance(this).onLowMemory("onTrimMemory (level $level)")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory callback triggered.")
        EngineManager.getInstance(this).onLowMemory("onLowMemory OS signal")
    }
}
