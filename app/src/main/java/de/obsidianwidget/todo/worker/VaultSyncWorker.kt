package de.obsidianwidget.todo.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.obsidianwidget.todo.data.TaskRepository
import java.util.concurrent.TimeUnit

class VaultSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        TaskRepository.syncVault(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "obsidian_vault_periodic_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<VaultSyncWorker>(
                30, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }
}
