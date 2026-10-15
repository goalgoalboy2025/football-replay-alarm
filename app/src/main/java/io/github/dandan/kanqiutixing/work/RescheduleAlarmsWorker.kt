package io.github.dandan.kanqiutixing.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.dandan.kanqiutixing.data.MatchRepository
import io.github.dandan.kanqiutixing.notification.MatchReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 开机 / 应用更新后，重新登记"用户已勾选"比赛的精确闹钟。
 *
 * 之所以放进 Worker 而不是直接在 BootReceiver.onReceive 里做：
 * BroadcastReceiver 的执行窗口只有约 10 秒，而 scheduler.schedule() 首次可能触发
 * 节假日数据的网络请求（~20s），在 receiver 里做容易超时被系统杀掉。
 */
@HiltWorker
class RescheduleAlarmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MatchRepository,
    private val scheduler: MatchReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val selected = repository.getSelectedMatches()
            selected.forEach { scheduler.schedule(it) }
            Log.i(TAG, "开机重排闹钟完成：已选 ${selected.size} 场比赛")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "开机重排闹钟失败：${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "KanqiuReschedule"
        private const val UNIQUE_NAME = "reschedule_alarms_worker"

        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<RescheduleAlarmsWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req
            )
        }
    }
}
