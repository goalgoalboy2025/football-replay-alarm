# 国产 ROM 上"隔夜提醒不响"的完整排查与解法

> 本文是 README「技术实现」章节的完整版，也是技术文章的稿源。
> 结论先行：**在国产 ROM 上做隔夜精确提醒，唯一可靠的路径是 `AlarmManager.setAlarmClock()`，且必须接住它抛出的异常；闹钟响了之后，还要保证通知渠道"响得出来"。**

经验来源：「吧嗒清单」的 ROM 适配实践 + 本项目真机验证（荣耀 400 Pro，MagicOS 10.0.0.175 / Android 16）。

---

## 一、症状：反直觉的双标测试

本项目第一版提醒链路用 `setExactAndAllowWhileIdle()` 挂闹钟，测试时出现了一个非常迷惑的现象：

- **2 分钟测试闹钟**：每次都准时响，100% 复现。
- **真实的隔夜录播提醒**（比如凌晨 3:00 的欧冠 → 早晨 7:00）：从来不响。不是偶尔不响，是**一次都没响过**。

如果 2 分钟测试不响，谁都查得到；恰恰因为它响，让人以为是"偶发"或"系统抽风"。真实的差异是：

| | 2 分钟测试 | 隔夜提醒 |
| --- | --- | --- |
| 距离设置的时间 | 2 分钟 | 4–8 小时 |
| 设置时的 App 状态 | 前台、用户刚点完按钮 | 后台 |
| 熄屏时长 | 通常亮着 | 一整夜 |
| 系统对 App 的处置 | 来不及冻结 | 进入冻结/深度睡眠 |

**短延迟测试掩盖了长延迟场景的失败。** 任何做"后台定时任务"的 App 都应该警惕这种测试盲区。

## 二、根因：三层"不被打扰"机制

Android 让设备省电的机制叠了三层，隔夜正是三层全部生效的时段：

1. **Doze（打盹）**：熄屏且不动，系统进入深度休眠，只留少数维护窗口。原生 AOSP 上 `setExactAndAllowWhileIdle()` 确实能在维护窗口之外唤醒——这是它"名义上可行"的来源。
2. **应用待机分桶（App Standby Buckets）**：按使用频率把 App 分进 active/working set/frequent/rare/restricted 桶，桶越冷，JobScheduler 和闹钟被推迟得越狠。一个"一周才用几次"的看球 App 大概率躺在 rare。
3. **厂商冻结（OEM-level freeze）**：荣耀 MagicOS 的"应用冷冻"、小米的"深度管控"、华为的"启动管理"，会在自己的一套逻辑里把不活跃 App 直接挂起（类似 cgroup freezer）。**这一层不在 AOSP 语义里，`setExactAndAllowWhileIdle` 对它没有任何豁免。**

所以根因链条是：熄屏一夜 → 三层机制逐级生效 → `setExactAndAllowWhileIdle` 的唤醒请求被厂商冻结丢弃或推迟到下一个任意维护窗口 → 用户早晨醒来才发现提醒半夜"应该响"的时刻早已过去。

## 三、解法：`setAlarmClock()`，并把 try/catch 写全

### 3.1 为什么是 setAlarmClock

`AlarmManager.setAlarmClock()` 会向系统登记一个**"用户闹钟"**（带 `showIntent`，点状态栏闹钟图标可以打开 App）——这是系统时钟 App 用的同一类闹钟。Android 对它的待遇完全不同：

- 穿透 Doze，不受分桶限制；
- 厂商 ROM 把它当作用户可感知的闹钟，**不敢延迟**（延迟用户闹钟是应用商店差评制造机）；
- 代价：状态栏常驻闹钟图标。对闹钟类 App 来说这是特性不是缺陷。

### 3.2 真实代码（本项目 `MatchReminderScheduler.setAlarm()`）

```kotlin
private fun setAlarm(triggerMillis: Long, operation: PendingIntent): Boolean {
    // 顺序：setAlarmClock → setExactAndAllowWhileIdle → setAndAllowWhileIdle。
    // 关键是 setAlarmClock 外面要有 try/catch：少数 ROM 对它有限制会抛异常，
    // 不接住的话闹钟就压根没挂上（而且不会有任何日志，表现为"设了却从来不响"）。
    if (canScheduleExactAlarms()) {
        try {
            val showIntent = PendingIntent.getActivity(
                context, ALARM_CLOCK_SHOW_REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerMillis, showIntent),
                operation
            )
            return true
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ setAlarmClock 失败（${e.javaClass.simpleName}），退回 setExactAndAllowWhileIdle")
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerMillis, operation
            )
            return true
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ setExactAndAllowWhileIdle 也失败，退回不精确闹钟")
        }
    } else {
        Log.w(TAG, "⚠️ 无精确闹钟权限，退回 setAndAllowWhileIdle（提醒可能延迟送达）")
    }
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
    return false
}
```

**四个不能省的点**：

1. **try/catch 不能省**。少数 ROM 对 `setAlarmClock` 有限制会直接抛异常（SecurityException 等）。不接住 = 闹钟根本没挂上，而且**没有任何日志**，表现恰好就是"设了却从来不响"。
2. **降级链要有**。`setAlarmClock` 失败退 `setExactAndAllowWhileIdle`，再失败退 `setAndAllowWhileIdle`——不精确但至少能响。
3. **先查 `canScheduleExactAlarms()`**（Android 12+ 用户可撤销精确闹钟权限），没权限时直接走不精确路径并提示用户。
4. **测试闹钟走同一条路**。本 App 的"测试通知"和真实提醒共用 `setAlarm()`——**用降级路径测真实送达能力才有意义**。用 `setExact` 单独写测试路径，测出来的是假绿灯。

### 3.3 测试盲区复盘

修好后我们保留了"2 分钟测试"和"30 分钟测试"两个入口（设置 → 高级），但 README 和引导页都明确写了：**最终验收必须做"8 小时测试"——睡前确认早晨有提醒，睡一觉看是否准时响。** 荣耀 400 Pro 上三项全过。

## 四、第二层坑：闹钟响了，通知"响不出来"

闹钟准时触发只是第一步。国产 ROM 对**通知本身**还有一层过滤：

- 没有铃声/震动的通知会被折叠成静默通知；
- 锁屏不可见的通知在熄屏时干脆不显示；
- 免打扰会直接吞掉普通通知。

对应配置（本项目 `NotificationHelper.buildChannel()`，三条渠道统一）：

```kotlin
channel.setSound(
    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)   // 显式铃声，别指望渠道默认值
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
)
channel.enableVibration(true)
channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
channel.setBypassDnd(true)

notification = notification
    .setCategory(Notification.CATEGORY_ALARM)
    .setVisibility(Notification.VISIBILITY_PUBLIC)
```

**关键陷阱：渠道的重要度（IMPORTANCE）建成后系统不允许 App 再修改**——但铃声、震动这些细项可以改。所以本 App 每次启动都会把三个渠道重新"修补"一遍，用户在系统设置里改乱、或 ROM 升级重置渠道后能被自动救回。

`post()` 统一收口三个 notify 调用点：无 `POST_NOTIFICATIONS` 权限直接 return，`SecurityException` 接住——不给系统抛崩溃的机会。

## 五、可观测性：让用户自己能查

"不响"类问题的排查成本极高（要 logcat、要 adb）。本 App 内置「提醒健康检查」页，逐项显示：

1. 通知权限（POST_NOTIFICATIONS）
2. 精确闹钟权限（canScheduleExactAlarms）
3. 三条渠道的重要度 / 铃声 / 震动
4. 电池优化白名单（isIgnoringBatteryOptimizations）
5. 当前已挂起的闹钟列表（来自 Room 的 PENDING 记录，和 AlarmManager 实际状态对账）
6. 上次赛程同步时间

配合 `docs/oem-battery-whitelist.md` 的 5 厂商配置清单，大多数用户不需要电脑就能自救。

## 六、5 厂商冻结机制与配置入口速查

| 厂商 / ROM | 冻结机制名 | 关键配置入口 |
| --- | --- | --- |
| 荣耀 MagicOS | 应用冷冻 / 智能省电 | 应用启动管理（手动三开）+ 电池不受限 |
| 小米 HyperOS / MIUI | 深度管控 / 神隐模式 | 自启动 + 省电策略无限制 |
| 华为 EMUI / HarmonyOS | 启动管理 / 智能省电 | 手动管理三开 + 手机管家启动管理 |
| OPPO ColorOS | 应用快速冻结 | 允许完全后台行为 + 最近任务锁定 |
| vivo OriginOS | 后台功耗管理 | 后台高耗电 + 锁定后台 + 自启动 |

逐步菜单见 [`oem-battery-whitelist.md`](oem-battery-whitelist.md)。

另一个容易被忽略的坑：**WorkManager 在冻结 App 上会被压后到 App 进前台才执行**——赛程同步晚了，录播提醒的触发点已经过去。所以本 App 对"触发点已过但不超过 24 小时、且从未补发过"的录播提醒**补弹一次**（直播提醒过了就跳过），用户不会因为 ROM 拖慢同步而错过比分。

## 七、验证清单（新机型适配时照抄）

1. 5 秒测试通知：锁屏弹横幅 + 铃声 + 震动
2. 30 分钟测试闹钟：熄屏后准时响
3. 8 小时真实场景：睡前设置，早晨准时响
4. 重启手机：开机广播后闹钟恢复（BootReceiver + TIME_SET/TIMEZONE_CHANGED/DATE_CHANGED 重排）
5. 关掉通知权限再开：渠道重要度没被降级

---

*本文观点的可复现代码：[`MatchReminderScheduler.kt`](../app/src/main/java/io/github/dandan/kanqiutixing/notification/MatchReminderScheduler.kt)、[`NotificationHelper.kt`](../app/src/main/java/io/github/dandan/kanqiutixing/notification/NotificationHelper.kt)、[`ReminderDiagnostics.kt`](../app/src/main/java/io/github/dandan/kanqiutixing/notification/ReminderDiagnostics.kt)。*
