# ⚽ Football Live — IntelliJ IDEA Plugin

[简体中文](README.md) | **English**

Live football scores, schedules, standings, FIFA rankings, text-live commentary and news — for **all** competitions (Premier League, La Liga, Bundesliga, Serie A, Ligue 1, UEFA Champions League, CSL, World Cup and 80+ more) — right inside your IDE.

[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.3+-blue.svg)](https://www.jetbrains.com/idea/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()
[![Zero Config](https://img.shields.io/badge/Zero%20Config-✓-success.svg)]()

## Highlights

- **Zero configuration** — no API key, no token, no signup. Install and watch.
- **All competitions** — a global league selector syncs every tab; 88+ competitions from the Baidu Sports catalog.
- **Goal & kick-off notifications** — balloon notifications for your followed teams/leagues, click-through to the live tab.
- **Live score in the status bar** — auto-refreshing, followed teams shown first.
- **Match details** — predictions, intelligence, head-to-head record and text-live commentary with colored event badges.

> Note: match data comes from Baidu Sports (tiyu.baidu.com) — team names, commentary and news are in Chinese.

## Tabs

| Tab | What you get |
|---|---|
| 🗓️ Schedule | Fixtures for the selected competition(s), ±7 day window, arrow-key day switching |
| 🔴 Live | Live / upcoming / finished matches, auto-refresh every 60s, click a row for details |
| 🌐 Team rankings | Flattened standings across groups, sortable columns, click a team to jump to its fixtures |
| 🌍 FIFA rankings | Official FIFA ranking of 211 national teams |
| 📰 News | News cards for the selected competition, auto-refresh every 5 min |

## Install

1. `Settings → Plugins → Marketplace` → search **Football Live** (after review), or
2. Download `Football-Live-x.x.x.zip` from [Releases](https://github.com/CaroNiu/Football-Live/releases) → `Settings → Plugins → ⚙ → Install Plugin from Disk`.

## Build from source

Requires JDK 17 + Gradle 9.x (no wrapper committed):

```bash
git clone https://github.com/CaroNiu/Football-Live.git
cd Football-Live
gradle buildPlugin     # output: build/distributions/Football-Live-<version>.zip
```

CI/CD: pushing a `v*` tag publishes to the JetBrains Marketplace automatically (see `.github/workflows/build-and-publish.yml`).

## Feedback

Issues and PRs are welcome at the [issue tracker](https://github.com/CaroNiu/Football-Live/issues). UI strings are currently Chinese-first; an i18n toggle is on the roadmap.

## License

MIT License © 2026 Football Live Contributors
