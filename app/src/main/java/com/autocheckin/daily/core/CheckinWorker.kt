package com.autocheckin.daily.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class CheckinWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            CheckinExecutor.runAll(ctx, force = false)
        } catch (e: Exception) {
            // never crash the worker; failures are recorded per-account
        }
        CheckinScheduler.scheduleNext(ctx)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "daily_checkin"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CheckinWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
