# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.1] — 2026-10-10

借鉴「吧嗒清单」（com.bada.app）已真机验证通过的提醒实现，加固提醒链路。
那一版曾出现过「后台不响」的排查：服务端回执显示手机其实准时弹了通知，用户却以为没响——
**闹钟是对的，感知不到才是真问题**。下面 1、4 两条主要就是针对这一点。

### 新增

- **提醒健康检查页**（设置页内，`notification/ReminderDiagnostics.kt`）：不连电脑就能看到
  版本号 / 上次同步时间与结果 / 通知权限 / 精确闹钟权限 / 电池优化白名单 /
  三个通知渠道的系统实际状态（重要度、有没有声音、能不能绕过免打扰）/
  已挂闹钟数量及最近 5 个触发时间。配「刷新 / 同步 / 电池」三个按钮。
- **电池优化白名单快捷入口**：设置页「电池」按钮直接跳系统授权页，
  国产 ROM 不加白名单会冻结后台进程，是「后台不响」最常见的原因。
- **同步状态记录**（`data/SyncStatusStore.kt`）：每次同步写入时间、拉到几场、排了几个闹钟、失败原因，
  供健康检查页展示。以前这些信息只存在于 logcat 里。
- **录播提醒补弹**：触发点已过但比赛早已结束（多半是 WorkManager 被国产 ROM 压后导致同步来晚）时，
  24 小时内补弹一次并标记，避免「明明设了提醒却没响」。直播提醒过了就没意义，不补。

### 修复

- **真实比赛提醒在隔夜后不触发（2 分钟测试却正常）**：根因是真实提醒用 `setExactAndAllowWhileIdle` 登记长延迟闹钟，在荣耀 MagicOS 等激进省电 ROM 上，App 熄屏一整夜被"冻结/深度睡眠"后，闹钟到点送不出来；而 2 分钟测试是刚从前台点完按钮、延迟又短，系统来不及冻结，所以一直能响，掩盖了问题。
  - 改为用 `AlarmManager.setAlarmClock()` 登记提醒——这是 Android 里唯一被系统及各家 OEM ROM 当作"用户闹钟"对待、能穿透 Doze + 应用待机 + 厂商冻结按时送达的闹钟类型（系统时钟 App 用的就是它）。
  - 测试闹钟改走同一条 `setAlarmClock` 路径，使"2 分钟测试"的结果真正代表真实提醒的送达能力。
  - 无精确闹钟权限时退回 `setAndAllowWhileIdle` 并打 WARN 日志（正常机型不会走到，因已声明 `USE_EXACT_ALARM`）。
  - 行为变化：设置提醒后状态栏会出现系统闹钟图标、锁屏可见"下一个闹钟"——对一个"看球闹钟"是符合预期的。
- **通知太安静，容易被 ROM 吞掉**：录播提醒渠道原来用 `IMPORTANCE_DEFAULT`（三个渠道里最安静的，
  而它恰恰是早晨睡梦中最需要响的场景）→ 三个渠道统一 `IMPORTANCE_HIGH`；
  通知补上显式铃声（USAGE_ALARM）、震动、`CATEGORY_ALARM`、锁屏可见、绕过免打扰。
  渠道默认声音在荣耀上经常是空的，不能指望默认值。
- **老渠道不会补声音**：渠道一旦建成就改不了重要度，但声音/震动/免打扰可以改。
  现在每次启动都补一遍，避免「装了新包还是静默通知」。
- **`setAlarmClock` 没有 try/catch**：少数 ROM 对它有限制会抛异常，不接住的话闹钟压根没挂上，
  而且没有任何日志。改为三级降级：setAlarmClock → setExactAndAllowWhileIdle → setAndAllowWhileIdle。
- **改时间/时区不重排闹钟**：`BootReceiver` 补上 `TIME_SET` / `TIMEZONE_CHANGED` / `DATE_CHANGED` 三个广播。
  闹钟登记的是绝对时间戳，时区一变「早晨 7 点」就不再是原来那个时刻。

### 变更

- `versionCode` 2 → 3，`versionName` "1.0.0" → "1.0.1"（版本号是排查「装的是不是新包」的第一步）
- `MatchReminderScheduler` 新增 `NotificationHelper` 依赖（补弹用）
- `NotificationDao` 新增 `countFired()` / `getAllPendingSorted()`

## [1.1.0] — 2026-10-15

为公开发布做准备：数据源 token 不再随 APK 分发，同时修掉几个会让人"以为 App 坏了"的问题。

### 新增

- **数据源 token 双轨制**（`data/TokenProvider.kt`）：用户在 设置 → 数据源 填的 token 优先，
  没填才回退构建期注入的 `BuildConfig.FOOTBALL_DATA_TOKEN`。
  - 开发者：把 token 写进 `local.properties`，clone 下来就能跑，不用先注册账号
  - 发布者：`local.properties` 留空后构建，APK 里不含任何 token，配额归使用者自己
- **设置页「数据源」分区**：token 输入框（密码遮挡，避免截图时泄露）+ 去注册 +
  **测试并保存**——先真打一次 API，通过才写入，避免用户粘错字符后 App 静默存个坏 token、
  之后每次同步都 403 却不知道问题在哪。
- **构建期 token 泄露告警**（`app/build.gradle.kts`）：构建 release 且 `local.properties`
  里有非空 token 时打印醒目警告，提醒清空后再出对外分发的包。

### 修复

- **核实全部 18 个 team id**：`teams.json` 里此前标注"未核实"的
  `4/5/73/98/108/109/113/516/524`（拜仁、多特、热刺、AC米兰、国米、尤文、那不勒斯、马赛、巴黎）
  经 2026-10-15 对照 API 实际响应逐一核验，**18/18 全部正确**。
  并做了端到端验证：拉取当周六个联赛赛程，18 支球队全部能匹配到比赛。
  这条此前错了会导致这些队完全抓不到比赛，而用户毫无感知——只是"怎么利物浦提醒了、热刺没有"。
- **空 token 时同步会白重试 3 次**：`MissingTokenException` 现在直接失败并把原因写进同步状态，
  让健康检查页显示"未配置数据源 token：请在 设置 → 数据源 填入"，而不是一个含糊的"同步失败"。
- **CI 会跑不起来**：`gradle-wrapper.properties` 的 `distributionUrl` 指回官方源
  （此前指向腾讯云镜像，GitHub Actions 的美国 runner 会超时），`settings.gradle.kts`
  移除 4 处阿里云 maven 镜像（镜像只对这台机器有意义，不应进仓库拖慢所有人的 CI）。
- 403 错误提示从"检查 local.properties"改为"请在设置页重新填入"——发布后的用户根本没有 local.properties。

### 变更

- `versionCode` 3 → 4，`versionName` "1.0.1" → "1.1.0"
- `teams.json` `version` 3 → 4，note 字段改写为"已核实"并记录端到端验证结论
- `MatchRepository` 新增 `TokenProvider` 依赖，不再直接读 `BuildConfig.FOOTBALL_DATA_TOKEN`

## [1.0.0] — 2026-05-17

第一个公开发布版本。从"自用 v0.1"重构为"可以丢给朋友装"的状态。

### 新增

- **首启动 4 步向导**（`ui/setup/SetupWizardScreen.kt`）
  1. 通知权限引导（Android 13+ 才需要）
  2. 精确闹钟权限引导（Android 12+ 才需要）
  3. 18 支俱乐部按联赛分组选择，至少 1 支才能继续
  4. 录播提醒时间自定义，工作日 / 节假日两个滑杆，范围 **5:00 — 12:00**
- 向导完成后立即触发首次同步，进赛程页有"正在加载本周赛程…"加载态
- `StartupViewModel` 决定起点路由，自动识别"v0.1 已配置好的老用户"跳过向导
- `RescheduleAlarmsWorker`：把开机重排闹钟工作从 `BootReceiver.goAsync()` 搬到 WorkManager，避开 ~10 秒窗口
- 完整的可观测性日志：`KanqiuAlarm` / `KanqiuScheduler` / `KanqiuNotify` 三个 tag 覆盖闹钟全链路
- v1 视觉 mockup（`preview/ui_mockup.html`，UCL 风格）
- 4 个 Composable `@Preview` 注解（在 AS 里可直接看 wizard 各步骤）
- 项目级文件：`LICENSE`（MIT）、`PRIVACY.md`、`README.md`、`signing.properties.template`、`CHANGELOG.md`、`RELEASE_CHECKLIST.md`
- GitHub Actions CI（debug 构建在每次 push 时自动跑）

### 修复

- **`MatchAlarmReceiver` / `BootReceiver` 没调 `super.onReceive()`**：导致 Hilt 注入永远不发生，闹钟一响就崩，通知被系统吞掉。这是 v0.1 后期"明明设置了提醒却没收到"的根因
- `CompetitionTypeMapper.classify` 重复分支清理，签名简化为 `classify(type)`
- `ScheduleViewModel.uiState` 的日期窗口在 ViewModel 构造时计算一次，跨午夜不会推进；改成 `flatMapLatest` + 按日刷新的 ticker
- `ChineseHolidayHelper.attemptedYears` 改成 `ConcurrentHashMap.newKeySet`，避免并发请求重复触发同一年的网络调用
- 测试通知不查 `POST_NOTIFICATIONS` 权限就报"已发送"误导用户

### 变更

- **包名**：`com.example.kanqiutixing` → `io.github.dandan.kanqiutixing`
- **应用名**：`看球提醒` → `看球不熬夜闹钟`
- `versionCode` 1 → 2，`versionName` "1.0" → "1.0.0"
- 默认关注球队：14 支（10 俱乐部 + 4 国家队）→ **18 支俱乐部，无国家队**
  - 新增：热刺、多特蒙德、勒沃库森、AC 米兰、国际米兰、尤文图斯、那不勒斯、马赛
  - 移除：英格兰队、德国队、法国队、葡萄牙队
- 短名调整：`巴塞罗那 → 巴萨`、`皇家马德里 → 皇马`、`马德里竞技 → 马竞`、`托特纳姆热刺 → 热刺`
- `FREE_TIER_COMPETITIONS` 由 8 个 code 减到 6 个：去掉 `EC` / `WC`（国家队赛事），保留五大联赛 + 欧冠
- `TeamConfigLoader` 新球队默认 `enabled=false`（v0.1 是 `true`），配合首启动向导让用户显式选择
- `SettingsScreen` 早晨时间滑杆范围 `5..11` → `5..12`
- `ScheduleScreen` 空状态区分"首次同步加载中"和"暂无未来 7 天比赛"两种情形
- `MainActivity` 简化：不再 onCreate 里主动弹权限框，统一交给向导
- `OnboardingScreen.kt` → `MagicOsGuideScreen.kt`（文件名 + 函数名），避免和"首启动向导"概念混淆
- `app/build.gradle.kts`：加 `signingConfigs.release`，从 `signing.properties` 读密码（gitignored）；去掉 v0.1 开发期的 `rotateLauncherIconColor` 任务

### 已知限制（v1.0 故意保留）

- **足总杯 / 各国域内杯赛 / 欧联杯 抓不到** —— football-data.org 免费档不覆盖。文档已在 README 里明确告知
- `FOOTBALL_DATA_TOKEN` 烧在 BuildConfig 跟 APK 走 —— v1 给朋友圈用的妥协，APK 不要让陌生人拿到。**已在 1.1.0 改为双轨制**：发布的 APK 不含 token，用户填自己的
- 设置页"资格赛 / 友谊赛"两个开关在当前数据源下不会影响任何比赛（free tier 的 competition.type 永远是 `LEAGUE` / `CUP`，不会归类为 QUALIFIERS / FRIENDLY）。代码保留以备未来数据源升级
- 没有 release 签名 keystore（开发者自己生成，见 README）

## [0.1.0-personal] — 2026-05-13

自用初版。功能基本完整但只在作者一台荣耀手机上跑过，含开发期工件。完整快照保存在 `kanqiutixing-v0.1-personal/` 目录里，作为永久回退基线，不再维护。

特征：包名 `com.example.kanqiutixing` / 默认 14 支球队（含 4 支国家队）/ 早晨滑杆 5–11 点 / 进 App 直接进赛程页（无向导）/ 含每次构建给图标换色的 dev task。
