package com.andloading.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.andloading.assistant.AssistantApp
import com.andloading.assistant.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {

    private var scheduleJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scheduleJob?.cancel()
        scheduleJob = null
        FloatingWindowService.stop(this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        ensureScheduleLoop()
        return START_STICKY
    }

    private fun ensureScheduleLoop() {
        if (scheduleJob?.isActive == true) return
        val app = application as? AssistantApp ?: return
        scheduleJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (isActive) {
                val realtime = app.settings.autoEnabled && app.settings.proxyRealtimeEnabled
                if (realtime) {
                    delay(app.settings.proxyRealtimeSeconds * 1000L)
                    app.proxyRunner.onRealtimeTick()
                } else {
                    delay(app.settings.proxyIntervalMinutes * 60_000L)
                    if (app.settings.autoEnabled) app.proxyRunner.onSchedule()
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (FloatingWindowService.isRunning ||
            (applicationContext as? AssistantApp)?.settings?.keepAliveFloating == true
        ) {
            runCatching { start(this) }
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AssistantForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AssistantApp.CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_running))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.andloading.assistant.action.STOP_ASSISTANT"

        fun start(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantForegroundService::class.java))
        }
    }
}
