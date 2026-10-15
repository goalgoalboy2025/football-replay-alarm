# Step 3 & Step 4 详细操作说明

> 适用：第一次用 Android Studio 的人
> 配套文件：本目录的 README.md

---

## 第 3 步：配置 local.properties

### 3.1 找到模板

解压 zip 后，在 `Kanqiutixing/` 根目录有 `local.properties.template`，内容：

```
sdk.dir=/path/to/your/Android/Sdk
FOOTBALL_DATA_TOKEN=your_token_here
TEAMS_REMOTE_URL=
```

### 3.2 复制并改名为 local.properties

- Windows 文件资源管理器里：选中 `local.properties.template` → Ctrl+C → Ctrl+V
- 把副本重命名为 `local.properties`（**去掉 `.template`**）

> Windows 默认隐藏扩展名。建议先在文件夹顶部"查看"菜单勾选"文件扩展名"。

最终目录里应该有：
```
Kanqiutixing/
├── local.properties.template   (原模板，保留)
└── local.properties            (您要编辑的)
```

### 3.3 填 sdk.dir（Android SDK 路径）

用记事本/VSCode 打开 `local.properties`，把第一行改成您电脑上 SDK 的真实路径。

**默认路径**：
| 系统 | 路径 |
|------|------|
| Windows | `C:\Users\<您的用户名>\AppData\Local\Android\Sdk` |
| macOS | `~/Library/Android/sdk` |
| Linux | `~/Android/Sdk` |

Windows 写法要用正斜杠或双反斜杠：

```
sdk.dir=C:/Users/44928/AppData/Local/Android/Sdk
```

> **省事的方法**：先打开 Android Studio 一次（见 4.1），它会自动帮您填好这一行。

### 3.4 填 FOOTBALL_DATA_TOKEN

1. 浏览器打开 https://www.football-data.org/client/register
2. 填邮箱、姓名、用途（"个人项目"即可）、勾选"Free"档
3. 提交后**几分钟内邮箱会收到一封带 token 的确认邮件**
4. 复制邮件正文里那串 token（一般是 32 位字母+数字）
5. 粘贴到 local.properties：
   ```
   FOOTBALL_DATA_TOKEN=您那串字符
   ```

> 没收到邮件？检查垃圾邮件箱；仍没有就过 10 分钟再注册一次（同邮箱）。

### 3.5 TEAMS_REMOTE_URL 留空

```
TEAMS_REMOTE_URL=
```

留空 = App 用本地 `assets/teams.json`（默认 14 队：10 个俱乐部 + 4 个欧洲国家队）。
未来想远程同步再来填。

### 3.6 最终样例

```
sdk.dir=C:/Users/44928/AppData/Local/Android/Sdk
FOOTBALL_DATA_TOKEN=abc123def456abc123def456abc12345
TEAMS_REMOTE_URL=
```

保存。

---

## 第 4 步：Android Studio 打开并运行

### 4.0 装 Android Studio（如未装）

下载 https://developer.android.com/studio (~1.5 GB) → 一路 Next 装上 → 首次启动会自动下载 Android SDK（5-8 GB，慢）

### 4.1 打开项目

启动 Android Studio → 欢迎窗口点 **`Open`**（不是 New Project）→ 选您解压的 `Kanqiutixing` **文件夹本身** → OK

### 4.2 等 Gradle 同步

打开后自动开始 **Gradle Sync**：底部状态栏会显示进度。

| 网络情况 | 预计时间 |
|---------|---------|
| 国外/挂代理 | 5-15 分钟 |
| 国内直连 | 30 分钟 — 1 小时 |

**完成标志**：
- 状态栏进度条消失
- 底部显示 "Gradle Sync finished"
- 左侧文件树正常显示，无红色叉

**常见失败原因**：

| 错误关键字 | 解决 |
|-----------|------|
| `Could not download gradle-8.7-bin.zip` | 挂代理重试；或手动下载 https://services.gradle.org/distributions/gradle-8.7-bin.zip 放到 `C:\Users\<您>\.gradle\wrapper\dists\gradle-8.7-bin\xxxx\` |
| `SDK location not found` | `local.properties` 的 sdk.dir 没填或路径错 |
| `Failed to resolve: xxx` | Maven 依赖下载失败，多重试 |
| `Plugin not found` | 等久一点（已配国内镜像） |

### 4.3 准备手机

#### 4.3.1 开开发者模式（一次性）

荣耀手机：
1. 设置 → 关于手机 → **连续点击"版本号"7 次**
2. 出现"您已成为开发者"
3. 设置 → 系统和更新 → **开发者选项** → 打开：
   - ✅ 开发者选项总开关
   - ✅ USB 调试
   - ✅ 仅充电模式下允许 ADB（如有）

#### 4.3.2 USB 连接

1. 数据线插上 → 手机弹"允许 USB 调试？" → **勾选"始终允许"** → 允许
2. 通知栏把 USB 模式改为"传输文件"
3. Android Studio 顶部工具栏（绿色三角左边）下拉菜单应该出现"HONOR XXX"

**如果看不到手机**：
- Windows 装荣耀 USB 驱动
- 拔掉重插
- 换原装数据线（劣质线只充电不传数据）

### 4.3.3 点 Run 运行

顶部下拉选中您的手机 → 点 **绿色三角 (▶ Run 'app')**

会发生：
1. Android Studio 编译 APK（首次 1-3 分钟）
2. 自动通过 USB 装到手机
3. **手机自动启动 App**

首次启动弹的权限：
- "允许通知" → 点**允许**
- "闹钟和提醒"页 → 把开关打开 → 返回 App

### 4.4 看到的界面

主屏 = 本周赛程列表，**第一次可能是空的**。

操作：
1. 右上角点 ⟳ 刷新 → 触发"立即同步" → 等 30 秒
2. 赛程出现 = API Key 配置成功
3. 进设置 → "MagicOS 后台保活引导" → 按 5 步配置（看 `06_荣耀手机配置清单.md`）

---

## 故障对照表（汇总）

| 现象 | 原因 | 处理 |
|------|------|------|
| 找不到 local.properties.template | 没解压或解压目录不对 | 重新解压 |
| local.properties 改名后有.txt 后缀 | Windows 自动加的 | 显示文件扩展名后再改 |
| Gradle Sync 一直转 | 网络问题 | 挂代理 / 手动下 Gradle |
| Sync 报 SDK location not found | sdk.dir 没填或填错 | 让 Android Studio 自动填 |
| Run 时找不到手机 | USB 调试没开 / 驱动问题 | 重做 4.3.1、4.3.2 |
| 装到手机后立刻闪退 | API Key 错或没填 | 检查 local.properties |
| App 能开但赛程一直空 | API 调用失败 | 看 Android Studio 底部 Logcat 标签的错误 |
| 通知不弹 | MagicOS 没配 5 项 | 看 06_荣耀手机配置清单.md |

---

## 求助

- 卡在某一步 → 截图错误信息找我（具体错误信息往往是 Android Studio 底部 "Build" 标签里的）
- Android 官方文档：https://developer.android.com/studio/intro
- Stack Overflow：搜英文关键字成功率远高于中文
