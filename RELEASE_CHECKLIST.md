# v1.0.0 发布清单

照这个清单一项一项打勾，全部 ✅ 之后就可以 `git tag v1.0.0` + `git push --tags` + 发 GitHub Release。

## 一、Android Studio 端

- [ ] **File → Invalidate Caches and Restart**（清掉 v0.1 时代 `com.example.kanqiutixing` 的 build cache）
- [ ] Gradle Sync 通过，没有红色错误
- [ ] 打开 `SetupWizardScreen.kt`，编辑器右上角切到 Split 模式，4 个 `@Preview` 都能正常渲染
- [ ] 跑一次 `Build → Make Project`（Ctrl/Cmd+F9），编译无报错
- [ ] 跑一次 `./gradlew lintDebug`（终端里执行）或 `Analyze → Inspect Code`，无 Error 级问题（Warning 可以忽略）

## 二、真机验证（这是关键）

### 2.1 全新用户路径

- [ ] 设置 → 应用 → 看球不熬夜闹钟 → 存储 → **清除数据**（或卸载重装 APK）
- [ ] 打开 App，确认看到首启动向导
- [ ] **Step 1 通知权限**：点"授予通知权限"按钮，弹系统对话框，授权 → 状态变成"通知权限已开启"
- [ ] **Step 2 精确闹钟权限**：点"打开系统设置开启"，到系统设置里开闸阀，返回 App → 状态自动刷新成"精确闹钟权限已开启"
- [ ] **Step 3 球队选择**：5 个联赛分组都正常显示，18 支球队全部列出。试着不选任何一支，"下一步"按钮应该是灰色的；选 1 支后变可点
- [ ] **Step 4 提醒时间**：两个滑杆可拖动，范围 5–12 点，时间显示同步更新
- [ ] 点"完成，开始用"：跳到赛程页，看到"正在加载本周赛程… 首次同步可能需要 30 秒左右"
- [ ] 等 30 秒左右，赛程列表填上比赛
- [ ] 按 home 键回桌面，再次打开 App → **直接进赛程页**，不再走向导

### 2.2 提醒链路

- [ ] 设置 → 测试通知 → "立即发送测试通知"，通知栏出现 ✓
- [ ] 设置 → "2 分钟后提醒测试"，App 划掉后台 → 2 分钟后通知栏弹出 ✓
- [ ] 赛程页勾选未来 1-2 场比赛，按"确认提醒选择" → 看到 "已为 N 场比赛设置提醒" snackbar
- [ ] 用 `adb logcat -s KanqiuScheduler:*` 看到对应的 `set TYPE @ MM-dd HH:mm` 日志

### 2.3 老用户升级路径（如果手里有跑 v0.1 的设备）

- [ ] 装 v1.0 直接覆盖安装，**不清数据**
- [ ] 打开 App，确认**不出现**向导，直接进赛程页
- [ ] 之前关注的 10 支俱乐部仍在 enabled 状态
- [ ] 4 支国家队从设置页里消失了（被 TeamConfigLoader 删了）
- [ ] 之前已勾选的比赛提醒在 logcat 里看到 `KanqiuReschedule` 任务跑过（开机后会重排）

## 三、构建 release APK

- [ ] 已生成 `kanqiutixing-release.jks` keystore（按 README 的 keytool 命令）
- [ ] 已创建 `signing.properties`，填好 4 个字段
- [ ] `signing.properties` 和 `.jks` 都在 `.gitignore` 里，`git status` 看不到
- [ ] `./gradlew assembleRelease` 跑通
- [ ] `app/build/outputs/apk/release/` 下有 `app-release.apk`
- [ ] 把 release APK 装到一台**不同的手机**上，跑一遍 2.1 全新用户路径

## 四、Git 仓库

- [ ] `git status` 干净（或只剩你预期的本地配置文件）
- [ ] `git log --oneline` 看一遍 commit 历史，没有 "wip" / "test" / "x" 之类垃圾
- [ ] `local.properties` 没被提交
- [ ] `signing.properties` 没被提交
- [ ] `*.jks` 没被提交
- [ ] 整个 v0.1 时代的 `com.example.kanqiutixing` 包名痕迹在 git 历史里也清掉了（如果之前 commit 过，需要 `git filter-repo`）
- [ ] **`FOOTBALL_DATA_TOKEN` 没有出现在任何 commit 的 diff 里**（重点检查！grep 自己的 token 字符串）

## 五、GitHub

- [ ] 建一个 **private** repo，名字 `kanqiutixing` 或 `kanqiu-bushou`
- [ ] `git push -u origin main`
- [ ] 找另一台干净的电脑 / 一个空目录，`git clone` 下来 → 按 README 步骤走一遍能否完整 build + run
- [ ] 确认 clone 后从零构建没问题 → 把 repo **切 public**
- [ ] `git tag v1.0.0 && git push --tags`
- [ ] GitHub 网页上：Releases → Draft a new release → 选 `v1.0.0` tag → 标题 `v1.0.0 · 首个公开版本` → 把 CHANGELOG 1.0.0 段贴进 description → 上传 release APK 附件 → Publish

## 六、可选 / v1.1 候选

下面这些不阻塞 v1.0 发布，可以放到 v1.1：

- [ ] 实际去 football-data.org 验证 8 个未在 v0.1 用过的 team id（4/5/73/98/108/109/113/516/524）正确
- [ ] 视觉换皮：把 `preview/ui_mockup.html` 的 UCL 深色主题搬进 KanqiuTheme
- [ ] 把"资格赛 / 友谊赛"两个无效开关从设置页 UI 隐藏
- [ ] 接 Sentry 或 Firebase Crashlytics 看用户崩溃
- [ ] GitHub Actions 加上 release 构建（tag push 触发 → 自动出签名 APK 挂 release）
- [ ] 应用图标换成像样的——现在还是 Android 默认机器人
- [ ] 字符串外置到 `strings.xml`，给未来英文版铺路
