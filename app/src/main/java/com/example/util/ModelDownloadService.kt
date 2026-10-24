package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps model downloads running when the app is in the background or closed,
 * displaying a live progress notification with Pause, Resume, and Cancel actions.
 */
class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager
    private lateinit var downloadManager: ModelDownloadManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        downloadManager = ModelDownloadManager.getInstance(applicationContext)
        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                title = "Preparing model download...",
                text = "Starting download service",
                progress = 0,
                indeterminate = true,
                ongoing = true,
                isPaused = false
            )
        )

        serviceScope.launch {
            downloadManager.downloadState.collectLatest { state ->
                when (state) {
                    is DownloadState.Checking -> {
                        val notification = buildNotification(
                            title = "Preparing download",
                            text = state.message,
                            progress = 0,
                            indeterminate = true,
                            ongoing = true,
                            isPaused = false
                        )
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    }

                    is DownloadState.Progress -> {
                        val downloadedMb = state.downloadedBytes / (1024.0 * 1024.0)
                        val totalMb = state.totalBytes / (1024.0 * 1024.0)
                        val sizePart = if (state.totalBytes > 0) {
                            "%.1f / %.1f MB (%d%%)".format(downloadedMb, totalMb, state.percent)
                        } else {
                            "%.1f MB".format(downloadedMb)
                        }

                        val statusText = when {
                            state.isCancelledResumable -> "Cancelled — $sizePart saved"
                            state.isPaused -> "Paused — $sizePart"
                            else -> "$sizePart • ${state.formattedSpeed} • ETA ${state.formattedEta}"
                        }

                        val notification = buildNotification(
                            title = if (state.isPaused) "Download paused: ${state.fileName}" else "Downloading ${state.fileName}",
                            text = statusText,
                            progress = state.percent,
                            indeterminate = state.totalBytes <= 0 && !state.isPaused,
                            ongoing = !state.isPaused,
                            isPaused = state.isPaused
                        )
                        notificationManager.notify(NOTIFICATION_ID, notification)

                        if (state.isCancelledResumable) {
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                    }

                    is DownloadState.Completed -> {
                        val doneNotification = NotificationCompat.Builder(this@ModelDownloadService, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("Model installed")
                            .setContentText("${state.file.name} downloaded and ready to load.")
                            .setContentIntent(createOpenAppPendingIntent())
                            .setAutoCancel(true)
                            .setOngoing(false)
                            .build()
                        notificationManager.notify(COMPLETED_NOTIFICATION_ID, doneNotification)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }

                    is DownloadState.Error -> {
                        val errNotification = NotificationCompat.Builder(this@ModelDownloadService, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("Model download failed")
                            .setContentText(state.message)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(state.message))
                            .setContentIntent(createOpenAppPendingIntent())
                            .addAction(
                                0,
                                "Retry",
                                createActionPendingIntent(ACTION_RETRY, 104)
                            )
                            .setAutoCancel(true)
                            .setOngoing(false)
                            .build()
                        notificationManager.notify(COMPLETED_NOTIFICATION_ID, errNotification)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }

                    is DownloadState.Idle -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> downloadManager.pauseDownload()
            ACTION_RESUME, ACTION_RETRY -> downloadManager.resumeDownload()
            ACTION_CANCEL -> downloadManager.cancelDownload(deletePartial = false)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background progress for LiteRT-LM model downloads"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean,
        ongoing: Boolean,
        isPaused: Boolean
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(createOpenAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)

        if (isPaused) {
            builder.addAction(0, "Resume", createActionPendingIntent(ACTION_RESUME, 101))
            builder.addAction(0, "Cancel", createActionPendingIntent(ACTION_CANCEL, 102))
        } else {
            builder.addAction(0, "Pause", createActionPendingIntent(ACTION_PAUSE, 103))
            builder.addAction(0, "Cancel", createActionPendingIntent(ACTION_CANCEL, 102))
        }

        return builder.build()
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(this, 100, intent, piFlags)
    }

    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ModelDownloadService::class.java).apply {
            this.action = action
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getService(this, requestCode, intent, piFlags)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID = 2001
        const val COMPLETED_NOTIFICATION_ID = 2002

        const val ACTION_START = "com.example.util.action.START_DOWNLOAD_SERVICE"
        const val ACTION_PAUSE = "com.example.util.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.example.util.action.RESUME_DOWNLOAD"
        const val ACTION_RETRY = "com.example.util.action.RETRY_DOWNLOAD"
        const val ACTION_CANCEL = "com.example.util.action.CANCEL_DOWNLOAD"

        fun start(context: Context) {
            try {
                val appContext = context.applicationContext
                val intent = Intent(appContext, ModelDownloadService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(appContext, intent)
            } catch (_: Exception) {
                // Ignore service start restriction in unit tests or headless environments
            }
        }
    }
}
