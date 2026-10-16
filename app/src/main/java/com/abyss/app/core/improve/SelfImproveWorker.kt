package com.abyss.app.core.improve

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.abyss.app.core.ServiceLocator
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs in the background (even with the app closed): harvests new knowledge
 * from external models when online, and always runs offline self-reflection.
 * Both are gated by the self-improvement toggle.
 */
class SelfImproveWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = ServiceLocator.settings.settings.first()
        if (!settings.backgroundHarvest) return Result.success()

        runCatching { ServiceLocator.harvester.runOnce(settings.harvestPerRun) }
        runCatching { ServiceLocator.reflection.runOnce() }
        return Result.success()
    }
}

object HarvestScheduler {
    private const val WORK = "abyss_self_improve"

    fun schedule(context: Context, intervalHours: Int) {
        val request = PeriodicWorkRequestBuilder<SelfImproveWorker>(
            intervalHours.coerceIn(1, 24).toLong(), TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}
