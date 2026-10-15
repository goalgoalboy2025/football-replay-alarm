// 只用官方源：github.com 上的 CI（以及国际贡献者）必须能直接解析依赖。
//
// 国内网络如果解析很慢，不要改这个文件（改了会进仓库、拖慢所有人的 CI），
// 而是在 ~/.gradle/init.gradle.kts 里加镜像，只对这台机器生效：
//
//   settingsEvaluated {
//     pluginManagement.repositories { maven("https://maven.aliyun.com/repository/google"); ... }
//     dependencyResolutionManagement.repositories { maven("https://maven.aliyun.com/repository/google"); ... }
//   }
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Kanqiutixing"
include(":app")
