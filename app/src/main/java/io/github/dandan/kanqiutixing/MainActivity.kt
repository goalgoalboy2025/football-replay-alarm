package io.github.dandan.kanqiutixing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.dandan.kanqiutixing.ui.StartupViewModel
import io.github.dandan.kanqiutixing.ui.onboarding.MagicOsGuideScreen
import io.github.dandan.kanqiutixing.ui.schedule.ScheduleScreen
import io.github.dandan.kanqiutixing.ui.settings.SettingsScreen
import io.github.dandan.kanqiutixing.ui.setup.SetupWizardScreen
import io.github.dandan.kanqiutixing.ui.theme.KanqiuTheme

/**
 * v1 之后 MainActivity 只负责拉起 Compose 内容；
 * 通知权限 / 精确闹钟权限的引导都搬进了 SetupWizardScreen，
 * 不再在 onCreate 里自动弹系统对话框（那样体验突兀）。
 *
 * 路由起点由 StartupViewModel 决定：
 *   - 已 onboarding_done 或 v0.1 升级用户 → schedule
 *   - 其余 → setup_wizard
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KanqiuTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(startupVm: StartupViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val startDestination by startupVm.startDestination.collectAsState()

    // StartupViewModel 还在算（异步查 DataStore + DB）→ 给个 spinner 顶住，
    // 避免先闪一下 schedule 又跳到 wizard 之类的坏体验
    val dest = startDestination ?: run {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        return
    }

    NavHost(navController = nav, startDestination = dest) {
        composable(StartupViewModel.ROUTE_SETUP_WIZARD) {
            SetupWizardScreen(
                onComplete = {
                    // 走完向导直接替换到 schedule，并把 wizard 从回退栈里清掉，
                    // 用户按返回键不会回到向导
                    nav.navigate(StartupViewModel.ROUTE_SCHEDULE) {
                        popUpTo(StartupViewModel.ROUTE_SETUP_WIZARD) { inclusive = true }
                    }
                }
            )
        }
        composable(StartupViewModel.ROUTE_SCHEDULE) {
            ScheduleScreen(
                onOpenSettings = { nav.navigate(StartupViewModel.ROUTE_SETTINGS) }
            )
        }
        composable(StartupViewModel.ROUTE_SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenOnboarding = { nav.navigate(StartupViewModel.ROUTE_MAGICOS_GUIDE) }
            )
        }
        composable(StartupViewModel.ROUTE_MAGICOS_GUIDE) {
            MagicOsGuideScreen(onBack = { nav.popBackStack() })
        }
    }
}
