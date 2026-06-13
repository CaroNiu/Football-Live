plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.fifa.plugin"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        bundledPlugin("com.intellij.java")
    }

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.11.0")

    // In-memory cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // HTML parsing (从百度体育 SSR 页面提取数据)
    implementation("org.jsoup:jsoup:1.18.1")
}

intellijPlatform {
    pluginConfiguration {
        name = "FIFA World Cup 2026"
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "253.*"
        }
        description = """
            2026美加墨世界杯实时比分、赛程、积分榜 IDEA 插件
            <br/>
            数据来源: football-data.org
            <br/><br/>
            功能:
            <ul>
                <li>实时比分 - StatusBar 显示正在进行的比赛</li>
                <li>赛程浏览 - 小组赛到决赛全赛程</li>
                <li>积分榜 - 12个小组实时排名</li>
                <li>比赛提醒 - 关注球队开赛通知</li>
            </ul>
        """.trimIndent()

        changeNotes = """
            <h3>1.0.0</h3>
            <ul>
                <li>Initial release</li>
                <li>赛事赛程查看</li>
                <li>实时比分显示</li>
                <li>小组积分榜</li>
            </ul>
        """.trimIndent()

        vendor {
            name = "FIFA Plugin"
            url = "https://github.com/fifa-worldcup-plugin"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
