package com.ta3.downloader

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh of the JOJ archive's show-list cache (see [JojShowCache]) — runs
 * on its own schedule regardless of whether the user ever opens the JOJ tab, so the cache is
 * rarely more than [JojShowCache.MAX_AGE_MS] stale by the time they do.
 */
class JojShowsRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val cached = JojShowCache.load(applicationContext)
            if (cached != null && !JojShowCache.isStale(cached)) return Result.success()
            val shows = JojScraper.fetchShows()
            JojShowCache.save(applicationContext, shows)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "joj_shows_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<JojShowsRefreshWorker>(
                JojShowCache.MAX_AGE_MS, TimeUnit.MILLISECONDS
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
