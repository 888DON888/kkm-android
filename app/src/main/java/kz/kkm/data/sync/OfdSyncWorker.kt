package kz.kkm.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kz.kkm.data.repository.ReceiptRepository
import java.util.concurrent.TimeUnit

@HiltWorker
class OfdSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val receiptRepository: ReceiptRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            receiptRepository.retryPendingOfd()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "ofd_sync_periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Periodic every 15 minutes
            val periodic = PeriodicWorkRequestBuilder<OfdSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }

        fun scheduleImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTime = OneTimeWorkRequestBuilder<OfdSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(oneTime)
        }
    }
}
