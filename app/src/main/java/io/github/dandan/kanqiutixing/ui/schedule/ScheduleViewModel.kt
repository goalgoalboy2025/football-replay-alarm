package io.github.dandan.kanqiutixing.ui.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.dandan.kanqiutixing.data.MatchRepository
import io.github.dandan.kanqiutixing.data.local.MatchEntity
import io.github.dandan.kanqiutixing.data.local.TeamDao
import io.github.dandan.kanqiutixing.data.local.TeamEntity
import io.github.dandan.kanqiutixing.notification.MatchReminderScheduler
import io.github.dandan.kanqiutixing.work.WeeklyScheduleWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class ScheduleUiState(
    val matches: List<MatchEntity> = emptyList(),
    val teamMap: Map<Int, TeamEntity> = emptyMap()
)

/** 手动刷新的状态，用于给刷新按钮做反馈 */
enum class SyncState { Idle, Syncing, Success, Failed }

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repo: MatchRepository,
    private val teamDao: TeamDao,
    private val scheduler: MatchReminderScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val workManager = WorkManager.getInstance(context)

    /**
     * 按"自然日"刷新的信号流：立即发一次今天，之后在每个午夜再发一次。
     * 用它驱动赛程窗口，避免 App 进程跨午夜还活着时窗口停在旧的一天。
     */
    private val dayTicker = flow {
        while (true) {
            val today = LocalDate.now(zone)
            emit(today)
            val nextMidnight = today.plusDays(1).atStartOfDay(zone)
            val delayMs = Duration.between(ZonedDateTime.now(zone), nextMidnight).toMillis()
            delay(delayMs.coerceAtLeast(1_000L))
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ScheduleUiState> =
        dayTicker.flatMapLatest { today ->
            val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.plusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
            combine(
                repo.observeMatches(from, to),
                teamDao.observeAll()
            ) { matches, teams ->
                ScheduleUiState(
                    matches = matches,
                    teamMap = teams.associateBy { it.id }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUiState())

    /**
     * 观察手动同步任务的状态，驱动刷新按钮的转圈和结果提示。
     * 用 any{} 而不是 firstOrNull()：REPLACE 策略下可能同时存在
     * 被取消的旧任务 + 新任务，要看"有没有正在跑的"，而不是看第一条。
     */
    val syncState: StateFlow<SyncState> =
        workManager.getWorkInfosForUniqueWorkFlow(WeeklyScheduleWorker.MANUAL_SYNC_NAME)
            .map { infos ->
                when {
                    infos.any { it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.ENQUEUED } -> SyncState.Syncing
                    infos.any { it.state == WorkInfo.State.FAILED } -> SyncState.Failed
                    infos.any { it.state == WorkInfo.State.SUCCEEDED } -> SyncState.Success
                    else -> SyncState.Idle
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncState.Idle)

    /** 一次性提示消息（Snackbar），用于"确认提醒选择"的结果反馈 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun syncNow() {
        WeeklyScheduleWorker.runOnce(context)
    }

    /**
     * 用户在赛程页勾选若干比赛后点"确认"：
     *  1. 把勾选集合持久化到数据库（reminderEnabled）
     *  2. 取消所有旧闹钟，按新的勾选集合重新排提醒
     */
    fun confirmSelection(checkedIds: Set<Long>) {
        viewModelScope.launch {
            repo.applyReminderSelection(checkedIds)
            val selected = repo.getSelectedMatches()
            scheduler.cancelAllPending()
            selected.forEach { scheduler.schedule(it) }
            _messages.tryEmit(
                if (selected.isEmpty()) "已清空提醒，当前没有选中的比赛"
                else "已为 ${selected.size} 场比赛设置提醒"
            )
        }
    }
}
