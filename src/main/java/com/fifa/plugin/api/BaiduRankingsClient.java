package com.fifa.plugin.api;

import com.fifa.plugin.model.BaiduRankedTeam;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 百度体育积分榜专用客户端
 * 端点: /al/match?match=...&tab=排名&async_source=h5&tab_type=single&from=baidu_shoubai_na&getAll=1
 * <p>
 * 返回形态是 san SSR HTML, 排名数据在 <code>&lt;!--s-data:{JSON}--&gt;</code> 注释里,
 * 且两种嵌套并存 ( PoC 验证 2026-10-26 ):
 * <ul>
 *   <li>国家队杯赛 (世界杯): tplData.data.tabsList[0].tabList[].data[] 按 type=groupStage 分组</li>
 *   <li>职业联赛 (英超):   data.tabsList[0].tabList[0].data[0].list[] 单表, requestParams.season 给出赛季</li>
 * </ul>
 * 两种形态的每队 record[5] 数组同构, 解析逻辑统一走 {@link #parseTeam}
 */
@Service
public final class BaiduRankingsClient {

    private static final Logger LOG = Logger.getInstance(BaiduRankingsClient.class);
    private static final String HOST = "https://tiyu.baidu.com";
    private static final String DEFAULT_MATCH = "世界杯";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public BaiduRankingsClient() {
    }

    /** 拉取世界杯排名 (兼容旧调用) */
    public RankingBundle fetch() throws IOException {
        return fetch(DEFAULT_MATCH);
    }

    /**
     * 拉取指定赛事的排名 (matchName 如 "英超" / "世界杯")
     */
    public RankingBundle fetch(String matchName) throws IOException {
        String url = HOST + "/al/match"
                + "?match=" + enc(matchName != null && !matchName.isEmpty() ? matchName : DEFAULT_MATCH)
                + "&tab=" + enc("排名")
                + "&&async_source=h5&tab_type=single&from=baidu_shoubai_na"
                + "&request__node__params=1&getAll=1";
        String body = httpGet(url);
        if (body == null) return new RankingBundle(Collections.emptyMap(), Collections.emptyList());
        try {
            // async 端点返回纯 JSON (tplData.data...), 兜底 s-data HTML — 双模式统一入口
            JsonObject root = BaiduMatchListClient.parseResponseRoot(body);
            if (root == null) return new RankingBundle(Collections.emptyMap(), Collections.emptyList());
            return parse(root);
        } catch (Exception e) {
            LOG.warn("Baidu rankings parse error", e);
            return new RankingBundle(Collections.emptyMap(), Collections.emptyList());
        }
    }

    private RankingBundle parse(JsonObject root) {
        Map<String, List<BaiduRankedTeam>> groups = new LinkedHashMap<>();
        List<String> knockoutStages = new ArrayList<>();
        if (root == null) return new RankingBundle(groups, knockoutStages);

        // tabsList 位置双形态: tplData.data.tabsList (世界杯) / data.tabsList (联赛)
        JsonArray topTabs = findTabsList(root);
        for (JsonElement topEl : topTabs) {
            if (!topEl.isJsonObject()) continue;
            JsonObject top = topEl.getAsJsonObject();
            // 有些场景直接 top.data[] (没有再嵌 tabList), 有些是 top.tabList[].data[]
            JsonArray innerTabs = top.has("tabList") && !top.get("tabList").isJsonNull()
                    ? top.getAsJsonArray("tabList") : new JsonArray();
            if (innerTabs.size() == 0 && top.has("data") && top.get("data").isJsonArray()) {
                innerTabs = top.getAsJsonArray("data");
            }

            for (JsonElement tl : innerTabs) {
                if (!tl.isJsonObject()) continue;
                JsonObject tab = tl.getAsJsonObject();
                String type = tab.has("type") && !tab.get("type").isJsonNull() ? tab.get("type").getAsString() : "";
                JsonArray data2 = tab.has("data") && !tab.get("data").isJsonNull()
                        ? tab.getAsJsonArray("data") : new JsonArray();

                if ("groupStage".equals(type)) {
                    // 世界杯形态: data2[] 每项是一个小组 {title:"A组", list:[4 队]}
                    for (JsonElement grp : data2) {
                        if (!grp.isJsonObject()) continue;
                        JsonObject g = grp.getAsJsonObject();
                        String title = g.has("title") && !g.get("title").isJsonNull() ? g.get("title").getAsString() : "";
                        String key = groupKey(title);
                        if (key == null) continue;
                        JsonArray list = g.has("list") && !g.get("list").isJsonNull()
                                ? g.getAsJsonArray("list") : new JsonArray();
                        List<BaiduRankedTeam> teams = new ArrayList<>();
                        for (JsonElement te : list) {
                            BaiduRankedTeam t = parseTeam(te);
                            if (t != null) teams.add(t);
                        }
                        groups.put(key, teams);
                    }
                } else if ("knockoutStage".equals(type)) {
                    if (tab.has("tabs") && tab.get("tabs").isJsonArray()) {
                        for (JsonElement s : tab.getAsJsonArray("tabs")) {
                            if (!s.isJsonNull()) knockoutStages.add(s.getAsString());
                        }
                    }
                } else if (data2.size() > 0) {
                    // 联赛形态 (type="1" 等): data2[] 每项 {title:"联赛", list:[20 队]}
                    for (JsonElement secEl : data2) {
                        if (!secEl.isJsonObject()) continue;
                        JsonObject sec = secEl.getAsJsonObject();
                        JsonArray list = sec.has("list") && !sec.get("list").isJsonNull()
                                ? sec.getAsJsonArray("list") : new JsonArray();
                        if (list.size() == 0) continue;
                        String key = groupKey(str(sec, "title"));
                        List<BaiduRankedTeam> teams = new ArrayList<>();
                        for (JsonElement te : list) {
                            BaiduRankedTeam t = parseTeam(te);
                            if (t != null) teams.add(t);
                        }
                        if (!teams.isEmpty()) groups.put(key, teams);
                    }
                }
            }
        }
        return new RankingBundle(groups, knockoutStages);
    }

    /**
     * tabsList 位置兼容: 优先 tplData.data.tabsList (世界杯), 回退 data.tabsList (英超等联赛)
     */
    private static JsonArray findTabsList(JsonObject root) {
        JsonObject tplData = root.has("tplData") && !root.get("tplData").isJsonNull()
                ? root.getAsJsonObject("tplData") : null;
        if (tplData != null) {
            JsonObject td = tplData.has("data") && tplData.get("data").isJsonObject()
                    ? tplData.getAsJsonObject("data") : null;
            if (td != null && td.has("tabsList") && td.get("tabsList").isJsonArray()) {
                return td.getAsJsonArray("tabsList");
            }
        }
        JsonObject data = root.has("data") && root.get("data").isJsonObject()
                ? root.getAsJsonObject("data") : null;
        if (data != null && data.has("tabsList") && data.get("tabsList").isJsonArray()) {
            return data.getAsJsonArray("tabsList");
        }
        return new JsonArray();
    }

    /**
     * 组 key: "A组" → "A" (紧凑, 兼容旧 UI); 其他标题 (如 "联赛"/"总榜") 原样作 key
     */
    private static String groupKey(String title) {
        if (title == null || title.isEmpty()) return null;
        String t = title.trim();
        if (t.length() == 2 && t.endsWith("组")) {
            char c = t.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                return String.valueOf(Character.toUpperCase(c));
            }
        }
        return t;
    }

    private BaiduRankedTeam parseTeam(JsonElement te) {
        if (!te.isJsonObject()) return null;
        JsonObject o = te.getAsJsonObject();
        if (!o.has("record") || !o.get("record").isJsonArray()) return null;
        JsonArray rec = o.getAsJsonArray("record");
        if (rec.size() < 5) return null;

        BaiduRankedTeam t = new BaiduRankedTeam();
        t.teamId = o.has("teamId") && !o.get("teamId").isJsonNull() ? o.get("teamId").getAsString() : "";
        String status = o.has("fillsName") && !o.get("fillsName").isJsonNull() ? o.get("fillsName").getAsString() : "";
        t.statusText = status;
        // 晋级状态泛化: 联赛是 "欧冠正赛"/"降级区" 等, 杯赛是 "晋级32强"/"晋级待定"
        t.qualified = status.contains("晋级") && !status.contains("待定");
        t.uncertain = status.contains("待定");

        if (rec.get(0).isJsonObject()) {
            JsonObject ti = rec.get(0).getAsJsonObject();
            t.name = ti.has("name") && !ti.get("name").isJsonNull() ? ti.get("name").getAsString() : "";
            t.logo = ti.has("logo") && !ti.get("logo").isJsonNull() ? ti.get("logo").getAsString() : "";
            t.rank = toInt(ti.has("rank") && !ti.get("rank").isJsonNull() ? ti.get("rank").getAsString() : null);
            t.stroke = toInt(ti.has("stroke") && !ti.get("stroke").isJsonNull() ? ti.get("stroke").toString() : null);
        }
        t.played = toInt(rec.get(1).isJsonPrimitive() ? rec.get(1).getAsString() : null);
        if (rec.get(2).isJsonPrimitive()) {
            String[] wdl = rec.get(2).getAsString().split("/");
            if (wdl.length == 3) { t.won = toInt(wdl[0]); t.draw = toInt(wdl[1]); t.lost = toInt(wdl[2]); }
        }
        if (rec.get(3).isJsonPrimitive()) {
            String[] gfga = rec.get(3).getAsString().split("/");
            if (gfga.length == 2) { t.goalsFor = toInt(gfga[0]); t.goalsAgainst = toInt(gfga[1]); }
        }
        t.points = toInt(rec.get(4).isJsonPrimitive() ? rec.get(4).getAsString() : null);
        return t;
    }

    private static int toInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
        return o.get(key).getAsString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String httpGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148")
                // 不带 text/html, 避开企业零信任代理对"网页浏览"型请求的劫持 (见 BaiduMatchListClient 同注释)
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

    public static class RankingBundle {
        public final Map<String, List<BaiduRankedTeam>> groups;
        public final List<String> knockoutStages;
        public RankingBundle(Map<String, List<BaiduRankedTeam>> groups, List<String> knockoutStages) {
            this.groups = groups;
            this.knockoutStages = knockoutStages;
        }
    }

    // ============================================================
    //  球队榜 (跨组按积分排序)
    // ============================================================

    /**
     * 拉取"球队榜": 把积分榜 flatten 后, 按 积分 / 净胜球 / 进球数 / 队名 跨组排序
     * <p>
     * 杯赛 (世界杯) = 12 组 flatten; 联赛 (英超) = 单表原样 (flatten 后即本身).
     *
     * @return 跨组排序后的全部球队, 积分降序. 返回空列表表示无可用数据.
     */
    public List<BaiduRankedTeam> fetchTeamRankings() throws IOException {
        return fetchTeamRankings(DEFAULT_MATCH);
    }

    public List<BaiduRankedTeam> fetchTeamRankings(String matchName) throws IOException {
        RankingBundle bundle = fetch(matchName);
        List<BaiduRankedTeam> all = new ArrayList<>();
        for (List<BaiduRankedTeam> group : bundle.groups.values()) {
            if (group == null) continue;
            all.addAll(group);
        }
        // 排序: 积分 > 净胜球 > 进球数 > 队名 (与 UI 期望顺序一致)
        all.sort((a, b) -> {
            int c = Integer.compare(b.points, a.points);
            if (c != 0) return c;
            int aDiff = a.goalsFor - a.goalsAgainst;
            int bDiff = b.goalsFor - b.goalsAgainst;
            c = Integer.compare(bDiff, aDiff);
            if (c != 0) return c;
            c = Integer.compare(b.goalsFor, a.goalsFor);
            if (c != 0) return c;
            return String.CASE_INSENSITIVE_ORDER.compare(
                    a.name != null ? a.name : "", b.name != null ? b.name : "");
        });
        return all;
    }
}
