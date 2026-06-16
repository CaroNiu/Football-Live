# Privacy Policy / 隐私政策

**Plugin:** FIFA World Cup 2026
**Effective date / 生效日期:** 2026-06-16
**Contact / 联系方式:** [Open an issue on GitHub](https://github.com/CaroNiu/FIFA-World-Cup-2026/issues)

---

## English

This plugin is designed with a **zero-data-collection** policy. It does not gather, transmit, sell, or share any user information.

### Data the plugin does NOT collect

The plugin does **not** access, store, or transmit any of the following:

- Personal information (name, email, account, IP address, location)
- IDE or project content (source code, file paths, workspace metadata)
- Usage telemetry, crash reports, analytics, or diagnostic data
- Authentication credentials of any kind

### Network requests the plugin makes

The plugin makes outbound HTTPS requests to two **public, unauthenticated, third-party APIs** solely to fetch FIFA World Cup 2026 match data:

| Endpoint | Purpose | Auth |
|---|---|---|
| `https://www.thesportsdb.com/api/v1/json/3/...` | Schedules, rounds, team data | Public demo key `3` hardcoded in URL — no user token |
| `https://tiyu.baidu.com/al/api/match/...` | Live scores, standings, rankings, news | Mobile User-Agent only — no account |

These requests contain **no user-identifying information** beyond standard HTTP headers (User-Agent, Accept-Language). The plugin does not set any cookies, authentication headers, or tracking identifiers on these requests.

### Local storage

The only on-disk write is `<IDE config dir>/options/fifa-worldcup.xml`, which stores **UI preferences only** (e.g. last selected tab). This file never leaves your machine.

### In-memory cache

Match data is cached in memory (Caffeine, max 200 entries, 5–10 min TTL) inside the IDE's JVM. The cache is **process-local** and is discarded when the IDE exits. It is not persisted and not transmitted anywhere.

### Third-party services

The third-party APIs above have their own privacy practices. This plugin does not control them; by enabling the plugin you acknowledge that requests are sent to those endpoints per their respective terms.

### Changes to this policy

Any updates to this policy will be committed to this file in the source repository, with the effective date updated accordingly.

---

## 中文

本插件采用**零数据收集**策略,不会获取、上传、出售或分享任何用户信息。

### 插件**不收集**的数据

- 个人信息(姓名、邮箱、账号、IP、位置)
- IDE 或项目内容(源码、文件路径、工作区元数据)
- 使用埋点、崩溃报告、分析或诊断数据
- 任何形式的身份认证凭据

### 插件发出的网络请求

插件仅出于获取 2026 世界杯赛事数据的目的,向以下**公共、无需登录的第三方 API** 发起 HTTPS 请求:

| 端点 | 用途 | 鉴权 |
|---|---|---|
| `https://www.thesportsdb.com/api/v1/json/3/...` | 赛程、轮次、球队数据 | 公共示例 key `3` 直接写在 URL 里,无需用户 token |
| `https://tiyu.baidu.com/al/api/match/...` | 直播、积分榜、排名、新闻 | 仅移动端 User-Agent,无需账号 |

这些请求中**不含任何用户身份信息**,只携带标准 HTTP 头(User-Agent、Accept-Language)。插件不设置任何 Cookie、鉴权头或追踪标识符。

### 本地存储

插件唯一的磁盘写入是 `<IDE 配置目录>/options/fifa-worldcup.xml`,只保存**界面偏好**(例如上次选中的 Tab)。该文件不会离开本机。

### 内存缓存

比赛数据缓存在 IDE JVM 内存中(Caffeine,最多 200 条,5–10 分钟过期),**仅在当前进程内**,IDE 退出即清空,不持久化、不外传。

### 第三方服务

上述第三方 API 有各自的隐私惯例,本插件无法控制其行为。启用本插件即视为你同意向这些端点发起请求,并接受其服务条款。

### 政策变更

本政策如有更新,会在本仓库的此文件中提交,并同步更新生效日期。
