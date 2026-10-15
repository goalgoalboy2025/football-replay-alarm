package io.github.dandan.kanqiutixing.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dandan.kanqiutixing.data.Settings
import io.github.dandan.kanqiutixing.data.SettingsRepository
import io.github.dandan.kanqiutixing.data.TokenProvider
import io.github.dandan.kanqiutixing.data.TokenSource
import io.github.dandan.kanqiutixing.data.config.TeamConfigLoader
import io.github.dandan.kanqiutixing.data.local.TeamDao
import io.github.dandan.kanqiutixing.data.local.TeamEntity
import io.github.dandan.kanqiutixing.data.remote.FootballDataOrgApi
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
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
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
    private val reminderDiagnostics: ReminderDiagnostics,
    private val tokenProvider: TokenProvider,
    private val api: FootballDataOrgApi
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

    // ── 数据源 token（双轨制：用户在 App 内填的优先于构建期注入的）──────────

    /** 输入框内容 */
    private val _tokenInput = MutableStateFlow("")
    val tokenInput: StateFlow<String> = _tokenInput

    /** 当前实际生效的 token 来自哪里，用于给用户一句人话说明 */
    private val _tokenSource = MutableStateFlow(TokenSource.NONE)
    val tokenSource: StateFlow<TokenSource> = _tokenSource

    /** 测试连接的结果文案；null = 还没测过 */
    private val _tokenTestResult = MutableStateFlow<String?>(null)
    val tokenTestResult: StateFlow<String?> = _tokenTestResult

    private val _tokenTesting = MutableStateFlow(false)
    val tokenTesting: StateFlow<Boolean> = _tokenTesting

    init {
        refreshDiagnostics()
        viewModelScope.launch {
            _tokenInput.value = settingsRepository.getApiToken()
            _tokenSource.value = tokenProvider.resolve().source
        }
    }

    fun onTokenInputChange(v: String) {
        _tokenInput.value = v
        _tokenTestResult.value = null
    }

    /**
     * 用输入框里的 token 真的打一次 API，验证通过才保存。
     *
     * 先测后存，是为了避免用户手滑粘错字符后：App 静默存了个坏 token，
     * 之后每次同步都 403，而用户完全不知道问题出在哪。
     */
    fun testAndSaveToken() {
        val raw = _tokenInput.value.trim()
        if (raw.isBlank()) {
            _tokenTestResult.value = "请先填入 token"
            return
        }
        viewModelScope.launch {
            _tokenTesting.value = true
            _tokenTestResult.value = "正在连接 football-data.org …"
            try {
                val today = LocalDate.now()
                val resp = api.getCompetitionMatches(
                    competitionCode = "PL",
                    dateFrom = today.toString(),
                    dateTo = today.plusDays(7).toString(),
                    token = raw
                )
                settingsRepository.setApiToken(raw)
                _tokenSource.value = TokenSource.USER
                _tokenTestResult.value =
                    "连接成功。未来 7 天英超有 ${resp.matches.size} 场比赛，token 已保存"
            } catch (e: HttpException) {
                _tokenTestResult.value = when (e.code()) {
                    400 -> "请求被拒（400）：token 格式不对，请检查是否复制完整"
                    403 -> "token 无效（403）：请确认复制正确，且注册邮箱已完成验证"
                    429 -> "请求太频繁（429）：免费档限 10 次/分钟，请稍等一分钟再试"
                    else -> "连接失败：HTTP ${e.code()}"
                }
            } catch (e: IOException) {
                _tokenTestResult.value = "网络不通：${e.message ?: "请检查网络后重试"}"
            } catch (e: Exception) {
                _tokenTestResult.value = "连接失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                _tokenTesting.value = false
            }
        }
    }

    /** 清空用户填写的 token，回退到构建期注入的值（本地开发版通常才有） */
    fun clearToken() {
        viewModelScope.launch {
            settingsRepository.setApiToken("")
            _tokenInput.value = ""
            _tokenSource.value = tokenProvider.resolve().source
            _tokenTestResult.value = "已清除，现在使用 App 内置的 token"
        }
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
