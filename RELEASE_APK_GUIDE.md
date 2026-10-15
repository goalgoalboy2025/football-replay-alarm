# 构建 release APK 详细指南

`RELEASE_CHECKLIST.md` 第三节"构建 release APK"的逐项展开。
照这份文档从头到尾走一遍，结尾你会得到一个**任何 Android 手机都能装**的 `app-release.apk`。

> 这一段是把你的 App 从"自己 Run"变成"能装到任何手机上的 APK"的关键流程。

---

## 为什么需要这一段

Android 系统强制要求**每个 APK 都得有数字签名**——相当于一个"印章"，证明"这是我做的，不是别人冒名顶替"。

你在 Android Studio 点 Run 时，AS 用的是一个**所有开发者共享的临时调试印章**（叫 debug keystore），所以能装、能跑。但这种印章 Google Play 不认，也不能用来发正式版本，更重要的是——**两台手机如果都装了"同一个 App 但用不同印章签的"，Android 会拒绝其中后装的那个**。

所以发正式版前，你得：

1. 生成你**自己的私人印章**（叫 release keystore）
2. 用这个印章去签出来一份 **release APK**
3. **这个印章保管好**——以后这个 App 所有版本必须用同一个印章签，丢了就再也没法给老用户推送更新

---

## 前置条件

- 已安装 JDK 17（Android Studio 自带，路径通常在 AS → Settings → Build → JDK Location）。`keytool` 命令是 JDK 自带的
- 终端能找到 `keytool`：跑 `keytool -help` 没报错就行；找不到的话把 JDK 的 `bin/` 目录加进 PATH
- 你已经在终端能跑通 `./gradlew assembleDebug`（debug 版本能成功 build）

---

## 6 步操作

下面所有命令都假设你已经 `cd` 到项目根目录（含 `build.gradle.kts` 的那个目录）：

```bash
cd <你的工作目录>/kanqiutixing/Kanqiutixing/
```

### ① 生成 keystore（一次性）

打开**终端**（Mac 的 Terminal / Windows 的 PowerShell），跑：

```bash
keytool -genkey -v -keystore kanqiutixing-release.jks \
        -keyalg RSA -keysize 2048 -validity 25000 \
        -alias kanqiutixing
```

它会**交互式**问你一串问题：

| 提问 | 怎么填 |
| --- | --- |
| `输入新密钥库的密码：` | **自己设一个密码，至少 8 位，记下来** |
| `再次输入新密码：` | 同一个 |
| `您的名字与姓氏是什么？` | 你名字或网名（比如 `dandan`），可以瞎填 |
| `您的组织单位名称是什么？` | 按回车跳过 |
| `您的组织名称是什么？` | 按回车跳过 |
| `您所在的城市或区域名称是什么？` | 按回车跳过或填城市 |
| `您所在的省/市/自治区名称是什么？` | 按回车跳过 |
| `该单位的双字母国家/地区代码是什么？` | `CN` |
| `是否正确？` | 输 `是` 或 `y` 回车 |
| `输入 <kanqiutixing> 的密钥口令...` | **直接回车**让它跟前面的密码一致 |

跑完后当前目录会多出一个 `kanqiutixing-release.jks` 文件。

> ⚠️ **立刻备份这个文件**——复制一份到 U 盘 / iCloud / Dropbox / 邮箱给自己发一份。
> 这个文件一丢，App 在 GitHub 上的所有老用户以后都装不上你的新版本（必须重新换 applicationId 才能再发，等于换了个 App）。

### ② 创建 `signing.properties`

这个文件告诉 Gradle："印章在哪、密码是啥"。项目里已经放了模板：

```bash
cp signing.properties.template signing.properties
```

然后用任意文本编辑器（VS Code / Sublime / 甚至记事本）打开新生成的 `signing.properties`，填这 4 行：

```properties
storeFile=kanqiutixing-release.jks
storePassword=刚才你设的那个密码
keyAlias=kanqiutixing
keyPassword=刚才你设的那个密码
```

保存。

### ③ 验证密钥和密码不会进 git

**密码和印章文件绝对不能进 git**，否则一旦你公开 repo，任何人都能拿你的印章重新打包冒充你的 App。

验证方法——终端跑：

```bash
git status
```

看输出里的 `Untracked files:` 列表，**应该看不到 `signing.properties` 和 `kanqiutixing-release.jks`**。如果看到了，说明 .gitignore 没生效，停下来排查。

### ④ 跑 release 构建

```bash
./gradlew assembleRelease
```

（Mac / Linux 用 `./gradlew`，Windows PowerShell 用 `.\gradlew`）

第一次跑大概要 2–10 分钟（看机器），会看到一大堆 `> Task :app:compileReleaseKotlin` 之类输出。**最后一行出现 `BUILD SUCCESSFUL`** 就是成了。

如果 `BUILD FAILED`，常见原因：

| 报错关键字 | 原因 | 解决 |
| --- | --- | --- |
| `Keystore was tampered with, or password was incorrect` | `signing.properties` 里密码填错 | 检查密码、注意有没有多余空格 |
| `Failed to read key ... from store` | keyAlias 拼错 | 必须是 `kanqiutixing`（创建 keystore 时用的 alias）|
| `Could not resolve dependency` | 国内网络拉不到 Maven 依赖 | 挂代理或换镜像 |
| `Lint found errors` | 代码 lint 检查没过 | 看具体哪行 lint error，修了再编 |

报错截屏发我看具体问题。

### ⑤ 找到生成的 APK

build 成功后，APK 文件就在这个目录。在终端跑：

```bash
ls -lh app/build/outputs/apk/release/
```

你会看到类似：

```
-rw-r--r--  1 you  staff   8.2M  May 18 14:32  app-release.apk
```

**这个 `app-release.apk` 就是你要发给朋友的最终产物**。一般 5–15 MB 大小。

### ⑥ 装到手机验证

为什么强调"**不同的手机**"——你自己那台手机已经装过 debug 版本（v0.1 + v1 调试期间），DataStore 里可能有残留状态，可能不会触发"全新用户向导"。

你可能没有第二台手机。两个变通办法：

**办法 A：在你现有手机上模拟"全新用户"**

- 系统设置 → 应用 → 看球不熬夜闹钟 → 存储 → **清除数据**
- 卸载 App
- 把 `app-release.apk` 复制到手机（USB 拷贝 / 微信传输 / AirDrop）
- 用文件管理器找到这个 APK，点开安装
- 第一次打开应该看到 4 步向导

**办法 B：发给朋友，远程验证**

- 把 `app-release.apk` 用微信文件传输 / 邮箱 / 网盘 发给一个朋友
- 让他装上
- 问他第一次打开是不是看到 4 步向导，按完能不能正常进赛程页
- 他能跑通 = 你的发布版本 OK

装 APK 时手机可能弹"未知来源应用"警告——点"允许这次安装"或者去 设置 → 安全 → 安装未知来源应用 里允许一次，就能装上。

---

## 完成之后干嘛

你手里就有了一份**任何 Android 手机都能装的 `app-release.apk`**。

下一步（`RELEASE_CHECKLIST.md` 的第 4、5 节）：

1. 把代码推 GitHub（`git push`）
2. 在 GitHub 网页上 Draft a new release
3. 把 `app-release.apk` 作为附件上传
4. 朋友从你 GitHub Releases 页面就能下载

---

## 踩坑速查

| 现象 | 原因 / 解决 |
| --- | --- |
| `keytool: command not found` | JDK 没装或 PATH 没配置好，AS 自带 JDK 在 AS 的 jbr/bin 下 |
| 安装 APK 时手机说"应用未安装" | 大概率你这台手机之前装过 debug 版本，签名不匹配 → 先把旧版本完全卸载再装 |
| 朋友手机说"解析包出错" | APK 传输过程损坏 → 换 USB 直接拷贝或者重新下载 |
| 朋友手机说"开发者尚未验证" | 系统警告，跟 keystore 无关，允许"未知来源"即可 |
| 第一次打开**没出现向导** | 检查你是不是装的 debug 版本而不是 release（卸载干净再装）；或者代码层 onboarding flag 出问题（查 logcat `KanqiuApp`）|

---

## 这份文档保存在哪儿

- 项目内：`kanqiutixing/Kanqiutixing/RELEASE_APK_GUIDE.md`（这个文件本身）
- `PROJECT_STATUS.md` 的"关键文件都在哪"一节也加了链接
- 想再拷一份到桌面：从工作目录顶层直接拖到桌面即可
