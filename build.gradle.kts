plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.fifa.plugin"
version = "3.0.2"

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
            <p>FIFA World Cup 2026 — Live scores, schedules, group standings, FIFA rankings,
            text-live commentary and news for the 2026 USA-Canada-Mexico World Cup, right inside your IDE.
            Zero configuration: no API key, no token, no signup required.</p>

            <p><b>2026 美加墨世界杯实时比分 / 赛程 / 积分榜 / 文字直播 / 新闻 IDEA 插件</b></p>

            <p>Data sources: Baidu Sports (tiyu.baidu.com) + TheSportsDB public API.</p>

            <h3>Features 功能</h3>
            <ul>
                <li><b>StatusBar live score</b> — 状态栏实时比分</li>
                <li><b>Schedule</b> — 按日期切换的世界杯赛程</li>
                <li><b>Live</b> — 直播 / 即将开赛 / 已结束比赛 + 详情弹窗 (分析 + 文字直播)</li>
                <li><b>Standings</b> — 12 个小组实时积分榜</li>
                <li><b>Team rankings</b> — 全 48 队跨组排名</li>
                <li><b>FIFA rankings</b> — FIFA 官方 211 队排名</li>
                <li><b>News</b> — 世界杯实时新闻卡片流</li>
            </ul>
        """.trimIndent()

        changeNotes = """
            <h3>3.0.2</h3>
            <ul>
                <li>Made source code repository public to comply with JetBrains Marketplace review requirements</li>
                <li>Added <code>PRIVACY.md</code> at the repository root to document the zero-data-collection policy</li>
            </ul>

            <h3>3.0.1</h3>
            <ul>
                <li>Fixed "球队榜" tab: switched data source from local TheSportsDB round aggregation to Baidu 12-group standings flattening. Previously returned empty when no rounds had finished; now always shows the current team ranking.</li>
            </ul>

            <h3>3.0.0</h3>
            <ul>
                <li>Removed deprecated <code>URL(String)</code> constructor in news image loading (replaced with <code>URI.create().toURL()</code>)</li>
                <li>Cleaned up Marketplace verifier warnings: 0 deprecated / 0 scheduled-for-removal API usages</li>
            </ul>

            <h3>2.0.0</h3>
            <ul>
                <li>Added 6 functional tabs: Schedule / Live / Standings / Team Rankings / FIFA Rankings / News</li>
                <li>Match detail dialog with analysis (predictions / intelligence / head-to-head) and text-live commentary</li>
                <li>StatusBar live score widget</li>
                <li>Replaced deprecated <code>EditorBasedWidget</code> base class and <code>MultipleTextValuesPresentation</code></li>
                <li>Removed legacy <code>ProjectManagerListener</code> (scheduled for removal in newer platform)</li>
                <li>Cleaned up source-attribution UI clutter from each tab</li>
                <li>Zero configuration — no API key required</li>
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
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }
}
