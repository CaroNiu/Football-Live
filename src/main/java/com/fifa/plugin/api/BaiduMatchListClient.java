package com.fifa.plugin.api;

import com.fifa.plugin.model.League;
import com.fifa.plugin.model.Match;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 百度体育"全部赛事"数据源客户端
 * <p>
 * 百度移动站 (tiyu.baidu.com) 的赛程页/赛事目录页是 san SSR 渲染,
 * 初始数据以 <code>&lt;!--s-data:{JSON}--&gt;</code> 注释内嵌在 HTML 里。
 * 本客户端负责提取并解析两份 s-data:
 * <ul>
 *   <li><b>赛事目录</b>: GET /al/match/list (手机 UA)
 *       <br>s-data 路径: matchList.tabList[title=足球].children[0].list[]
 *       <br>每项: name(赛事名/logo/text "今日1场"/DayCount/DayIngCount/Order/Category)</li>
 *   <li><b>全足球聚合赛程</b>: GET / (移动站首页, 近 3 天: 昨天/今天/明天)
 *       <br>s-data 路径: data.liveList[].data[] 按日分组, 每日 list[] 为跨联赛比赛行
 *       <br>比赛行结构与 /al/api/match/schedules 同构 (leftLogo/rightLogo/matchStatus/key),
 *       额外带 matchType("football"/"basketball"/...) 与 game(赛事短名), 过滤 matchType=football</li>
 * </ul>
 */
@Service
public final class BaiduMatchListClient {

    private static final Logger LOG = Logger.getInstance(BaiduMatchListClient.class);

    private static final String HOST = "https://tiyu.baidu.com";
    /** s-data 注释起始标记 (百度 san 框架 SSR 初始数据) */
    private static final String S_DATA_MARK = "<!--s-data:";
    /**
     * async 参数: 走纯 JSON API 形态 (tplData.data 下同构数据), 不依赖 SSR HTML。
     * 实测企业零信任代理 (QAX) 会劫持 SSR 页面型请求返回壳响应, async JSON 形态稳定可用
     */
    private static final String ASYNC_PARAMS = "?async_source=h5&from=baidu_shoubai_na&request__node__params=1";
    private static final String UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    /** 首页聚合共享缓存: 赛程/直播/状态栏各自轮询同一接口, 60s 内合并为一次上游请求 */
    private static final long AGG_TTL_MS = TimeUnit.SECONDS.toMillis(60);
    private volatile BaiduSportsClient.ScheduleBundle cachedBundle;
    private volatile long bundleFetchedAt = 0;
    private final Object bundleLock = new Object();

    /**
     * 拉取足球赛事目录 (含今日场次统计), 按 Order 升序
     */
    public List<League> fetchFootballLeagues() throws IOException {
        String body = httpGet(HOST + "/al/match/list" + ASYNC_PARAMS);
        JsonObject root = parseResponseRoot(body);
        if (root == null) return Collections.emptyList();
        try {
            return parseLeagueCatalog(root);
        } catch (Exception e) {
            LOG.warn("Baidu league catalog parse error", e);
            return Collections.emptyList();
        }
    }

    /**
     * 拉取全部足球比赛 (近 3 天聚合, 跨所有联赛), 含按日分组信息
     * <p>
     * 60s 共享缓存: 各面板轮询间隔都 ≥30s, 合并后上游请求 ≤1 次/分钟
     */
    public BaiduSportsClient.ScheduleBundle fetchAllFootball() throws IOException {
        long now = System.currentTimeMillis();
        BaiduSportsClient.ScheduleBundle cached = cachedBundle;
        if (cached != null && now - bundleFetchedAt < AGG_TTL_MS) {
            return cached;
        }
        synchronized (bundleLock) {
            // 双重检查: 并发面板线程只放一个进去打网络
            if (cachedBundle != null && System.currentTimeMillis() - bundleFetchedAt < AGG_TTL_MS) {
                return cachedBundle;
            }
            String body = httpGet(HOST + "/" + ASYNC_PARAMS);
            JsonObject root = parseResponseRoot(body);
            if (root == null) {
                // 拉取失败时保留旧缓存 (打上过期时间戳避免死循环重试), 让 UI 沿用上次数据
                if (cachedBundle != null) {
                    bundleFetchedAt = System.currentTimeMillis();
                    return cachedBundle;
                }
                return new BaiduSportsClient.ScheduleBundle(Collections.emptyList(), Collections.emptyList());
            }
            try {
                BaiduSportsClient.ScheduleBundle parsed = parseAllFootball(root);
                if (!parsed.matches.isEmpty() || !parsed.dateLabels.isEmpty()) {
                    cachedBundle = parsed;
                    bundleFetchedAt = System.currentTimeMillis();
                }
                return parsed;
            } catch (Exception e) {
                LOG.warn("Baidu all-football parse error", e);
                return new BaiduSportsClient.ScheduleBundle(Collections.emptyList(), Collections.emptyList());
            }
        }
    }

    // ============================================================
    //  解析
    // ============================================================

    /**
     * 双模式解析响应根: 优先 async 纯 JSON ({@code {"tplData":{"data":...}}}),
     * 兜底 SSR HTML 里的 s-data 注释 (extractSData)。两种形态的数据结构同构
     */
    static JsonObject parseResponseRoot(String body) {
        if (body == null || body.isEmpty()) return null;
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonElement el = JsonParser.parseString(trimmed);
                return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
            } catch (Exception e) {
                LOG.warn("async json parse error", e);
                return null;
            }
        }
        return extractSData(body);
    }

    /**
     * 归一化数据节点: async 形态在 {@code root.tplData.data}, s-data 形态直接挂在 root 的子键下
     */
    private static JsonObject dataNode(JsonObject root) {
        if (root.has("tplData") && root.get("tplData").isJsonObject()) {
            JsonObject tpl = root.getAsJsonObject("tplData");
            if (tpl.has("data") && tpl.get("data").isJsonObject()) {
                return tpl.getAsJsonObject("data");
            }
        }
        return root;
    }

    /**
     * 从目录 (async JSON / s-data) 提取足球分类下的赛事列表
     * <p>
     * async: {@code tplData.data.tabList[title=足球].children[0].list[]};
     * s-data: {@code matchList.tabList[...]} — 同构
     */
    private static List<League> parseLeagueCatalog(JsonObject root) {
        List<League> out = new ArrayList<>();
        JsonObject node = dataNode(root);
        JsonObject tabHolder = node.has("tabList") ? node
                : node.has("matchList") && node.get("matchList").isJsonObject()
                        ? node.getAsJsonObject("matchList") : null;
        if (tabHolder == null) return out;

        JsonArray tabList = tabHolder.has("tabList") && tabHolder.get("tabList").isJsonArray()
                ? tabHolder.getAsJsonArray("tabList") : new JsonArray();
        for (JsonElement tabEl : tabList) {
            if (!tabEl.isJsonObject()) continue;
            JsonObject tab = tabEl.getAsJsonObject();
            String title = tab.has("title") && !tab.get("title").isJsonNull() ? tab.get("title").getAsString() : "";
            if (!"足球".equals(title)) continue;

            JsonArray children = tab.has("children") && tab.get("children").isJsonArray()
                    ? tab.getAsJsonArray("children") : new JsonArray();
            for (JsonElement chEl : children) {
                if (!chEl.isJsonObject()) continue;
                JsonArray list = chEl.getAsJsonObject().has("list") && chEl.getAsJsonObject().get("list").isJsonArray()
                        ? chEl.getAsJsonObject().getAsJsonArray("list") : new JsonArray();
                for (JsonElement itemEl : list) {
                    League lg = parseLeagueItem(itemEl);
                    if (lg != null) out.add(lg);
                }
            }
        }
        out.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        return out;
    }

    private static League parseLeagueItem(JsonElement el) {
        if (!el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        // 只收足球分类 (目录里混有篮球/电竞等, 顶层 tabs 已分, 双保险)
        String cat = str(o, "Category");
        if (!cat.isEmpty() && !"football".equals(cat)) return null;
        String name = str(o, "name");
        if (name.isEmpty()) return null;
        return new League(
                name,
                str(o, "logo"),
                parseIntOr(str(o, "DayCount"), -1),
                parseIntOr(str(o, "DayIngCount"), 0),
                parseIntOr(str(o, "Order"), 999));
    }

    /**
     * 从首页聚合数据 (async JSON / s-data) 提取全部足球比赛, 按日分组
     * <p>
     * async: {@code tplData.data.liveList[0].data[]}; s-data: {@code data.liveList[0].data[]} — 同构
     */
    private static BaiduSportsClient.ScheduleBundle parseAllFootball(JsonObject root) {
        List<Match> allMatches = new ArrayList<>();
        List<BaiduSportsClient.DateLabel> dateLabels = new ArrayList<>();

        JsonObject node = dataNode(root);
        JsonObject liveHolder = node.has("liveList") ? node
                : node.has("data") && node.get("data").isJsonObject()
                        ? node.getAsJsonObject("data") : null;
        if (liveHolder == null) return new BaiduSportsClient.ScheduleBundle(allMatches, dateLabels);

        JsonArray liveList = liveHolder.has("liveList") && liveHolder.get("liveList").isJsonArray()
                ? liveHolder.getAsJsonArray("liveList") : new JsonArray();
        // liveList[0] = "热门" (近 3 天全量比赛), 其余分组 (如 "全部") 为客户端懒加载, s-data 里为空
        for (JsonElement groupEl : liveList) {
            if (!groupEl.isJsonObject()) continue;
            JsonArray days = groupEl.getAsJsonObject().has("data") && groupEl.getAsJsonObject().get("data").isJsonArray()
                    ? groupEl.getAsJsonObject().getAsJsonArray("data") : new JsonArray();
            for (JsonElement dayEl : days) {
                if (!dayEl.isJsonObject()) continue;
                JsonObject day = dayEl.getAsJsonObject();
                String dayDate = str(day, "time");           // "2026-10-25"
                String weekday = str(day, "weekday");        // "昨天"
                if (dayDate.isEmpty()) continue;

                JsonArray list = day.has("list") && day.get("list").isJsonArray()
                        ? day.getAsJsonArray("list") : new JsonArray();
                int footballCount = 0;
                for (JsonElement mEl : list) {
                    if (!mEl.isJsonObject()) continue;
                    // 只保留足球 (页面混合篮球/电竞等)
                    if (!"football".equals(str(mEl.getAsJsonObject(), "matchType"))) continue;
                    Match m = BaiduSportsClient.parseBaiduMatch(mEl, dayDate);
                    if (m != null) {
                        allMatches.add(m);
                        footballCount++;
                    }
                }
                BaiduSportsClient.DateLabel dl = new BaiduSportsClient.DateLabel();
                try {
                    dl.date = LocalDate.parse(dayDate, DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception e) {
                    continue;
                }
                dl.suffix = footballCount + "场";
                dl.desc = weekday;
                dl.disabled = false;
                dateLabels.add(dl);
            }
            break; // 只取第一个分组 (热门), 避免与空"全部"分组重复
        }
        return new BaiduSportsClient.ScheduleBundle(allMatches, dateLabels);
    }

    // ============================================================
    //  s-data 提取
    // ============================================================

    /**
     * 从 SSR HTML 里提取 <code>&lt;!--s-data:{JSON}--&gt;</code> 注释中的 JSON
     * <p>
     * 用 JsonReader 只读第一个 JSON 值, 不要求读到结尾, 天然忽略尾部的 "--&gt;"
     */
    static JsonObject extractSData(String html) {
        if (html == null || html.isEmpty()) return null;
        int mark = html.indexOf(S_DATA_MARK);
        if (mark < 0) {
            // 带上长度和片段, 便于区分反爬壳页 / 限频页 / 其它异常响应
            String head = html.substring(0, Math.min(120, html.length())).replaceAll("\\s+", " ");
            LOG.warn("s-data mark not found in SSR html (len=" + html.length() + ") head: " + head);
            return null;
        }
        int brace = html.indexOf('{', mark);
        if (brace < 0) return null;
        try {
            JsonReader reader = new JsonReader(new StringReader(html.substring(brace)));
            // s-data JSON 后面跟着 "-->" 尾巴, 必须 lenient 才能容忍 (setLenient(boolean) 在 Gson 2.11 已过时)
            reader.setStrictness(com.google.gson.Strictness.LENIENT);
            JsonElement el = JsonParser.parseReader(reader);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            LOG.warn("s-data json parse error", e);
            return null;
        }
    }

    // ---- 工具 ----

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
        return o.get(key).getAsString();
    }

    private static int parseIntOr(String s, int def) {
        try { return (s == null || s.isEmpty()) ? def : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private String httpGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                // 不带 text/html: 实测带 text/html 的"网页浏览"型请求会被企业零信任代理 (QAX) 劫持降级,
                // 返回 HTTP/1.0 JSON 网关响应; API 型请求放行. 百度对 Accept 不做严格内容协商, 仍返回 SSR HTML
                .header("Accept", "application/json,text/plain,*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .get()
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOG.warn("Baidu HTTP " + response.code() + " for " + url);
                return null;
            }
            return response.body() != null ? response.body().string() : null;
        }
    }
}
