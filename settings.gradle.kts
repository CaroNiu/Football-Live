pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // IntelliJ Platform Gradle Plugin 传递依赖 (annotations/undertow/structure-* 等) 在 Maven Central,
        // 仅靠 gradlePluginPortal 在 CI 裸环境会解析失败 (本地有 init 脚本兜底掩盖了问题)
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
    }
}

rootProject.name = "Football-Live"
