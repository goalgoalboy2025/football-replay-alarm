package io.github.dandan.kanqiutixing.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dandan.kanqiutixing.data.SettingsRepository
import io.github.dandan.kanqiutixing.data.local.TeamDao
import io.github.dandan.kanqiutixing.data.local.TeamEntity
import io.github.dandan.kanqiutixing.work.WeeklyScheduleWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupWizardUiState(
    val step: Int = 0,
    val allTeams: List<TeamEntity> = emptyList(),
    val selectedTeamIds: Set<Int> = emptySet(),
    val workdayHour: Int = 7,
    val holidayHour: Int = 8
) {
    val totalSteps: Int get() = 4
    val isLastStep: Boolean get() = step == totalSteps - 1

    /** "下一步 / 完成" 按钮是否可点 */
    val canProceed: Boolean
        get() = when (step) {
            0, 1 -> true   // 权限步：哪怕用户拒了也允许跳过——App 能用，只是体验降级
            2 -> selectedTeamIds.isNotEmpty()
            3 -> true
            else -> true
        }
}

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val teamDao: TeamDao
) : ViewModel() {

    private val _internal = MutableStateFlow(SetupWizardUiState())

    val uiState: StateFlow<SetupWizardUiState> = combine(
        _internal,
        teamDao.observeAll()
    ) { internal, teams ->
        internal.copy(allTeams = teams)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SetupWizardUiState())

    fun next() = _internal.update {
        it.copy(step = (it.step + 1).coerceAtMost(it.totalSteps - 1))
    }

    fun previous() = _internal.update {
        it.copy(step = (it.step - 1).coerceAtLeast(0))
    }

    fun toggleTeam(id: Int) = _internal.update {
        val newSet = if (id in it.selectedTeamIds) it.selectedTeamIds - id
        else it.selectedTeamIds + id
        it.copy(selectedTeamIds = newSet)
    }

    fun setWorkdayHour(h: Int) = _internal.update { it.copy(workdayHour = h) }
    fun setHolidayHour(h: Int) = _internal.update { it.copy(holidayHour = h) }

    /**
     * 收尾：把用户选择写入数据库 + 设置项 + onboarding flag，
     * 然后立刻入队一次同步——这样用户进赛程页时大概率已经能看到比赛，
     * 不需要等下个周六 9 点的定时任务。
     */
    fun finish(onComplete: () -> Unit) {
        viewModelScope.launch {
            val state = _internal.value
            state.selectedTeamIds.forEach { id -> teamDao.setEnabled(id, true) }
            settingsRepository.update { s ->
                s.copy(
                    workdayMorningHour = state.workdayHour,
                    holidayMorningHour = state.holidayHour
                )
            }
            settingsRepository.markOnboardingComplete()
            WeeklyScheduleWorker.runOnce(context)
            onComplete()
        }
    }
}
