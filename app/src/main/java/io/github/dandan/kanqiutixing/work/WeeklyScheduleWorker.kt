package io.github.dandan.kanqiutixing.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import io.github.dandan.kanqiutixing.data.MatchRepository
import io.github.dandan.kanqiutixing.data.SyncStatusStore
import io.github.dandan.kanqiutixing.data.local.MatchEntity
import io.github.dandan.kanqiutixing.data.remote.FREE_TIER_COMPETITIONS
import io.github.dandan.kanqiutixing.notification.MatchReminderScheduler
import io.github.dandan.kanqiutixing.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

@HiltWorker
class WeeklyScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MatchRepository,
    private val scheduler: MatchReminderScheduler,
    private val notifier: NotificationHelper,
    private val syncStatus: SyncStatusStore
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "weekly_schedule_worker"
        const val MANUAL_SYNC_NAME = "manual_schedule_sync"
        private const val TAG = "KanqiuWorker"

        /**
         * 两次 API 请求之间的间隔。
         * 改用按联赛查后只有 8 个请求，远低于 football-data.org 免费档 10 次/分钟限速，
         * 3 秒间隔已足够安全，整次同步约 20 多秒。
         */
        private const val REQUEST_INTERVAL_MS = 3_000L

        fun enqueue(context: Context) {
            val now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
            val nextSat9 = now
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                .withHour(9).withMinute(0).withSecond(0).withNano(0)
                .let { if (it.isBefore(now)) it.plusWeeks(1) else it }
            val initialDelaySec = Duration.between(now, nextSat9).seconds

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WeeklyScheduleWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(initialDelaySec, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun runOnce(context: Context) {
            val req = OneTimeWorkRequestBuilder<WeeklyScheduleWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // 用唯一任务 + REPLACE：点刷新时，把上一次可能卡住/没跑完的同步取消掉，
            // 重新来一次。保证用户永远能强制触发一次干净的同步，不会被"卡死的旧任务"困住。
            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_SYNC_NAME, ExistingWorkPolicy.REPLACE, req
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val zone = ZoneId.of("Asia/Shanghai")
            val today = LocalDate.now(zone)
            val from = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val to = today.plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)

            val teams = repository.getEnabledTeams()
            val followedTeamIds = teams.map { it.id }.toSet()
            Log.i(TAG, "开始同步：关注球队 ${teams.size} 支，按 ${FREE_TIER_COMPETITIONS.size} 个联赛拉取，日期 $from ~ $to")
            if (teams.isEmpty()) {
                Log.w(TAG, "启用球队为 0 —— teams.json 没同步进数据库？检查 TeamConfigLoader")
            }

            // 关键：先把所有比赛拉成功再 cancel + reschedule，
            // 中途任何失败都不会动到现有 alarm，避免"网络抖动 → 所有提醒消失"
            val all = mutableListOf<MatchEntity>()
            FREE_TIER_COMPETITIONS.forEachIndexed { i, code ->
                try {
                    val matches = repository.fetchAndCacheCompetitionMatches(
                        code, followedTeamIds, from, to
                    )
                    Log.i(TAG, "  联赛 $code → 命中关注球队 ${matches.size} 场")
                    all.addAll(matches)
                } catch (e: HttpException) {
                    // 429 限速 → 抛出去让整个同步重试；
                    // 其他错误（如某赛事休赛期 404）→ 跳过这个联赛，不拖垮整次同步
                    if (e.code() == 429) throw e
                    Log.w(TAG, "  联赛 $code 拉取失败 HTTP ${e.code()}，跳过")
                }
                // 控制请求频率（8 个请求，3 秒间隔，远在 10 次/分钟限速内）
                if (i < FREE_TIER_COMPETITIONS.lastIndex) delay(REQUEST_INTERVAL_MS)
            }

            val unique = all.distinctBy { it.id }
            Log.i(TAG, "拉取完成：去重后共 ${unique.size} 场比赛")

            // fetch 全部成功后，再原子替换：cancel 旧 → 只给"用户已勾选"的比赛排提醒。
            // 新拉到的比赛默认 reminderEnabled=false，需要用户在赛程页勾选+确认才会进提醒队列。
            scheduler.cancelAllPending()
            val selected = repository.getSelectedMatches()
            selected.forEach { scheduler.schedule(it) }

            // 清理 30 天前的旧数据
            repository.cleanupOld(System.currentTimeMillis() - 30L * 24 * 3600_000)

            notifier.notifyWeeklyDigest(unique.size)
            syncStatus.record(unique.size, selected.size, null)
            Log.i(TAG, "同步成功：共 ${unique.size} 场比赛，其中已选 ${selected.size} 场已排提醒")
            Result.success()
        } catch (e: HttpException) {
            // football-data.org 错误码：
            //   400 请求格式不对 / 403 Key 错或越权 / 429 短时间内 req 过多 / 5xx 服务端
            Log.e(TAG, "HTTP 错误 ${e.code()}：${e.message()}", e)
            syncStatus.record(0, 0, "HTTP ${e.code()}（${httpHint(e.code())}）")
            when (e.code()) {
                400, 403 -> Result.failure()                  // 客户端配置错误，重试无意义
                429 -> Result.retry()                         // 限速触发，靠 WorkManager backoff
                in 500..599 -> Result.retry()                 // 服务端故障
                else -> if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: IOException) {
            // 网络 IO 异常（断网、超时）→ 重试
            Log.e(TAG, "网络 IO 异常（断网/超时/DNS）：${e.message}", e)
            syncStatus.record(0, 0, "网络异常：${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "未预期异常：${e.javaClass.simpleName} ${e.message}", e)
            syncStatus.record(0, 0, "${e.javaClass.simpleName}：${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun httpHint(code: Int) = when (code) {
        400 -> "请求格式不对"
        403 -> "API token 无效或越权，检查 local.properties 里的 FOOTBALL_DATA_TOKEN"
        429 -> "请求太频繁，被限速"
        in 500..599 -> "数据源服务端故障"
        else -> "未知"
    }
}
