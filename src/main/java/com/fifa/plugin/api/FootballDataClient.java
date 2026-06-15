package com.fifa.plugin.api;

import com.fifa.plugin.model.ComputedStanding;
import com.fifa.plugin.model.Match;
import com.fifa.plugin.model.MatchListResponse;
import com.fifa.plugin.model.Standing;
import com.fifa.plugin.model.StandingResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * TheSportsDB 公共数据源客户端
 * <p>
 * 使用 TheSportsDB 公共 API key = "3", 无需注册 / 无需用户配置
 * 文档: https://www.thesportsdb.com/api.php
 * <p>
 * 端点:
 * <ul>
 *   <li>每日赛事: /eventsday.php?d=YYYY-MM-DD&amp;s=Soccer</li>
 *   <li>实时比分: /livescore.php?s=Soccer</li>
 *   <li>积分榜:   /lookuptable.php?l={leagueId}&amp;s={season}</li>
 * </ul>
 */
@Service
public final class FootballDataClient {

    private static final Logger LOG = Logger.getInstance(FootballDataClient.class);

    private static final String BASE_URL = "https://www.thesportsdb.com/api/v1/json/3";

    /** FIFA World Cup 联赛 ID (TheSportsDB 约定) */
    private static final String DEFAULT_LEAGUE_ID = "4429";

    /** 当前 World Cup 赛季 */
    private static final String DEFAULT_SEASON = "2026";

    private final OkHttpClient httpClient;
    private final Gson gson;

    private String leagueId = DEFAULT_LEAGUE_ID;
    private String season = DEFAULT_SEASON;

    public FootballDataClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder().create();
    }

    // ---- 配置 (代码层, 不可由用户改) ----

    public void setLeagueId(String leagueId) { this.leagueId = leagueId; }
    public String getLeagueId() { return leagueId; }

    public void setSeason(String season) { this.season = season; }
    public String getSeason() { return season; }

    /** 兼容旧 API: 旧代码用 competitionCode 作为赛事代码 */
    public void setCompetitionCode(String code) { this.leagueId = code; }
    public String getCompetitionCode() { return leagueId; }

    // ---- 数据接口 ----

    /**
     * 获取今日所有足球比赛 (用于赛程 Tab)
     * <p>
     * eventsday.php?d=... 对 World Cup 时区数据不完整, 改用 eventspastleague +
     * eventsnextleague 拿全量, 再按今天日期过滤
     */
    public List<Match> fetchTodayMatches() throws IOException {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<Match> all = fetchAllLeagueEvents();
        List<Match> out = new ArrayList<>();
        for (Match m : all) {
            String matchDate = extractDate(m);
            if (today.equals(matchDate)) {
                out.add(m);
            }
        }
        // 按本地时间排序
        out.sort((x, y) -> {
            String xt = x.getUtcDate() != null ? x.getUtcDate() : "";
            String yt = y.getUtcDate() != null ? y.getUtcDate() : "";
            return xt.compareTo(yt);
        });
        return out;
    }

    /**
     * 拉取 World Cup 全部比赛 (过去 + 未来) 并去重
     */
    private List<Match> fetchAllLeagueEvents() throws IOException {
        java.util.Map<String, Match> dedup = new java.util.LinkedHashMap<>();
        for (String endpoint : new String[]{"eventspastleague.php", "eventsnextleague.php"}) {
            String body = httpGet(BASE_URL + "/" + endpoint + "?id=" + leagueId);
            if (body == null) continue;
            try {
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonArray events = root.has("events") && !root.get("events").isJsonNull()
                        ? root.getAsJsonArray("events") : new JsonArray();
                List<Match> list = parseEventsArray(events);
                for (Match m : normalizeAndFilter(list)) {
                    String key = m.getId() > 0 ? String.valueOf(m.getId())
                            : (safe(m.getHomeTeam() != null ? m.getHomeTeam().getShortName() : null)
                                    + "|" + safe(m.getAwayTeam() != null ? m.getAwayTeam().getShortName() : null)
                                    + "|" + safe(m.getUtcDate()));
                    dedup.putIfAbsent(key, m);
                }
            } catch (Exception e) {
                LOG.warn(endpoint + " parse error", e);
            }
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * 从 Match 中提取日期字符串 (yyyy-MM-dd)
     */
    private static String extractDate(Match m) {
        String utc = m.getUtcDate();
        if (utc != null && utc.length() >= 10) {
            return utc.substring(0, 10);
        }
        return null;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /**
     * 把 JSON events 数组解成 List&lt;Match&gt;
     * <p>
     * 关键: gson.fromJson(JsonArray, MatchListResponse.class) 会失败,
     * 因为 MatchListResponse 是带 events 字段的对象. 这里改用数组反序列化
     */
    private List<Match> parseEventsArray(JsonArray events) {
        if (events == null || events.size() == 0) return Collections.emptyList();
        Match[] arr;
        try {
            arr = gson.fromJson(events, Match[].class);
        } catch (Exception e) {
            LOG.warn("parseEventsArray failed", e);
            return Collections.emptyList();
        }
        if (arr == null) return Collections.emptyList();
        List<Match> list = new ArrayList<>(arr.length);
        for (Match m : arr) {
            if (m != null) list.add(m);
        }
        return list;
    }

    /**
     * 获取所有实时比赛 (用于 StatusBar Widget)
     * <p>
     * 旧端点 /livescore.php 已废弃 (404), 这里直接返空.
     * StatusBar 改从 BaiduSportsClient.fetchSchedule() 里取 IN_PLAY 状态的 match
     */
    public List<Match> fetchLiveMatches() throws IOException {
        return Collections.emptyList();
    }

    /**
     * 缓存: round → 该 round 全部比赛. 第一次拉后复用
     */
    private final java.util.Map<Integer, List<Match>> roundCache = new java.util.HashMap<>();

    /**
     * 拉取并缓存指定 round 全部 WC 比赛
     */
    private List<Match> getRoundCached(int round) throws IOException {
        List<Match> cached = roundCache.get(round);
        if (cached != null) return cached;
        List<Match> fresh = fetchRound(round);
        roundCache.put(round, fresh);
        return fresh;
    }

    /**
     * 获取指定日期的全部 WC 比赛 (用于赛程 Tab)
     * <p>
     * 数据源: rounds 1-3 (涵盖小组赛 6/11-7/2 共 48 场), 之后 round 4+
     * 是淘汰赛. 用 roundCache 避免重复 HTTP
     */
    public List<Match> fetchScheduleByDate(LocalDate date) throws IOException {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<Match> result = new ArrayList<>();

        // 小组赛 round 1-3
        for (int round = 1; round <= 3; round++) {
            for (Match m : getRoundCached(round)) {
                if (m.getUtcDate() != null && m.getUtcDate().startsWith(dateStr)) {
                    result.add(m);
                }
            }
        }
        // 淘汰赛 round 4-8 (按需拉, 找不到则跳过)
        for (int round = 4; round <= 8; round++) {
            try {
                for (Match m : getRoundCached(round)) {
                    if (m.getUtcDate() != null && m.getUtcDate().startsWith(dateStr)) {
                        result.add(m);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        result.sort((x, y) -> {
            String xt = x.getUtcDate() != null ? x.getUtcDate() : "";
            String yt = y.getUtcDate() != null ? y.getUtcDate() : "";
            return xt.compareTo(yt);
        });
        return result;
    }

    /**
     * 清空 round 缓存 (供刷新按钮使用)
     */
    public void clearScheduleCache() {
        roundCache.clear();
    }

    /**
     * 获取积分榜 (用于积分榜 Tab)
     * <p>
     * 注意: TheSportsDB 的 lookuptable 对 FIFA World Cup 2026 返回的全是
     * "Playoffs" 占位 + 0 数据, 不可用. 这里改成从 rounds 1-3 赛果实时计算
     */
    public StandingResponse fetchStandings() throws IOException {
        StandingResponse response = new StandingResponse();

        // 兜底: 仍然拉一次 lookuptable, 留作原始数据 (UI 不一定用)
        try {
            String body = httpGet(BASE_URL + "/lookuptable.php?l=" + leagueId + "&s=" + season);
            if (body != null) {
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonArray table = root.has("table") && !root.get("table").isJsonNull()
                        ? root.getAsJsonArray("table") : new JsonArray();
                Standing[] arr = gson.fromJson(table, Standing[].class);
                List<Standing> list = new ArrayList<>();
                if (arr != null) {
                    for (Standing s : arr) {
                        s.normalize();
                        list.add(s);
                    }
                }
                response.setStandings(list);
            }
        } catch (Exception e) {
            LOG.warn("lookuptable fallback failed", e);
        }

        // 主路径: 从 rounds 1-3 算
        try {
            Map<String, List<ComputedStanding>> grouped = computeGroupStandings();
            response.setGroupStandings(grouped);
        } catch (Exception e) {
            LOG.warn("computeGroupStandings failed", e);
        }
        return response;
    }

    /**
     * 从 group stage rounds 1-3 赛果计算每组积分
     * <p>
     * 算法: 两遍扫描. 第一遍枚举所有参赛队, 每队 0 分布底;
     * 第二遍对已结束比赛 applyResult. 这样没开打的组也显示完整 4 队
     */
    private Map<String, List<ComputedStanding>> computeGroupStandings() throws IOException {
        // group letter → teamName → ComputedStanding
        Map<String, Map<String, ComputedStanding>> groups = new java.util.LinkedHashMap<>();

        // Pass 1: 枚举所有参赛队 (rounds 1-3 涵盖全部 12 组 × 4 队 = 48 队)
        for (int round = 1; round <= 3; round++) {
            List<Match> events = fetchRound(round);
            for (Match m : events) {
                if (m.getGroup() == null || m.getGroup().isEmpty()) continue;
                if (m.getHomeTeam() == null || m.getAwayTeam() == null) continue;

                Map<String, ComputedStanding> table = groups.computeIfAbsent(
                        m.getGroup(), k -> new java.util.LinkedHashMap<>());

                table.computeIfAbsent(
                        m.getHomeTeam().getShortName(),
                        k -> new ComputedStanding(
                                m.getHomeTeam().getShortName(),
                                String.valueOf(m.getHomeTeam().getId()),
                                m.getHomeTeam().getCrest()));
                table.computeIfAbsent(
                        m.getAwayTeam().getShortName(),
                        k -> new ComputedStanding(
                                m.getAwayTeam().getShortName(),
                                String.valueOf(m.getAwayTeam().getId()),
                                m.getAwayTeam().getCrest()));
            }
        }

        // Pass 2: 应用已结束比赛的结果
        for (int round = 1; round <= 3; round++) {
            List<Match> events = fetchRound(round);
            for (Match m : events) {
                if (m.getGroup() == null || m.getGroup().isEmpty()) continue;
                if (!"FINISHED".equalsIgnoreCase(m.getStatus())) continue;
                if (m.getHomeTeam() == null || m.getAwayTeam() == null) continue;
                if (m.getScore() == null || m.getScore().getFullTime() == null) continue;

                Integer h = m.getScore().getFullTime().getHome();
                Integer a = m.getScore().getFullTime().getAway();
                if (h == null || a == null) continue;

                Map<String, ComputedStanding> table = groups.get(m.getGroup());
                if (table == null) continue;
                ComputedStanding home = table.get(m.getHomeTeam().getShortName());
                ComputedStanding away = table.get(m.getAwayTeam().getShortName());
                if (home == null || away == null) continue;
                home.applyResult(h, a);
                away.applyResult(a, h);
            }
        }

        // 排序: 积分 > 净胜球 > 进球数 > 队名
        Map<String, List<ComputedStanding>> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ComputedStanding>> e : groups.entrySet()) {
            List<ComputedStanding> list = new ArrayList<>(e.getValue().values());
            list.sort((x, y) -> {
                int c = Integer.compare(y.points, x.points);
                if (c != 0) return c;
                c = Integer.compare(y.goalDifference, x.goalDifference);
                if (c != 0) return c;
                c = Integer.compare(y.goalsFor, x.goalsFor);
                if (c != 0) return c;
                return x.teamName.compareTo(y.teamName);
            });
            out.put(e.getKey(), list);
        }
        return out;
    }

    /**
     * 获取指定 round 的所有比赛 (用于计算积分)
     */
    private List<Match> fetchRound(int round) throws IOException {
        String url = BASE_URL + "/eventsround.php?id=" + leagueId + "&r=" + round + "&s=" + season;
        String body = httpGet(url);
        if (body == null) return Collections.emptyList();
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray events = root.has("events") && !root.get("events").isJsonNull()
                    ? root.getAsJsonArray("events") : new JsonArray();
            return normalizeAndFilter(parseEventsArray(events));
        } catch (Exception e) {
            LOG.warn("fetchRound " + round + " parse error", e);
            return Collections.emptyList();
        }
    }

    // ============================================================
    //  球员榜单 (Player Rankings)
    // ============================================================

    /**
     * 球员榜单: TheSportsDB 公共 API 没有按场次的球员进球/助攻数据,
     * 改用 rounds 1-3 赛程里枚举的全部 48 支参赛队, 列出队名/缩写/队徽/已比赛场次
     */
    public List<PlayerItem> fetchPlayerRankings(int limit) throws IOException {
        java.util.Map<String, PlayerItem> byTeamId = new java.util.LinkedHashMap<>();
        for (int round = 1; round <= 3; round++) {
            List<Match> events = fetchRound(round);
            for (Match m : events) {
                addOrUpdatePlayer(byTeamId, m.getHomeTeam(), m.getHomeTeamName());
                addOrUpdatePlayer(byTeamId, m.getAwayTeam(), m.getAwayTeamName());
            }
        }

        List<PlayerItem> list = new ArrayList<>(byTeamId.values());
        list.sort((x, y) -> {
            int xv = x.statValue == null ? 0 : x.statValue;
            int yv = y.statValue == null ? 0 : y.statValue;
            int c = Integer.compare(yv, xv);
            if (c != 0) return c;
            return x.teamName.compareTo(y.teamName);
        });
        return list.subList(0, Math.min(limit, list.size()));
    }

    private void addOrUpdatePlayer(java.util.Map<String, PlayerItem> map, Match.TeamInfo team, String fallbackName) {
        // 多重 fallback: team.shortName → fallbackName (raw homeTeamName/awayTeamName from JSON) → 跳过
        String name = (team != null && team.getShortName() != null && !team.getShortName().isEmpty())
                ? team.getShortName()
                : (fallbackName != null && !fallbackName.isEmpty() ? fallbackName : null);
        if (name == null) return;

        String tla = team != null ? team.getTla() : null;
        String badge = team != null ? team.getCrest() : null;

        PlayerItem p = map.computeIfAbsent(name, k -> {
            PlayerItem x = new PlayerItem();
            x.playerName = name;
            x.teamName = name;
            x.tla = tla;
            x.badge = badge;
            x.statValue = 0;
            return x;
        });
        p.statValue = (p.statValue == null ? 0 : p.statValue) + 1;
    }

    public static class PlayerItem {
        public String playerName;
        public String teamName;
        public String tla;
        public String badge;
        public String strCountry;
        public Integer statValue;  // 已开赛场次
    }

    // ============================================================
    //  新闻 (News) - 百度体育 /al/api/realtime JSON
    // ============================================================

    /**
     * 拉取 FIFA World Cup 2026 的最新新闻 (来自百度体育)
     * <p>
     * 端点: <code>/al/api/realtime?&amp;pn=10&amp;word=世界杯</code>
     * 响应: <pre>{ "data": { "data": [ {title, link, source, endTime, factorTime, img[]} ] } }</pre>
     */
    public List<NewsItem> fetchBaiduNews(int limit) throws IOException {
        List<NewsItem> out = new ArrayList<>();
        String url = "https://tiyu.baidu.com/al/api/realtime?&pn=10&word=%E4%B8%96%E7%95%8C%E6%9D%AF";
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148")
                .header("Accept", "application/json,text/plain,*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOG.warn("Baidu news fetch failed: HTTP " + response.code());
                return out;
            }
            String body = response.body() != null ? response.body().string() : "{}";
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("data") || root.get("data").isJsonNull()) return out;
            JsonObject dataObj = root.getAsJsonObject("data");
            if (!dataObj.has("data") || dataObj.get("data").isJsonNull()) return out;
            JsonArray arr = dataObj.getAsJsonArray("data");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                NewsItem n = new NewsItem();
                n.title = o.has("title") && !o.get("title").isJsonNull() ? o.get("title").getAsString() : "";
                n.url = o.has("link") && !o.get("link").isJsonNull() ? o.get("link").getAsString() : "";
                n.source = o.has("source") && !o.get("source").isJsonNull() ? o.get("source").getAsString() : "";
                n.publishedAt = o.has("endTime") && !o.get("endTime").isJsonNull() ? o.get("endTime").getAsString() : "";
                n.factorTime = o.has("factorTime") && !o.get("factorTime").isJsonNull() ? o.get("factorTime").getAsString() : "";
                if (o.has("img") && !o.get("img").isJsonNull() && o.get("img").isJsonArray()) {
                    JsonArray imgs = o.getAsJsonArray("img");
                    if (imgs.size() > 0 && !imgs.get(0).isJsonNull()) {
                        n.imageUrl = imgs.get(0).getAsString();
                    }
                }
                if (!n.title.isEmpty()) out.add(n);
            }
        } catch (Exception e) {
            LOG.warn("Baidu news parse error", e);
        }
        return out.subList(0, Math.min(limit, out.size()));
    }

    public static class NewsItem {
        public String title;
        public String summary;       // (百度接口无摘要, 留空)
        public String url;
        public String publishedAt;   // 相对时间 "2小时前"
        public String factorTime;    // 时间戳
        public String source;        // "泉州晚报" 等
        public String imageUrl;
    }

    // ---- 内部 ----

    /**
     * 调用 normalize() 并过滤出 World Cup 2026 的比赛
     */
    private List<Match> normalizeAndFilter(List<Match> matches) {
        if (matches == null) return Collections.emptyList();
        List<Match> out = new ArrayList<>();
        for (Match m : matches) {
            m.normalize();
            // 过滤: 只保留 FIFA World Cup 2026 的比赛
            // JSON 字段名是 strLeague (SerializedName 注解)
            String league = null;
            try {
                JsonElement leagueEl = JsonParser.parseString(gson.toJson(m)).getAsJsonObject().get("strLeague");
                if (leagueEl != null && !leagueEl.isJsonNull()) league = leagueEl.getAsString();
            } catch (Exception ignored) {
            }
            if (league == null) {
                out.add(m); // 拿不到 league 字段时也保留, 由 UI 决定是否展示
            } else if (league.toLowerCase().contains("world cup")) {
                out.add(m);
            }
        }
        return out;
    }

    private String httpGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "FIFA-WorldCup-2026-Plugin/1.0")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOG.warn("HTTP " + response.code() + " for " + url);
                return null;
            }
            return response.body() != null ? response.body().string() : null;
        }
    }
}
