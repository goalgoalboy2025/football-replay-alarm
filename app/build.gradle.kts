import java.util.Properties
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// release 包签名配置。signing.properties 在 .gitignore 里，
// 不提交到 git；本地不存在时跳过签名配置（debug 包仍能正常构建）。
// 想出 release 包：参考 signing.properties.template 创建 signing.properties 并生成 keystore。
val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigningConfig = signingProps.containsKey("storeFile")

// 每次构建时刻（配置阶段求值，每次构建都会刷新），用于 App 内显示"构建时间"
val buildTimeStamp: String =
    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date())

// ── 共存测试包 ──────────────────────────────────────────────────────────
// 默认构建的是「覆盖升级包」，与手机上已装的正式包同 applicationId、同签名，直接覆盖更新。
// 传 -PbetaSuffix=.beta 则构建「共存测试包」：applicationId 加后缀、桌面名加"·测"、
// versionName 加 -beta。它是一个独立 App，与旧包并存、数据互不影响，
// 适合新旧对照测试（旧包留着跑，新包同时装）。
//   ./gradlew assembleDebug                 → 覆盖升级包
//   ./gradlew assembleDebug -PbetaSuffix=.beta → 共存测试包
val betaSuffix: String = (project.findProperty("betaSuffix") as String?).orEmpty()
val isBeta = betaSuffix.isNotEmpty()

android {
    namespace = "io.github.dandan.kanqiutixing"
    compileSdk = 34

    defaultConfig {
        // 共存测试包用独立的 applicationId → 系统视作另一个 App，与旧包并存
        applicationId = "io.github.dandan.kanqiutixing$betaSuffix"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "1.1.0" + if (isBeta) "-beta" else ""

        // 桌面名：共存测试包带"·测"，图标一眼可辨，不会和正式包搞混
        manifestPlaceholders["appLabel"] =
            if (isBeta) "看球不熬夜闹钟·测" else "看球不熬夜闹钟"

        buildConfigField(
            "boolean",
            "IS_BETA",
            "$isBeta"
        )
        buildConfigField(
            "String",
            "APPLICATION_LABEL",
            "\"${if (isBeta) "看球不熬夜闹钟·测" else "看球不熬夜闹钟"}\""
        )
        // 数据源 token —— 双轨制的「构建注入」轨，见 data/TokenProvider.kt：
        //   本地开发 / CI：在 local.properties 填自己的 token，clone 下来就能跑
        //   对外分发的 release 包：必须留空，让用户在 App 内 设置 → 数据源 填自己的
        // 留空时本字段是空串，TokenProvider 会要求用户填写，不会静默发无效请求。
        buildConfigField(
            "String",
            "FOOTBALL_DATA_TOKEN",
            "\"${localProps.getProperty("FOOTBALL_DATA_TOKEN", "")}\""
        )
        buildConfigField(
            "String",
            "TEAMS_REMOTE_URL",
            "\"${localProps.getProperty("TEAMS_REMOTE_URL", "")}\""
        )
        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"$buildTimeStamp\""
        )
    }

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有签名配置就用之；没有时 release 包会是 unsigned 状态（装不上手机但能编译过）
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("io.coil-kt:coil-compose:2.6.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// 注：v0.1 开发期曾有 rotateLauncherIconColor task 自动换图标背景色（肉眼区分新旧 APK），
// 在改造 v1 公开版时已移除——公开版用户没有这个需求。如需调试期使用，参考 kanqiutixing-v0.1-personal/。

// ── 发布安全：release 包里不应内置 API token ───────────────────────────
// FOOTBALL_DATA_TOKEN 会被 BuildConfig 写进 dex，任何人 unzip APK 就能逆向取出，
// 然后用它耗尽你的 football-data.org 免费档配额（10 次/分钟）。
// 对外分发的 release 包请把 local.properties 里的该值清空，让用户在 App 内填自己的。
// 这里只告警不阻断——本地出包自测时带 token 是合理的。
val embeddedToken: String = localProps.getProperty("FOOTBALL_DATA_TOKEN", "") ?: ""
gradle.taskGraph.whenReady {
    val isReleaseBuild = allTasks.any {
        it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease")
    }
    if (isReleaseBuild && embeddedToken.isNotBlank()) {
        logger.warn(
            "\n" + "=".repeat(74) + "\n" +
                "[!] 你正在构建 release 包，但 local.properties 里有非空的 FOOTBALL_DATA_TOKEN。\n" +
                "    该 token 会被明文写进 dex，任何拿到 APK 的人都能逆向取出并使用。\n" +
                "    对外分发前：把 local.properties 的 FOOTBALL_DATA_TOKEN 清空后重新构建，\n" +
                "    让用户首次启动后在  设置 -> 数据源  填入自己的 token。\n" +
                "    验证：unzip -p <apk> classes*.dex | strings | grep -i \"你的token前8位\"\n" +
                "=".repeat(74)
        )
    }
}
