package io.github.dandan.kanqiutixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dandan.kanqiutixing.data.SettingsRepository
import io.github.dandan.kanqiutixing.data.local.TeamDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App 启动时决定 NavHost 的起点路由。
 *
 * 三种情形：
 *  1. 已 mark onboarding_done → 进 schedule
 *  2. flag 为 false 但 DB 里已经有 enabled 球队 → 视为 v0.1 升级用户，
 *     顺手把 flag 补上、进 schedule（不再打扰他们走新手向导）
 *  3. 其余 → 进 setup_wizard
 *
 * 期间 `startDestination = null`，MainActivity 显示一个 spinner 占位。
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val teamDao: TeamDao
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            if (settingsRepository.hasCompletedOnboarding()) {
                _startDestination.value = ROUTE_SCHEDULE
                return@launch
            }
            // 没有 flag。看看是不是 v0.1 升级上来的用户（DB 里已有 enabled 球队）
            val anyEnabled = teamDao.getEnabled().isNotEmpty()
            if (anyEnabled) {
                settingsRepository.markOnboardingComplete()
                _startDestination.value = ROUTE_SCHEDULE
            } else {
                _startDestination.value = ROUTE_SETUP_WIZARD
            }
        }
    }

    companion object {
        const val ROUTE_SCHEDULE = "schedule"
        const val ROUTE_SETUP_WIZARD = "setup_wizard"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_MAGICOS_GUIDE = "magicos_guide"
    }
}
