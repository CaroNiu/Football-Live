# ⚽ FIFA World Cup 2026 — IDEA 插件

> 在 IntelliJ IDEA 中实时追踪 2026 美加墨世界杯 — 比分、赛程、积分榜、文字直播、新闻一站式查看。

[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.3+-blue.svg)](https://www.jetbrains.com/idea/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()
[![Zero Config](https://img.shields.io/badge/Zero%20Config-✓-success.svg)]()

---

## 🌟 项目简介

**FIFA World Cup 2026** 是一款专为开发者设计的 IntelliJ IDEA 插件,在你写代码的同时关注 2026 美加墨世界杯赛事动态 — 完全无需注册、无需 API token、零配置开箱即用。

数据由 **百度体育 (tiyu.baidu.com)** + **TheSportsDB 公共 API** 双源驱动,中文展示、移动端 UA 直连、自动轮询。

---

## ✨ 核心功能

### 📅 6 大功能 Tab(右侧 ToolWindow)

| Tab | 说明 | 数据源 |
|---|---|---|
| 🗓️ **赛程** | 按日期切换查看世界杯赛程,显示主客队、比分、阶段、状态 | 百度体育 |
| 🔴 **直播** | 列出正在直播 / 24h 内即将开赛 / 已结束的比赛,**60 秒自动刷新**;点击行弹窗看详情 | 百度体育 |
| 🏆 **排名** | 12 个小组实时积分榜(W/D/L/GF/GA/Pts + 晋级状态) | 百度体育 |
| 🌐 **球队榜** | 全部 48 队跨组排名(本地从赛果实时计算) | TheSportsDB |
| 🌍 **FIFA排名** | FIFA 官方 211 队全球排名(含变化箭头) | 百度体育 |
| 📰 **新闻** | 世界杯实时新闻卡片流,**5 分钟自动刷新**,点击跳浏览器 | 百度体育 |

### 💬 比赛详情弹窗(点击直播 Tab 任意一行)

- **分析 Tab**
  - 📊 **网友预测** — 主胜 / 平 / 客胜 % + 各队胜率箭头
  - 🧠 **智能预测** — 有利情报 / 不利情报双视角,主客队各自多条文字情报
  - 📜 **历史交锋 / 主队战绩 / 客队战绩** — 胜率汇总(`67% (4胜0平2负)`)+ 近期 6 场比赛比分(主队赢高亮标色)

- **文字直播 Tab**
  - ⏱️ 按时间正序展示比赛事件流
  - 🎨 类型彩色徽章(🔴 进球 / 🟡 黄牌 / 🟥 红牌 / 🔵 换人 / 🟣 点球 / ⚫ VAR)
  - 完整中文描述(分钟 + 球员 + 助攻者)

### 📊 StatusBar 实时比分 Widget

底部状态栏显示当前直播比赛比分 — 例如 `⚽ ARG 2:1 FRA (67')`,点击立即手动刷新。

---

## 🚀 安装方式

### 方式一:从 JetBrains Marketplace 安装(发布后)

1. `Settings (Ctrl+Alt+S) → Plugins → Marketplace`
2. 搜索 `FIFA World Cup 2026`
3. 点击 `Install`,重启 IDE

### 方式二:本地 zip 包安装

1. 从 [Releases](https://github.com/fifa-worldcup-plugin/releases) 下载最新 `FIFA-World-Cup-2026-x.x.x.zip`
2. `Settings → Plugins → ⚙ → Install Plugin from Disk...`
3. 选择 zip 文件,重启 IDE

### 方式三:从源码构建

```bash
git clone https://github.com/fifa-worldcup-plugin/FIFA.git
cd FIFA
./gradlew buildPlugin
# 产物在 build/distributions/FIFA-World-Cup-2026-1.0.0-SNAPSHOT.zip
```

> Windows 用户使用 `gradlew.bat` 替换 `./gradlew`。

---

## 📖 使用指南

### 第一次打开

安装完成后,IDEA 会自动:
- ✅ 在右侧工具栏注册 **FIFA** ToolWindow(⚽ 图标)
- ✅ 在 StatusBar(右下角)挂上 **实时比分 Widget**
- ✅ 启动后台轮询(30s 实时 / 5min 今日 / 10min 积分榜)

**你不需要做任何配置** — 没有 API key,没有用户名密码,没有 cookie,直接看比赛。

### 打开 FIFA 工具窗

| 操作 | 入口 |
|---|---|
| 点击右侧工具栏 ⚽ 图标 | 直接展开 |
| 菜单 `View → Tool Windows → FIFA` | 显式打开 |
| 快捷键 | 可在 `Settings → Keymap` 中绑定 |

### 切换 Tab

工具窗顶部 6 个 Tab 横向排列,点击切换。**每个 Tab 都有独立的数据源、独立的刷新机制**。

### 手动刷新

工具窗右上角有一个统一的 **🔄 刷新** 按钮。它会按当前可见 Tab 触发对应面板的 `refresh()`,只刷新当前看的 Tab,不会重复拉取所有数据。

### 看比赛详情

1. 切到 **直播** Tab
2. 看到正在进行 / 即将开赛 / 已结束的比赛列表
3. **点击任意一行** → 弹出比赛详情对话框
4. 在对话框顶部 Tab 切换 **分析** ↔ **文字直播**

### Tools 菜单刷新

`Tools → Refresh FIFA Data` 同样可以触发数据刷新,并通过 BALLOON 通知反馈结果。

---

## 🎨 截图

> 待补充实际截图

```
┌──────────────────────────────────────────────────────┐
│  ⚽ FIFA World Cup 2026                 [🔄 刷新]    │
├──────────────────────────────────────────────────────┤
│ [赛程] [直播] [排名] [球队榜] [FIFA排名] [新闻]      │
├──────────────────────────────────────────────────────┤
│  ⚽ 直播中 / 即将开赛 / 已结束                       │
│  ┌─────┬──────┬──────┬──────┬────────┬────────┐    │
│  │时间 │ 主队 │ 比分 │ 客队 │ 状态   │ 阶段   │    │
│  ├─────┼──────┼──────┼──────┼────────┼────────┤    │
│  │09:00│ 美国 │ 2-0  │巴拉圭│🔴直播中│小组赛 │    │
│  │11:30│ 摩洛哥│ vs  │ 加拿大│⏳未开赛│小组赛│    │
│  └─────┴──────┴──────┴──────┴────────┴────────┘    │
│  ✓ 百度体育 · 8 场可关注                             │
└──────────────────────────────────────────────────────┘
```

---

## 🏗️ 技术架构

### 兼容性

| 项 | 要求 |
|---|---|
| IntelliJ IDEA | Community / Ultimate **2024.3+** (`sinceBuild=243`, `untilBuild=253.*`) |
| Java | 17 |
| 操作系统 | Windows / macOS / Linux 全平台 |
| 网络 | 仅需访问 `tiyu.baidu.com` 与 `www.thesportsdb.com` |

### 数据流

```
[百度体育 / TheSportsDB JSON]
       │ OkHttp + Gson
       ▼
   *Client (@Service ×5)  ──►  MatchCache (Caffeine 5min/10min TTL)
       │                          ▲
       ▼                          │  (回退读)
TournamentManager (单线程 ScheduledExecutorService)
   ├─ 30s   实时比分
   ├─ 5min  今日赛程
   └─ 10min 积分榜
       │
       ├─► StatusBar Widget
       └─► FifaToolWindowFactory → 6 个 Tab
```

### 核心模块

| 包 | 职责 |
|---|---|
| `api/` | 5 个 REST 客户端(`FootballDataClient`、`BaiduSportsClient`、`BaiduLiveMatchClient`、`BaiduRankingsClient`、`BaiduFifaRankingsClient`) |
| `cache/` | `MatchCache` — Caffeine 内存缓存(5min/10min TTL) |
| `core/` | `TournamentManager` 调度 + `FifaProjectListener` 生命周期 |
| `model/` | Gson DTO + `normalize()` 字段映射 |
| `settings/` | UI 偏好持久化(`fifa-worldcup.xml`) |
| `ui/toolwindow/` | 6 个 Panel + 1 个比赛详情 Dialog |
| `ui/statusbar/` | StatusBar Widget |
| `util/TeamNames` | 12 组共 50+ 队 EN→CN 翻译表 |

---

## 🛠️ 开发命令

```bash
# 编译
./gradlew compileJava

# 跑单测
./gradlew test

# 启动沙盒 IDE 实测
./gradlew runIde

# 打包插件
./gradlew buildPlugin
# 输出 build/distributions/FIFA-World-Cup-2026-1.0.0-SNAPSHOT.zip

# 验证插件兼容性
./gradlew verifyPlugin

# 发布到 Marketplace
./gradlew publishPlugin
```

---

## 🔧 配置

> **本插件零配置** — 没有任何 API key 或 token 需要填。

唯一可配置的偏好(`Settings → Tools → FIFA World Cup 2026`):

- 仅 UI 偏好(开关之类),不影响数据来源
- 持久化到 `<config>/options/fifa-worldcup.xml`

如需切换到自有数据源,改 `FootballDataClient.BASE_URL` / `BaiduSportsClient.HOST` 常量后重新构建即可。

---

## 📦 依赖

| 库 | 版本 | 用途 |
|---|---|---|
| OkHttp | 4.12.0 | HTTP 客户端 |
| Gson | 2.11.0 | JSON 解析 |
| Caffeine | 3.1.8 | 内存缓存 |
| Jsoup | 1.18.1 | HTML 解析(备用) |

---

## ❓ FAQ

### Q1: 为什么 StatusBar 一直显示 "No live matches"?
A: TheSportsDB 的 `/livescore.php` 端点已 404,实时数据已迁移到百度体育。直播 Tab 内容是正常的;StatusBar 后续会切换数据源。

### Q2: 数据更新频率是多少?
A: 三档轮询:
- **实时比分** — 30 秒
- **今日赛程** — 5 分钟
- **积分榜** — 10 分钟
- **直播 Tab** — 60 秒(独立轮询器)
- **新闻 Tab** — 5 分钟(独立轮询器)

### Q3: 球队名英文 / 中文混杂?
A: 百度数据天然中文,TheSportsDB 是英文。`util/TeamNames` 维护 12 组 50+ 队的 EN→CN 翻译表统一展示;若发现某队漏译,可补到 `TeamNames.EN_TO_CN`。

### Q4: 插件吃多少资源?
A: 5 个 OkHttpClient 实例 + 3 个轮询线程 + Caffeine(<200 entries),内存占用 < 30 MB,CPU 几乎可忽略。

### Q5: 公司网络无法访问 tiyu.baidu.com / thesportsdb.com 怎么办?
A: 目前两个数据源都为公网。后续版本计划支持自定义 BASE_URL 与代理设置。

### Q6: 想看某场比赛的回放视频?
A: 不支持。本插件聚焦数据展示,视频播放请用浏览器打开新闻卡片或百度体育原站。

---

## 🐛 反馈与贡献

- 报 Bug / 提需求 → [GitHub Issues](https://github.com/fifa-worldcup-plugin/issues)
- 提交 PR → 欢迎 fork 并 Pull Request
- 翻译队名 → 编辑 `src/main/java/com/fifa/plugin/util/TeamNames.java`

---

## 📄 License

MIT License © 2026 FIFA Plugin Contributors

---

## 🙏 致谢

- 数据来源:[百度体育](https://tiyu.baidu.com) + [TheSportsDB](https://www.thesportsdb.com)
- IDEA 插件平台:[IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij)
- 灵感来自所有热爱足球与代码的开发者 ⚽💻
