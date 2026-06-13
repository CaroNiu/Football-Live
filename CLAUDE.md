# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**FIFA World Cup 2026** is an IntelliJ IDEA plugin (Java 17, Gradle Kotlin DSL) that surfaces live scores, today's match schedule, group / FIFA standings, team rankings, and World Cup news inside the IDE. Data is fetched from **two** public APIs — no registration, no user-supplied token.

- Plugin ID: `com.fifa.plugin.worldcup2026`
- Target IDE: IntelliJ IDEA Community **2024.3** (`sinceBuild=243`, `untilBuild=253.*`)
- Data sources:
  - **TheSportsDB** `https://www.thesportsdb.com/api/v1/json/3` (public key `3`, hardcoded — English team names, no auth)
  - **Baidu Sports** `https://tiyu.baidu.com` (mobile UA, no auth — Chinese team names, live match details, FIFA rankings, news)
- Default league: FIFA World Cup (`leagueId=4429`, `season=2026`)
- **Zero configuration** — `plugin.xml` has no API-key input; `FifaSettingsConfigurable` exposes only UI preferences.

## Build & Development Commands

```bash
# Build plugin distribution into build/distributions/
./gradlew buildPlugin

# Launch a sandboxed IDE with the plugin installed for manual testing
./gradlew runIde

# Compile main sources only
./gradlew compileJava

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.fifa.plugin.SomeTestClass"

# Verify the plugin against the target IDE version
./gradlew verifyPlugin

# Publish to JetBrains Marketplace
./gradlew publishPlugin
```

> Windows: use `gradlew.bat` instead of `./gradlew`.

## Architecture

The plugin follows the standard IntelliJ Platform pattern: extension points declared in `src/main/resources/META-INF/plugin.xml`, services resolved via `ApplicationManager.getApplication().getService(...)` (application-scoped) or `project.getService(...)` (project-scoped). **No user-configurable data-source fields exist** — base URLs and league IDs are hardcoded in their respective client.

### Data flow

```
[TheSportsDB public API, key=3, no auth]        [Baidu Sports tiyu.baidu.com, mobile UA, no auth]
        │ OkHttp + Gson                                  │ OkHttp + Gson + Jsoup
        ▼                                                ▼
FootballDataClient (api/)              BaiduSportsClient / BaiduLiveMatchClient /
        │                               BaiduRankingsClient / BaiduFifaRankingsClient
        │                                       │
        └──────────►  MatchCache (cache/, Caffeine)  ◄────┘
                              ▲  (5 min matches / 10 min standings fallback)
                              │
              TournamentManager (core/)  ── 3 pollers:
                  30s  refreshLiveMatches    (TheSportsDB — currently returns empty; see Notes)
                  5min refreshTodayMatches   (TheSportsDB)
                  10min refreshStandings     (TheSportsDB)
                              │
        ┌─────────────────────┴─────────────────────┐
        ▼                                           ▼
StatusBarWidget                                FifaToolWindowFactory
  LiveScoreWidgetFactory                          (single tool window, 6 tabs)
                                                  ├─ 赛程        SchedulePanel      今日/按日切换
                                                  ├─ 直播        LivePanel          直播/即将/已结束 + 详情 dialog
                                                  ├─ 排名        StandingPanel      12 组积分榜 (百度)
                                                  ├─ 球队榜      TeamRankingPanel   48 队跨组 (本地计算)
                                                  ├─ FIFA排名    FifaRankingPanel   FIFA 官方排名 (百度)
                                                  └─ 新闻        NewsPanel          百度实时新闻

FifaProjectListener (core/) ──► start/stop TournamentManager polling on project lifecycle
FifaSettingsState (settings/) ──► persistent UI prefs only (fifa-worldcup.xml)
FifaSettingsConfigurable ──────► Settings → Tools → FIFA World Cup 2026 (no token field)
```

Per-panel independent pollers (daemon threads, not TournamentManager):
- `LivePanel`  →  `BaiduSportsClient.fetchSchedule()` every 60s (`FIFA-Live-Poller`)
- `NewsPanel`  →  `FootballDataClient.fetchBaiduNews(20)` every 300s (`FIFA-News-Poller`)

### Package layout (`com.fifa.plugin`)

| Package | Role |
|---|---|
| `api` | 5 REST clients. **No auth header on any** (TheSportsDB key embedded in URL; Baidu uses mobile UA). |
| `api.FootballDataClient` | TheSportsDB: 赛程 (eventsday / eventspastleague / eventsnextleague), round 拉取, 本地计算积分榜 (`computeGroupStandings`), 球队榜 (`fetchTeamRankings`), 球员榜 (`fetchPlayerRankings` — 实际返参赛队列表), 百度新闻 (`fetchBaiduNews` — 名字虽在 `FootballDataClient`, 实际是百度接口) |
| `api.BaiduSportsClient` | 百度 `/al/api/match/schedules` 赛程 (含 `ScheduleBundle.matches` + `DateLabel[]` 用于日期选择器) |
| `api.BaiduLiveMatchClient` | 百度 `/al/live/detail` 单场比赛详情 (分析 tab) — Intelligence / HomeRecord / Result 预测 |
| `api.BaiduRankingsClient` | 百度 `/al/match?tab=排名` 12 小组积分榜 (含晋级状态 `fillsName`) |
| `api.BaiduFifaRankingsClient` | 百度 `/al/match?tab=FIFA排名` FIFA 官方排名 (211 队) |
| `cache` | `MatchCache` — Caffeine: matches (5 min TTL, 100 entries), standings (10 min TTL, 10 entries). |
| `core` | `TournamentManager` (`@Service`, 单线程 `ScheduledExecutorService` 名为 `FIFA-Poller`, 三轮询: 30s/5min/10min). `FifaProjectListener` (`@Service implements ProjectManagerListener`) 触发 start/stop. |
| `model` | Gson DTOs: `Match` (含 `TeamInfo` / `MatchScore` / `ScoreDetail` 内部类), `MatchListResponse`, `Standing`, `StandingResponse`, `ComputedStanding` (本地计算积分), `BaiduRankedTeam`. `Match` 在反序列化后由 `FootballDataClient.normalizeAndFilter()` 调用 `normalize()` 把 TheSportsDB 原始字段映射到 UI 视图模型. |
| `settings` | `FifaSettingsState` (`@State` → `fifa-worldcup.xml`) + `FifaSettingsConfigurable` (Settings UI). **无 API token 字段**, 仅有 UI 偏好. |
| `ui.action` | `RefreshAction` — Tools 菜单手动刷新, 弹 BALLOON 通知. |
| `ui.statusbar` | `LiveScoreWidgetFactory` — StatusBar widget, 格式 `⚽ ARG 2:1 FRA (67')`. **当前因 TheSportsDB `/livescore.php` 已 404, 默认显示 "No live matches"**. |
| `ui.toolwindow` | `FifaToolWindowFactory` — **单 ToolWindow 容器**, 顶部一个共享刷新按钮 (按当前可见 Tab 调用 `panel.refresh()`), 主体 6 Tab. |
| `ui.toolwindow.SchedulePanel` | 赛程 — 按日期切换, 走百度 (BaiduSportsClient) |
| `ui.toolwindow.LivePanel` | 直播 — 列表 + 双击/单击行 → `LiveMatchDialog` 弹窗 (含 igence/homeRecord/result 三类分析) |
| `ui.toolwindow.StandingPanel` | 12 小组积分榜 — 走百度 (BaiduRankingsClient) |
| `ui.toolwindow.TeamRankingPanel` | 48 队跨组排名 — 走 TheSportsDB (本地计算) |
| `ui.toolwindow.FifaRankingPanel` | FIFA 官方排名 — 走百度 (BaiduFifaRankingsClient) |
| `ui.toolwindow.NewsPanel` | 百度新闻卡片 — 走百度 (FootballDataClient.fetchBaiduNews), 5 min 自动刷新 |
| `ui.toolwindow.LiveMatchDialog` | 比赛详情模态弹窗 (LivePanel 点击行触发) |
| `util` | `TeamNames` — 12 组共 ~50 队 EN→CN 翻译表 (LinkedHashMap + 大小写不敏感 fallback). 把 TheSportsDB 英文队名统一成中文展示. |

### Extension points registered in `plugin.xml`

- `statusBarWidgetFactory` → `LiveScoreWidgetFactory` (order=`after Encoding`)
- `toolWindow` → `FifaToolWindowFactory` (id `FIFA`, anchor right, icon `icons/fifaSchedule.svg`)
- `applicationConfigurable` → `FifaSettingsConfigurable` (displayName "FIFA World Cup 2026")
- `notificationGroup` → `FIFA.Notification` (BALLOON)
- `applicationService` ×8 →
  - `FootballDataClient`
  - `BaiduSportsClient`
  - `BaiduRankingsClient`
  - `BaiduFifaRankingsClient`
  - `BaiduLiveMatchClient`
  - `MatchCache`
  - `TournamentManager`
  - `FifaSettingsState`
- `projectService` ×1 → `FifaProjectListener`
- `action` → `RefreshAction` (Tools 菜单, icon `icons/fifaRefresh.svg`)

### Plugin icon

IntelliJ Platform 2024.3 removed the top-level `<icon>` element from `plugin.xml`. The IDE auto-loads the plugin icon from `src/main/resources/META-INF/pluginIcon.svg`.

## Key Conventions

- All services are annotated `@Service` and resolved through `ApplicationManager.getApplication().getService(...)`. Don't instantiate them with `new`.
- UI work must hop to the EDT with `SwingUtilities.invokeLater(...)` before touching Swing components (see `SchedulePanel` / `StandingPanel` / `LivePanel` / `NewsPanel`).
- Background I/O and polling run on dedicated executors; do not block the EDT or the IDE's read action. The three pollers are: `FIFA-Poller` (TournamentManager, 守护项目生命周期), `FIFA-Live-Poller` (LivePanel, daemon), `FIFA-News-Poller` (NewsPanel, daemon).
- Model classes (`Match`, `Standing`) have a transient view-model + a `normalize()` method called by `FootballDataClient` after Gson deserialization, to map raw TheSportsDB fields to the UI's expected shape.
- `Match.status` values used by the UI: `SCHEDULED | TIMED | IN_PLAY | PAUSED | FINISHED`. `Match.normalize()` maps from TheSportsDB's `strStatus` ("Not Started" / "1H" / "HT" / "FT" / "Match Finished" / etc.) into these canonical values.
- `Match.stage` values used by the UI: `GROUP_STAGE | LAST_32 | LAST_16 | QUARTER_FINALS | SEMI_FINALS | FINAL | THIRD_PLACE`. `Match.normalize()` maps from TheSportsDB's `intRound` ("1"-"3" or "Quarter Final" / "Semi Final" / etc.).
- 百度客户端用 `Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 ...)` 移动端 UA + `Accept-Language: zh-CN`, TheSportsDB 用 `FIFA-WorldCup-2026-Plugin/1.0`.
- 比赛 ID: 百度走 `match.key` (base64), 用于拉 `/al/live/detail`. TheSportsDB 走 `idEvent`. `Match.baiduMatchId` 是 transient 字段, 走完 TheSportsDB 时为空, 走完百度时填 base64.
- 积分榜 fallback 链: 主路径 = TheSportsDB rounds 1-3 本地计算 (`computeGroupStandings`); StatusBar 走 `BaiduSportsClient.fetchSchedule()` 拿 IN_PLAY 状态的 match; 12 小组 UI 走百度 (BaiduRankingsClient).
- The default token-free public data sources make registration unnecessary; if you need a different data source, change the `BASE_URL` / `HOST` constants and the `normalize()` / `mapStage` / `mapMatchStatus` mappings in the client classes.
- Java source/target: 17. Source files must be UTF-8 (the compile task enforces this and adds `-Xlint:unchecked`).
- All user-facing labels and code comments are in Chinese (zh-CN); preserve this style for new UI strings.

## Notes / Known Gaps

- `FootballDataClient.fetchLiveMatches()` returns `Collections.emptyList()` — the upstream `/livescore.php` endpoint is 404. The StatusBar widget therefore permanently shows `⚽ No live matches`. Real-time data is currently sourced from `BaiduSportsClient.fetchSchedule()` filtered by `IN_PLAY` (used by `LivePanel`).
- The 3 polling executors do not respect an "off-match-day" heuristic — they all run regardless of calendar.
- Each of the 5 clients constructs its own `OkHttpClient` instance; a shared `HttpClient` bean would be a clean simplification.
- The player rankings tab (`FootballDataClient.fetchPlayerRankings`) is a stub — it returns the list of teams that have appeared in rounds 1-3 rather than real per-player stats (TheSportsDB public API does not expose per-fixture player goal/assist data).
- `NewsPanel` defines its own `@NotNull` annotation as a source-retention marker instead of importing `org.jetbrains.annotations.NotNull` — a small cleanup target.
