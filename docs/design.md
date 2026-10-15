# 看球提醒 Android App · 设计方案

> 项目代号：**kanqiutixing**
> 目标用户：欧洲足球爱好者，关注 10 支俱乐部 + 7 支男子成年国家队（共 17 队）
> 平台：Android 7.0 (API 24) 及以上
> 文档版本：v1.2 · 2026-05-05（17队 + api-football + 三段制 + MagicOS 10）

---

## 一、需求复述

| 编号 | 需求 | 说明 |
|------|------|------|
| R1 | 周赛程推送 | 每周六**北京时间 09:00**，推送未来7天关注球队的全部比赛 |
| R2 | 直播提醒 | 开球时间在 **当日 09:00 — 22:00** 之间 → 开球前 **15 分钟**提醒收看 |
| R3a | 当日录播提醒 | 开球时间在 **当日 00:00 — 09:00** 之间（深夜赛事） → **当天**早晨提醒看录播；**节假日 08:00**，**工作日 07:00** |
| R3b | 次日录播提醒 | 开球时间在 **当日 22:00 — 24:00** 之间 → **次日**早晨提醒看录播；时间规则同上 |
| R4 | 数据准确及时 | 优先保证赛程数据可靠 |

> **说明**：欧洲足球的开球时间换算到北京时间后大致分布为：英超下午场 21:00-22:00、欧冠 03:00-05:00、各联赛晚场 03:00-04:00。三段制覆盖了全部场景。

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────┐
│                  UI 层 (Jetpack Compose)            │
│  · 赛程列表  · 球队设置  · 通知历史  · 关于         │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│           ViewModel + Repository (MVVM)             │
└──────┬──────────────────────────────────┬───────────┘
       │                                  │
┌──────▼──────────┐               ┌───────▼──────────┐
│  Room 本地数据库 │               │  Retrofit 网络层 │
│  matches, teams │               │  api-football    │
│  notifications  │               │  v3 REST API     │
└─────────────────┘               └──────────────────┘
       ▲                                  ▲
       │                                  │
┌──────┴──────────────────────────────────┴───────────┐
│           后台任务 (WorkManager + AlarmManager)     │
│  · WeeklyScheduleWorker (每周六 09:00)             │
│  · MatchReminderAlarm (动态注册精确闹钟)           │
└─────────────────────────────────────────────────────┘
```

技术栈选型理由：

- **Kotlin + Jetpack Compose**：Google 现代官方推荐，代码量比 Java/XML 少 40% 以上
- **Retrofit + OkHttp + Moshi**：HTTP+JSON 解析的事实标准
- **Room**：本地缓存赛程，断网也能查看本周已拉取的数据
- **WorkManager**：处理"每周六 09:00 拉取"——系统级保活，重启后恢复
- **AlarmManager (setExactAndAllowWhileIdle)**：处理"开球前 15 分钟"和"次日早晨"——必须用精确闹钟，WorkManager 时间精度只能到分钟级且会受 Doze 模式影响

---

## 三、核心通知逻辑

### 3.1 状态机（三段制）

```
       ┌─ 周六 09:00 触发 ─┐
       │                   │
       ▼                   │
  拉取未来7天比赛 ─────► 写入 Room ─────► 遍历每场比赛
                                            │
                                            ▼
                              ┌────────────────────────────┐
                              │ 开球时刻 (北京时间) 落在哪段? │
                              └──────┬─────────────────────┘
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        ▼                            ▼                            ▼
   [09:00, 22:00]               (22:00, 24:00)               [00:00, 09:00)
   日间/晚间黄金场                深夜场（同一日）              凌晨场（欧冠等）
        │                            │                            │
        ▼                            ▼                            ▼
   注册"开球前15分钟"           注册"次日早晨"               注册"当日早晨"
   AlarmManager                 节假日 08:00 / 工作日 07:00   节假日 08:00 / 工作日 07:00
   类型: LIVE_15MIN             类型: NEXT_DAY_REPLAY         类型: SAME_DAY_REPLAY
```

**举例**：

| 开球时间（北京时间） | 类型 | 提醒时刻 |
|------------------|------|---------|
| 周六 21:00（英超下午场） | 直播提醒 | 周六 20:45 |
| 周六 22:00（英超晚场） | 直播提醒 | 周六 21:45 |
| 周六 23:00（西甲晚场） | 次日录播 | 周日 07:00 / 08:00（看周日是否节假日） |
| 周日 03:00（欧冠周中赛） | 当日录播 | 周日 07:00 / 08:00 |
| 周三 04:00（欧冠决赛） | 当日录播 | 周三 07:00（工作日） |

### 3.2 节假日判断

中国法定节假日（含调休）每年由国务院公布，无法用算法纯计算。方案：

1. **首选**：调用 `https://timor.tech/api/holiday/year/{year}` 公益接口，返回当年所有节假日 JSON
2. **缓存**：每年 1 月初拉一次，存到 Room；其他时间直接读本地
3. **兜底**：若接口失效，提供静态 JSON 文件（随版本更新），并允许用户在设置里手动覆盖某天为"节假日"或"工作日"

判断逻辑：
```
isHoliday(date):
  if 节假日表包含 date AND 是 holiday  → true
  if 节假日表包含 date AND 是 workday  → false  (调休补班)
  否则按周末判断 (周六/周日 → holiday)
```

### 3.3 时区处理

- 所有 API 返回的时间是 **UTC**
- 全 App 内部统一用 `ZonedDateTime` 在 `Asia/Shanghai` 时区处理
- 显示给用户的时间格式：`05/12 周二 03:00`（24 小时制）

---

## 四、数据模型

### 4.1 Room 表结构

**team 表**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | api-football team ID |
| type | TEXT | CLUB / NATIONAL |
| nameZh | TEXT | 中文名 |
| nameEn | TEXT | 英文名 |
| country | TEXT | 国家 |
| league | TEXT | 主要联赛/所属足联 |
| crestUrl | TEXT | 队徽图片 URL |
| enabled | INTEGER | 是否在用户的关注列表中 (0/1) |

**match 表**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | API 比赛 ID |
| competition | TEXT | 赛事名（联赛/欧冠/世预赛/友谊赛等） |
| competitionType | TEXT | OFFICIAL_CUP / QUALIFIERS / FRIENDLY |
| leagueId | INTEGER | api-football 联赛/赛事 ID（用于过滤） |
| homeTeamId | INTEGER FK | |
| awayTeamId | INTEGER FK | |
| utcKickoff | INTEGER | 开球 UTC 时间戳 (毫秒) |
| status | TEXT | SCHEDULED / LIVE / FINISHED / POSTPONED / CANCELLED |
| score | TEXT | 已结束的比分 |

**notification 表**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK AUTOINCREMENT | |
| matchId | INTEGER FK | |
| type | TEXT | LIVE_15MIN / NEXT_DAY_REPLAY |
| triggerAt | INTEGER | 触发时间戳 |
| status | TEXT | PENDING / FIRED / CANCELLED |
| alarmRequestCode | INTEGER | AlarmManager pendingIntent ID |

### 4.2 API → Model 映射

api-football.com 的 `/v3/fixtures` 端点返回：
```json
{
  "response": [{
    "fixture": {
      "id": 868872,
      "date": "2026-05-09T22:00:00+08:00",
      "timestamp": 1747318800,
      "status": { "long": "Not Started", "short": "NS" }
    },
    "league": { "id": 39, "name": "Premier League", "country": "England" },
    "teams": {
      "home": { "id": 40, "name": "Liverpool" },
      "away": { "id": 33, "name": "Manchester United" }
    }
  }]
}
```
→ 映射到 `match` 表，其中 `competitionType` 字段由 `CompetitionTypeMapper.classify(league.id)` 生成（OFFICIAL_CUP / QUALIFIERS / FRIENDLY），便于按用户的过滤设置筛选。

---

## 五、关键模块清单

| 模块 | 文件名（建议） | 职责 |
|------|--------------|------|
| 网络 | `ApiFootballApi.kt` | Retrofit 接口定义 + DTO + 比赛分类映射 |
| 仓库 | `MatchRepository.kt` | 数据获取与缓存（含比赛类型过滤） |
| 周更新 | `WeeklyScheduleWorker.kt` | 每周六 09:00 触发，拉取并写库 |
| 闹钟调度 | `MatchReminderScheduler.kt` | 为每场比赛注册 AlarmManager（三段制） |
| 闹钟接收 | `MatchAlarmReceiver.kt` | 闹钟触发后弹通知 |
| 节假日 | `ChineseHolidayHelper.kt` | 节假日查询与缓存 |
| 通知 | `NotificationHelper.kt` | 创建 channel、发送通知（直播/录播/汇总） |
| 启动恢复 | `BootReceiver.kt` | 开机后重新注册闹钟 |

---

## 六、权限清单

需要在 `AndroidManifest.xml` 声明：

| 权限 | 用途 | 是否需运行时申请 |
|------|------|-----------------|
| `INTERNET` | 拉取赛程 | 否 |
| `ACCESS_NETWORK_STATE` | 判断网络可用 | 否 |
| `POST_NOTIFICATIONS` | 弹通知（Android 13+） | **是** |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复闹钟 | 否 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟（Android 12+） | **是** |
| `USE_EXACT_ALARM` | 精确闹钟（Android 13+） | 否（白名单中需用户授权） |
| `WAKE_LOCK` | 唤醒设备弹通知 | 否 |
| `FOREGROUND_SERVICE` | 拉取过程显示前台服务（防杀） | 否 |

---

## 七、国产 ROM 后台保活适配

**这是国内安卓 App 最大的坑**。不同厂商杀后台的策略不同。

### 7.1 你的设备：荣耀 + MagicOS 10（重点）

MagicOS 10 基于 Android 14。荣耀从华为分家后，后台管理策略与华为/EMUI 较接近，但有自己的"智能管理"和"应用冷冻"机制，需要逐项关闭。

**必做的 5 项设置**（按顺序）：

```
① 应用启动管理（最关键）
   设置 → 应用和服务 → 应用启动管理 → 找到「G14看球提醒」
   → 关闭"自动管理"开关
   → 手动勾选：✓允许自启动  ✓允许关联启动  ✓允许后台活动
```

```
② 电池优化白名单
   设置 → 电池 → 更多电池设置（或"启动管理"）
   → 找到「G14看球提醒」→ 选"不允许"省电策略
   或者：设置 → 应用 → 应用管理 → G14看球提醒 → 电池 → 设为"不受限制"
```

```
③ 应用冷冻 / 智能省电
   设置 → 电池 → 智能省电 → 关闭对本应用的智能管理
   或：设置 → 应用 → 应用管理 → G14看球提醒 → 关闭"应用冷冻"
   （MagicOS 10 新功能：长时间未打开的应用会被冷冻，必须把本 App 排除）
```

```
④ 通知与状态栏
   设置 → 通知和状态栏 → 应用通知管理 → G14看球提醒
   → 全部允许，且把"直播提醒"channel 设为"重要通知"（横幅+震动）
```

```
⑤ 锁屏后台运行
   设置 → 应用 → 应用管理 → G14看球提醒 → 高级 → 显示在其他应用上层
   （部分 MagicOS 版本叫"悬浮窗"，开启可让通知在锁屏上弹出）
```

**测试方法**：
- 设置完成后强制结束 App（最近任务里上滑）
- 锁屏放置 1-2 小时
- 让朋友给你拨打电话（确认手机没断网/没被杀）
- 等待最近一场比赛的提醒触发，验证是否准时弹出

App 内做一个「MagicOS 引导页」，分这 5 步，每步配截图。荣耀官方的设置路径在不同 MagicOS 子版本可能略有差异，**App 应使用 `Build.MANUFACTURER == "HONOR"` 判断后展示该页**。

### 7.2 其他厂商对照表

| 厂商 | 应对方案 |
|------|---------|
| 华为 (鸿蒙/EMUI) | 应用启动管理 → 关闭"自动管理" → 全部勾选；与荣耀大同小异 |
| 小米 (MIUI/HyperOS) | 设置 → 应用 → 应用管理 → kanqiutixing → 自启动允许 + 省电策略选"无限制" |
| OPPO/realme | 自启动管理 + 耗电保护设为"允许后台运行" + 应用冻结排除 |
| vivo | 后台高耗电允许 + 自启动 + 关联启动 |
| 三星 | 设置 → 设备维护 → 电池 → 应用电源管理 → 例外 |

### 7.3 代码层加固

即便用户配置正确，仍要做这些保险：

1. **使用 `setExactAndAllowWhileIdle`**（已在示例代码中），是 Doze 模式下唯一可靠的精确闹钟 API
2. **WorkManager 的 `setExpedited`**（Android 12+）：周更新任务用 expedited 模式
3. **前台服务兜底**：如果检测到关键提醒迟到 ≥ 5 分钟，下次自动启用前台服务保活
4. **后台监测**：用 `WorkManager.getWorkInfosByTagLiveData()` 在 UI 上展示上次成功执行时间，超过 8 天未成功则红色警告

---

## 八、不在 v1 范围内的功能（明确不做）

- ❌ 比分实时推送（不是看球前提醒，是看球期间）
- ❌ 视频内嵌播放（涉及版权）
- ❌ 用户账户/云同步（增加复杂度，单机版即可）
- ❌ iOS 版（当前需求是安卓）
- ❌ 球队增删界面（v1 硬编码 17 队；后续版本可加自定义球队）

---

## 九、开发顺序建议

```
Week 1: 项目骨架 + Retrofit 接通 api-football.com，命令行能拉到 17 队数据
Week 2: Room 数据库 + 比赛分类映射 + UI 列表能展示赛程
Week 3: WorkManager 实现每周六 09:00 拉取
Week 4: AlarmManager 实现三段制精确提醒（最难，需大量测试）
Week 5: 节假日 + MagicOS 10 适配引导页 + 比赛类型过滤设置
Week 6: 打磨 UI、写打包脚本、出 APK
```

预计总开发量 80-120 小时（业余时间约 6-8 周）。

---

## 十、文件交付一览

| 文件 | 内容 |
|------|------|
| `01_设计方案.md` | 本文件 |
| `02_数据源调研.md` | api-football.com 使用细则 |
| `03_关注球队清单.json` | 17 支球队（10 俱乐部 + 7 国家队）及 API ID |
| `04_UI原型.html` | 浏览器打开即可预览 App 界面 |
| `05_环境搭建指南.md` | 从零到打出 APK 的完整步骤 |
| `06_荣耀手机配置清单.md` | MagicOS 10 后台保活专属指南 |
| `code_samples/` | 6 个核心 Kotlin 文件示例 + Manifest 片段 |
