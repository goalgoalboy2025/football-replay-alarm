# 看球不熬夜闹钟

> 给熬夜不动了的球迷的"录播闹钟"——深夜比赛结束后，第二天早晨准点叫醒你。

一个不打扰、无广告、不收集个人信息的 Android 提醒 App，专门做一件事：把你关注的欧洲俱乐部的比赛日程，**按你的作息**变成准点的通知。

| 开球时段（北京时间） | 提醒方式 | 触发时间 |
| --- | --- | --- |
| 09:00 — 22:00 | 直播提醒 | 开球前 15 分钟 |
| 22:00 — 24:00 | 录播提醒 | 次日早晨 |
| 00:00 — 09:00 | 录播提醒 | 当日早晨 |

早晨时间用户可在首启动向导和设置里自定义，工作日和节假日分别设置（5:00 — 12:00 任选），节假日用 [timor.tech](https://timor.tech/) 法定节假日数据自动判定。

## 截图

界面视觉 mockup 在 [`preview/ui_mockup.html`](preview/ui_mockup.html)，浏览器打开就能看到 6 个核心屏幕（4 步首启动向导 + 赛程页 + 设置页）的样子。

## 默认关注 18 支球队

- 英超：阿森纳、曼城、曼联、切尔西、利物浦、热刺
- 西甲：巴萨、皇马、马竞
- 德甲：拜仁慕尼黑、多特蒙德、勒沃库森
- 意甲：AC 米兰、国际米兰、尤文图斯、那不勒斯
- 法甲：巴黎圣日耳曼、马赛

首启动向导里勾选你关心的（至少 1 支），之后可在设置页里随时增减。

## 已知限制（请务必看）

数据源是 [football-data.org](https://www.football-data.org/) 免费档，覆盖五大联赛 + 欧冠 + 欧洲杯 + 世界杯。**以下比赛抓不到、不会提醒**：

- 域内杯赛：FA Cup、英联杯、意大利杯、西班牙国王杯、德国杯、法国杯
- 欧联杯、欧协联
- 南美洲赛事、国家队友谊赛

如果你关注的球队当周打的是上面这些杯赛——例如 FA Cup 决赛——本 App 不会提醒。这是数据源约束。未来若切换到付费档或别的数据源会补全。

## 安装

### 给朋友（不写代码的人）

到本仓库 [Releases 页](https://github.com/dandan/kanqiutixing/releases) 下载最新的 `kanqiutixing-vX.Y.Z.apk`，传到 Android 手机点开安装。手机会提示"未知来源应用"——允许一次即可。

**荣耀 / 华为 MagicOS 用户**：装好打开 App，进设置页 → "MagicOS 后台保活引导"，跟着 5 步走一遍——这一步必须做，否则深夜提醒会被系统冻结。其它国产手机品牌（小米、OPPO、vivo 等）原理类似，可参考其中"应用启动管理 / 电池策略"两条自行配置。

### 给开发者（从源码构建）

需要 JDK 17、Android Studio Hedgehog (2023.1) 或更新版本。

```bash
git clone https://github.com/dandan/kanqiutixing.git
cd kanqiutixing
```

到 [football-data.org/client/register](https://www.football-data.org/client/register) 免费注册账号，邮箱会收到 token，然后：

```bash
cp local.properties.template local.properties
# 编辑 local.properties，把 FOOTBALL_DATA_TOKEN= 后面填上 token
```

之后用 Android Studio 打开项目目录直接 Run，或者命令行：

```bash
./gradlew installDebug   # 编译并装到连接的手机上
```

### 出 release 包

需要先生成 keystore + 创建 signing.properties：

```bash
keytool -genkey -v -keystore kanqiutixing-release.jks \
        -keyalg RSA -keysize 2048 -validity 25000 \
        -alias kanqiutixing
cp signing.properties.template signing.properties
# 编辑 signing.properties，把刚才设置的密码填上
```

keystore 和 signing.properties 都在 `.gitignore` 里，不会被意外提交。然后：

```bash
./gradlew assembleRelease
# 产物在 app/build/outputs/apk/release/
```

## 增减球队

### 方式 A：改 assets/teams.json + 重新打包

往 `app/src/main/assets/teams.json` 的 `teams` 数组里加元素：

```json
{ "id": 92, "type": "CLUB", "nameZh": "皇家社会", "nameEn": "Real Sociedad de Fútbol",
  "country": "Spain", "league": "西甲" }
```

球队 id 用 football-data API 查（需要 token）：

```bash
curl 'https://api.football-data.org/v4/competitions/PD/teams' \
  -H 'X-Auth-Token: <你的token>' | jq '.teams[] | {id,name}'
```

`PD` 是西甲联赛 code，其它：`PL` 英超 / `BL1` 德甲 / `SA` 意甲 / `FL1` 法甲 / `CL` 欧冠。

补完 `teams.json` 重新打包即可——`TeamConfigLoader` 启动时会 diff 数据库自动 upsert。

### 方式 B：远程 JSON 热更新

把同样格式的 JSON 上传到任意公开 URL（GitHub Gist 最简单），在 `local.properties` 设置：

```
TEAMS_REMOTE_URL=https://gist.githubusercontent.com/yourname/xxxxx/raw/teams.json
```

App 启动时会先尝试远程，失败回退到 assets。之后改 Gist 即可改球队，不用重发 APK。

## 项目结构

```
Kanqiutixing/
├── app/src/main/
│   ├── java/io/github/dandan/kanqiutixing/
│   │   ├── KanqiuApp.kt              Application + Hilt 入口
│   │   ├── MainActivity.kt           NavHost + 路由决策
│   │   ├── data/                     仓库 + DAO + DTO
│   │   ├── di/AppModule.kt           Hilt 模块
│   │   ├── holiday/                  节假日判定（timor.tech）
│   │   ├── notification/             AlarmManager + 通知 channel
│   │   ├── ui/                       Compose 屏幕
│   │   │   ├── StartupViewModel.kt   决定走向导还是直接进赛程
│   │   │   ├── setup/                4 步首启动向导
│   │   │   ├── schedule/             赛程列表
│   │   │   ├── settings/             设置页
│   │   │   └── onboarding/           MagicOS 后台保活引导
│   │   └── work/                     WorkManager 周更新 / 重排闹钟
│   ├── assets/
│   │   ├── teams.json                默认 18 支球队
│   │   └── team_names_zh.json        英→中翻译表
│   └── res/                          字符串、主题、图标
├── preview/ui_mockup.html            浏览器可看的 UI mockup
├── PRIVACY.md                        隐私政策
└── LICENSE                           MIT
```

## 调试

提醒链路上 3 个关键 tag：

```bash
adb logcat -v time \
  -s KanqiuAlarm:* KanqiuScheduler:* KanqiuNotify:* \
     KanqiuWorker:* KanqiuHoliday:* KanqiuReschedule:*
```

正常排闹钟会看到：
```
KanqiuScheduler: set SAME_DAY_REPLAY @ 05-17 08:00 (exact=true, reqCode=12345) [利物浦 vs 阿斯顿维拉 @ 05-17 03:00]
```

闹钟触发时会看到：
```
KanqiuAlarm: onReceive 触发
KanqiuNotify: notifyMatchReminder posted id=12345 type=SAME_DAY_REPLAY
KanqiuAlarm:   ✓ 已发出 SAME_DAY_REPLAY 通知
```

如果通知没弹但 logcat 显示 `KanqiuAlarm:   ⚠️ POST_NOTIFICATIONS 未授权`——去系统设置开通知权限。

## 隐私

不收集、不存储、不上传任何个人信息——详见 [`PRIVACY.md`](PRIVACY.md)。

## License

[MIT](LICENSE)

## 致谢

- [football-data.org](https://www.football-data.org/) 提供比赛数据
- [timor.tech](https://timor.tech/) 提供中国节假日数据
- 提醒链路灵感来自每个熬夜看了球早晨却又要上班的球迷
