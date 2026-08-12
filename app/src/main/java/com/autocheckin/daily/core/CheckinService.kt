package com.autocheckin.daily.core

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.autocheckin.daily.App
import com.autocheckin.daily.MainActivity
import com.autocheckin.daily.R
import com.autocheckin.daily.data.Repository

class CheckinService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(App.NOTIFICATION_ID, buildNotification())
        CheckinScheduler.scheduleNext(this)
        return START_STICKY
    }

    override fun onDestroy() {
        val repo = Repository(this)
        if (repo.serviceEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, CheckinService::class.java))
                } else {
                    startService(Intent(this, CheckinService::class.java))
                }
            } catch (e: Exception) {
                // background start restrictions; boot receiver / next launch will restart
            }
        }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val repo = Repository(this)
        val text = if (repo.scheduleEnabled) {
            "下次自动签到: ${repo.scheduleText()}"
        } else {
            "定时签到已停用"
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("自动签到运行中")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
