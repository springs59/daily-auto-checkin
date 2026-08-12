package com.autocheckin.daily.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CheckinAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CheckinWorker.enqueue(context)
    }
}
