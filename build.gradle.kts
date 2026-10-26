plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.fifa.plugin"
version = "4.0.0"

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
        pluginVerifier()
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
    pluginVerification {
        ides {
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")
        }
    }

    pluginConfiguration {
        name = "Football Live"
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "253.*"
        }
        description = """
            <p>Football Live — Live scores, schedules, standings, FIFA rankings,
            text-live commentary and news for ALL football competitions (Premier League, La Liga,
            Bundesliga, Serie A, Ligue 1, UEFA Champions League, World Cup, CSL and more),
            right inside your IDE. Zero configuration: no API key, no token, no signup required.</p>

            <p><b>全足球赛事实时比分 / 赛程 / 积分榜 / 文字直播 / 新闻 IDEA 插件</b></p>

            <p>Data sources: Baidu Sports (tiyu.baidu.com) + TheSportsDB public API.</p>

            <h3>Features 功能</h3>
            <ul>
                <li><b>StatusBar live score</b> — 状态栏实时比分 (多场进行中按关注优先展示)</li>
                <li><b>Schedule</b> — 全部足球赛事近 3 天赛程, 按日期切换, 跨联赛聚合</li>
                <li><b>Live</b> — 直播 / 即将开赛 / 已结束比赛 + 详情弹窗 (分析 + 文字直播)</li>
                <li><b>Standings</b> — 积分榜: 杯赛分组 / 联赛单表自适应</li>
                <li><b>Team rankings</b> — 跨组球队排名</li>
                <li><b>FIFA rankings</b> — FIFA 官方 211 队排名</li>
                <li><b>News</b> — 实时新闻卡片流 (关键词跟随所选赛事)</li>
                <li><b>League filter</b> — 全局赛事选择条 + 关注赛事</li>
            </ul>
        """.trimIndent()

        changeNotes = """
            <h3>4.0.0 — 全赛事足球直播</h3>
            <ul>
                <li>从世界杯单赛事升级为全部足球赛事: 五大联赛 / 欧冠 / 中超 / 世界杯等 (百度全赛事聚合数据源)</li>
                <li>新增全局赛事选择条: 一处切换, 赛程 / 直播 / 排名 / 球队榜 / 新闻全部联动</li>
                <li>积分榜自适应: 杯赛分组榜 (世界杯 A-L 组) 与联赛单表 (英超 20 队) 双形态</li>
                <li>关注赛事持久化 + 状态栏比分按关注球队/赛事优先展示</li>
                <li>插件 id 保持不变, 已安装用户平滑升级</li>
            </ul>

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

    // TEMP-TEST: runIde 自动打开空项目, 触发 StatusBar widget install → 全链路轮询 (验证完删除)
    runIde {
        args(listOf("D:/testProject/fifa/FIFA/build/idea-sandbox/test-project"))
    }
}
