package com.prithvi.northstar.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prithvi.northstar.data.DiscoverySync

class DiscoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        DiscoverySync.refresh(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
