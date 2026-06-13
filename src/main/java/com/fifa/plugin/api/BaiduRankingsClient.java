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
 */
@Service
public final class BaiduRankingsClient {

    private static final Logger LOG = Logger.getInstance(BaiduRankingsClient.class);
    private static final String HOST = "https://tiyu.baidu.com";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public BaiduRankingsClient() {
    }

    public RankingBundle fetch() throws IOException {
        String url = HOST + "/al/match"
                + "?match=" + enc("世界杯")
                + "&tab=" + enc("排名")
                + "&&async_source=h5&tab_type=single&from=baidu_shoubai_na"
                + "&request__node__params=1&getAll=1";
        String body = httpGet(url);
        if (body == null) return new RankingBundle(Collections.emptyMap(), Collections.emptyList());
        try {
            return parse(JsonParser.parseString(body).getAsJsonObject());
        } catch (Exception e) {
            LOG.warn("Baidu rankings parse error", e);
            return new RankingBundle(Collections.emptyMap(), Collections.emptyList());
        }
    }

    private RankingBundle parse(JsonObject root) {
        Map<String, List<BaiduRankedTeam>> groups = new LinkedHashMap<>();
        List<String> knockoutStages = new ArrayList<>();
        if (root == null) return new RankingBundle(groups, knockoutStages);
        JsonObject tplData = root.has("tplData") && !root.get("tplData").isJsonNull()
                ? root.getAsJsonObject("tplData") : null;
        if (tplData == null) return new RankingBundle(groups, knockoutStages);
        JsonObject data = tplData.has("data") && !tplData.get("data").isJsonNull()
                ? tplData.getAsJsonObject("data") : null;
        if (data == null) return new RankingBundle(groups, knockoutStages);

        // 结构: tplData.data.tabsList[0].tabList[0].data[]  (12 小组)
        //       tplData.data.tabsList[0].tabList[1].data[]  (淘汰赛)
        // 实际还有 tplData.data.tabsList[] 自身可能多包一层
        JsonArray topTabs = data.has("tabsList") && !data.get("tabsList").isJsonNull()
                ? data.getAsJsonArray("tabsList") : new JsonArray();
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
                    for (JsonElement grp : data2) {
                        if (!grp.isJsonObject()) continue;
                        JsonObject g = grp.getAsJsonObject();
                        String title = g.has("title") ? g.get("title").getAsString() : "";
                        String letter = extractGroupLetter(title);
                        if (letter == null) continue;
                        JsonArray list = g.has("list") && !g.get("list").isJsonNull()
                                ? g.getAsJsonArray("list") : new JsonArray();
                        List<BaiduRankedTeam> teams = new ArrayList<>();
                        for (JsonElement te : list) {
                            BaiduRankedTeam t = parseTeam(te);
                            if (t != null) teams.add(t);
                        }
                        groups.put(letter, teams);
                    }
                } else if ("knockoutStage".equals(type)) {
                    if (tab.has("tabs") && tab.get("tabs").isJsonArray()) {
                        for (JsonElement s : tab.getAsJsonArray("tabs")) {
                            if (!s.isJsonNull()) knockoutStages.add(s.getAsString());
                        }
                    }
                }
            }
        }
        return new RankingBundle(groups, knockoutStages);
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
        t.qualified = "晋级32强".equals(status);
        t.uncertain = "晋级待定".equals(status);

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

    private static String extractGroupLetter(String title) {
        if (title == null) return null;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (c >= 'A' && c <= 'L') return String.valueOf(c);
        }
        return null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String httpGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148")
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
}
