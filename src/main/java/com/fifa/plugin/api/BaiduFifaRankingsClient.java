package com.fifa.plugin.api;

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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 百度体育 FIFA 官方排名专用客户端
 * <p>
 * 端点: <code>/al/match?match=世界杯&amp;tab=FIFA排名&amp;&amp;async_source=h5&amp;tab_type=single&amp;from=baidu_shoubai_na&amp;request__node__params=1&amp;getAll=1</code>
 * <p>
 * 响应: tplData.data.tabsList[0].data.ranking[]  (211 条全部 FIFA 排名, 含中文队名/队徽/积分/排名)
 */
@Service
public final class BaiduFifaRankingsClient {

    private static final Logger LOG = Logger.getInstance(BaiduFifaRankingsClient.class);
    private static final String HOST = "https://tiyu.baidu.com";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public BaiduFifaRankingsClient() {
    }

    public List<FifaRankEntry> fetch(int limit) throws IOException {
        String url = HOST + "/al/match"
                + "?match=" + enc("世界杯")
                + "&tab=" + enc("FIFA排名")
                + "&&async_source=h5&tab_type=single&from=baidu_shoubai_na"
                + "&request__node__params=1&getAll=1";
        String body = httpGet(url);
        if (body == null) return Collections.emptyList();
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject tplData = root.has("tplData") && !root.get("tplData").isJsonNull()
                    ? root.getAsJsonObject("tplData") : null;
            if (tplData == null) return Collections.emptyList();
            JsonObject data = tplData.has("data") && !tplData.get("data").isJsonNull()
                    ? tplData.getAsJsonObject("data") : null;
            if (data == null) return Collections.emptyList();
            JsonArray tabsList = data.has("tabsList") && !data.get("tabsList").isJsonNull()
                    ? data.getAsJsonArray("tabsList") : new JsonArray();
            if (tabsList.size() == 0) return Collections.emptyList();

            JsonObject top0 = tabsList.get(0).getAsJsonObject();
            JsonObject inner = top0.has("data") && !top0.get("data").isJsonNull()
                    ? top0.getAsJsonObject("data") : null;
            if (inner == null) return Collections.emptyList();
            JsonArray ranking = inner.has("ranking") && !inner.get("ranking").isJsonNull()
                    ? inner.getAsJsonArray("ranking") : new JsonArray();

            List<FifaRankEntry> out = new ArrayList<>();
            for (JsonElement el : ranking) {
                if (!el.isJsonObject()) continue;
                JsonObject e = el.getAsJsonObject();
                FifaRankEntry r = new FifaRankEntry();
                r.rank = toInt(e.has("ranking") && !e.get("ranking").isJsonNull() ? e.get("ranking").getAsString() : null);
                r.points = e.has("points") && !e.get("points").isJsonNull() ? e.get("points").getAsString() : "";
                r.change = e.has("position_changed") && !e.get("position_changed").isJsonNull()
                        ? e.get("position_changed").getAsString() : "0";
                r.highlight = e.has("highlight") && !e.get("highlight").isJsonNull()
                        ? e.get("highlight").getAsString() : "0";

                JsonObject team = e.has("team") && e.get("team").isJsonObject()
                        ? e.getAsJsonObject("team") : null;
                if (team != null) {
                    r.teamId = team.has("id") && !team.get("id").isJsonNull() ? team.get("id").getAsString() : "";
                    r.name = team.has("name_zh") && !team.get("name_zh").isJsonNull()
                            ? team.get("name_zh").getAsString() : "";
                    r.logo = team.has("logo") && !team.get("logo").isJsonNull()
                            ? team.get("logo").getAsString() : "";
                    r.stroke = toInt(team.has("stroke") && !team.get("stroke").isJsonNull()
                            ? team.get("stroke").toString() : null);
                }
                out.add(r);
                if (out.size() >= limit) break;
            }
            LOG.info("Baidu FIFA rankings loaded: " + out.size() + " teams");
            return out;
        } catch (Exception e) {
            LOG.warn("Baidu FIFA rankings parse error", e);
            return Collections.emptyList();
        }
    }

    private static int toInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
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

    public static class FifaRankEntry {
        public int rank;
        public String points;
        public String change;       // 排名变化 (e.g. "+2" / "-1" / "0")
        public String highlight;     // 1 = 高亮
        public String teamId;
        public String name;          // 中文队名 (name_zh)
        public String logo;          // 队徽 URL
        public int stroke;           // 加粗标记
    }
}
