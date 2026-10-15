package io.github.dandan.kanqiutixing.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dandan.kanqiutixing.data.Settings
import io.github.dandan.kanqiutixing.data.SettingsRepository
import io.github.dandan.kanqiutixing.data.config.TeamConfigLoader
import io.github.dandan.kanqiutixing.data.local.TeamDao
import io.github.dandan.kanqiutixing.data.local.TeamEntity
import io.github.dandan.kanqiutixing.notification.MatchReminderScheduler
import io.github.dandan.kanqiutixing.notification.NotificationHelper
import io.github.dandan.kanqiutixing.notification.ReminderDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: Settings = Settings(true, true, true, true, true, 7, 8),
    val teams: List<TeamEntity> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val teamDao: TeamDao,
    private val teamConfigLoader: TeamConfigLoader,
    private val notificationHelper: NotificationHelper,
    private val matchReminderScheduler: MatchReminderScheduler,
    private val reminderDiagnostics: ReminderDiagnostics
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.flow, teamDao.observeAll()) { s, t ->
            SettingsUiState(s, t)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    /** 提醒健康检查：总判定 */
    private val _verdict = MutableStateFlow("检查中…")
    val verdict: StateFlow<String> = _verdict

    /** 提醒健康检查：明细 */
    private val _diagnostics = MutableStateFlow("读取中…")
    val diagnostics: StateFlow<String> = _diagnostics

    init {
        refreshDiagnostics()
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _verdict.value = reminderDiagnostics.verdict()
            _diagnostics.value = reminderDiagnostics.text()
        }
    }

    /** 一次性提示消息（Snackbar） */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun toggleOfficial(v: Boolean) = updateSettings { it.copy(enableOfficial = v) }
    fun toggleQualifiers(v: Boolean) = updateSettings { it.copy(enableQualifiers = v) }
    fun toggleFriendly(v: Boolean) = updateSettings { it.copy(enableFriendly = v) }
    fun toggleLiveReminder(v: Boolean) = updateSettings { it.copy(enableLiveReminder = v) }
    fun toggleReplayReminder(v: Boolean) = updateSettings { it.copy(enableReplayReminder = v) }
    fun setWorkdayHour(h: Int) = updateSettings { it.copy(workdayMorningHour = h) }
    fun setHolidayHour(h: Int) = updateSettings { it.copy(holidayMorningHour = h) }

    fun toggleTeamEnabled(team: TeamEntity) {
        viewModelScope.launch { teamDao.setEnabled(team.id, !team.enabled) }
    }

    fun resyncTeams() {
        viewModelScope.launch { teamConfigLoader.syncTeams() }
    }

    /** 立即弹一条测试通知：验证通知权限 + 通知渠道 */
    fun testNotificationNow() {
        if (!notificationHelper.hasNotificationPermission()) {
            // 没有 POST_NOTIFICATIONS 权限时 notify() 会被系统静默丢弃，
            // 别误报"已发送"，明确告诉用户去开权限
            _messages.tryEmit("通知权限未开启，请到系统设置里允许本 App 发送通知后再测试")
            return
        }
        notificationHelper.notifyTest(delayed = false)
        _messages.tryEmit("已发送测试通知，看通知栏是否出现")
    }

    /** 设一个 2 分钟后的测试闹钟：验证 AlarmManager + 后台保活 */
    fun testNotificationDelayed() {
        if (!notificationHelper.hasNotificationPermission()) {
            _messages.tryEmit("通知权限未开启，请先到系统设置里允许本 App 发送通知")
            return
        }
        matchReminderScheduler.scheduleTestAlarm(minutesFromNow = 2)
        val hint = if (matchReminderScheduler.canScheduleExactAlarms()) {
            "测试提醒已设置，约 2 分钟后弹出。现在可以把 App 从后台划掉，看它还能不能准时响"
        } else {
            "测试提醒已设置（约 2 分钟后）。注意：精确闹钟权限未开，可能会延迟几分钟"
        }
        _messages.tryEmit(hint)
    }

    private fun updateSettings(t: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepository.update(t) }
    }
}
