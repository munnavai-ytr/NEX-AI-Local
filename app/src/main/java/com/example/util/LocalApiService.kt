package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import com.example.inference.EngineManager
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*

class LocalApiService : Service() {

    private var server: NettyApplicationEngine? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        val channelId = "local_api_service"
        val channel = NotificationChannel(
            channelId,
            "Local API Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Local API Server")
            .setContentText("Local API server is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a proper icon
            .build()
        
        startForeground(1, notification)
        
        // Start Ktor server
        startServer()
    }

    private fun startServer() {
        val engineManager = EngineManager.getInstance(this)
        serverInstance = LocalApiServer(engineManager)
        serverInstance?.start()
    }

    private var serverInstance: LocalApiServer? = null

    override fun onDestroy() {
        serverInstance?.stop()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
