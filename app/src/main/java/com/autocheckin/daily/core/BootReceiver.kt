package com.autocheckin.daily.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.autocheckin.daily.data.Repository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = Repository(context)
        if (repo.serviceEnabled) {
            try {
                val svc = Intent(context, CheckinService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc)
                } else {
                    context.startService(svc)
                }
            } catch (e: Exception) {
                // ignore
            }
            CheckinScheduler.scheduleNext(context)
        }
    }
}
