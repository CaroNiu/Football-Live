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
 * 百度体育直播比赛详情客户端
 * <p>
 * 端点: <code>/al/live/detail?matchId={id}&amp;tab={tabName}&amp;&amp;async_source=h5&amp;tab_type=single&amp;from=baidu_shoubai_na&amp;request__node__params=1&amp;getAll=1</code>
 * <p>
 * 两种 tab:
 * <ul>
 *   <li><b>分析</b> → tplData.data.tabsList[0].data = {igence, homeRecord, result, predictions}</li>
 *   <li><b>赛况</b> → tplData.data.tabsList[0].data.graphic_incidents = {graphic[], events[], incidents[]}</li>
 * </ul>
 */
@Service
public final class BaiduLiveMatchClient {

    private static final Logger LOG = Logger.getInstance(BaiduLiveMatchClient.class);
    private static final String HOST = "https://tiyu.baidu.com";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public BaiduLiveMatchClient() {
    }

    /**
     * 拉取某场比赛的详情 (分析 tab)
     * @param matchId 来自赛程接口的 data-sf-href 或 matchId 字段 (URL 编码过的 base64)
     */
    public LiveMatch fetch(String matchId) throws IOException {
        String url = HOST + "/al/live/detail"
                + "?matchId=" + enc(matchId)
                + "&tab=" + enc("分析")
                + "&&async_source=h5&tab_type=single&from=baidu_shoubai_na"
                + "&request__node__params=1&getAll=1";
        String body = httpGet(url);
        if (body == null) return null;

        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject tplData = root.has("tplData") && !root.get("tplData").isJsonNull()
                    ? root.getAsJsonObject("tplData") : null;
            if (tplData == null) return null;
            JsonObject data = tplData.has("data") && !tplData.get("data").isJsonNull()
                    ? tplData.getAsJsonObject("data") : null;
            if (data == null) return null;

            LiveMatch m = new LiveMatch();
            m.matchId = matchId;

            // header 包含比赛基础信息
            JsonObject header = data.has("header") && !data.get("header").isJsonNull()
                    ? data.getAsJsonObject("header") : null;
            if (header != null) {
                m.matchName = s(header, "matchName");
                m.matchStage = s(header, "matchStage");
                m.matchStatus = toInt(s(header, "matchStatus"));
                m.matchStatusText = s(header, "matchStatusText");
                m.time = s(header, "time");
                m.date = s(header, "date");
                m.vs = s(header, "vs");
                m.status = toInt(s(header, "status"));
                m.leftGoal = toInt(s(header, "leftGoal"));
                m.rightGoal = toInt(s(header, "rightGoal"));
                m.winner = s(header, "winner");
                m.startColor = s(header, "startColor");
                m.endColor = s(header, "endColor");
                m.bgColor = s(header, "bgColor");
                JsonObject ll = header.has("leftLogo") && header.get("leftLogo").isJsonObject()
                        ? header.getAsJsonObject("leftLogo") : null;
                if (ll != null) {
                    m.leftName = s(ll, "name");
                    m.leftLogoUrl = s(ll, "logo");
                }
                JsonObject rl = header.has("rightLogo") && header.get("rightLogo").isJsonObject()
                        ? header.getAsJsonObject("rightLogo") : null;
                if (rl != null) {
                    m.rightName = s(rl, "name");
                    m.rightLogoUrl = s(rl, "logo");
                }
            }

            // tabsList[0].data = 分析
            JsonArray tabs = data.has("tabsList") && !data.get("tabsList").isJsonNull()
                    ? data.getAsJsonArray("tabsList") : new JsonArray();
            if (tabs.size() > 0) {
                JsonObject tab = tabs.get(0).getAsJsonObject();
                JsonObject inner = tab.has("data") && !tab.get("data").isJsonNull()
                        ? tab.getAsJsonObject("data") : new JsonObject();

                // igence (智能预测) - 2 个 (有利情报 / 不利情报)
                if (inner.has("igence") && inner.get("igence").isJsonArray()) {
                    for (JsonElement el : inner.getAsJsonArray("igence")) {
                        if (el.isJsonObject()) {
                            JsonObject o = el.getAsJsonObject();
                            String title = s(o, "intelligencetitle");
                            JsonObject intel = o.has("intelligence") && o.get("intelligence").isJsonObject()
                                    ? o.getAsJsonObject("intelligence") : null;
                            if (intel != null) {
                                Intelligence ig = new Intelligence();
                                ig.title = title;
                                ig.homeTeamName = teamNameFromInfo(intel, "intelligenceTeamInfo");
                                ig.awayTeamName = teamNameFromInfo(intel, "intelligenceteamLeaterInfo");
                                ig.homeContents = readContentList(intel, "intelligenceteam");
                                ig.awayContents = readContentList(intel, "intelligenceteamleater");
                                // 兼容老字段
                                ig.team = ig.homeTeamName;
                                ig.leater = ig.awayTeamName;
                                m.intelligences.add(ig);
                            }
                        }
                    }
                }

                // homeRecord (历史交锋 / 主队战绩 / 客队战绩) - 3 个
                if (inner.has("homeRecord") && inner.get("homeRecord").isJsonArray()) {
                    for (JsonElement el : inner.getAsJsonArray("homeRecord")) {
                        if (el.isJsonObject()) {
                            JsonObject o = el.getAsJsonObject();
                            // homeRecord 元素有 "history" + "home" 两个并列子项, 内容重复; 取任一即可
                            JsonObject src = o.has("history") && o.get("history").isJsonObject()
                                    ? o.getAsJsonObject("history") : null;
                            if (src == null) {
                                src = o.has("home") && o.get("home").isJsonObject()
                                        ? o.getAsJsonObject("home") : null;
                            }
                            if (src != null) {
                                Record rec = new Record();
                                rec.title = s(src, "title");
                                rec.img = s(src, "img");
                                rec.teamName = s(src, "team_name");
                                rec.result = s(src, "result");
                                rec.probability = summarizeProbability(src);
                                rec.type = "history";
                                rec.matches = readMatchList(src, "list");
                                m.records.add(rec);
                            }
                        }
                    }
                }

                // result (结果预测: 胜/平/负 %)
                if (inner.has("result") && inner.get("result").isJsonObject()) {
                    JsonObject r = inner.getAsJsonObject("result");
                    JsonObject pct = r.has("percentage") && r.get("percentage").isJsonObject()
                            ? r.getAsJsonObject("percentage") : null;
                    if (pct != null) {
                        m.victoryPct = s(pct, "victory");
                        m.drawPct = s(pct, "draw");
                        m.lostPct = s(pct, "lost");
                    }
                    m.resultFont = s(r, "resultfont");
                    if (r.has("team") && r.get("team").isJsonArray()) {
                        for (JsonElement el : r.getAsJsonArray("team")) {
                            if (el.isJsonObject()) {
                                JsonObject t = el.getAsJsonObject();
                                m.predictions.add(new Prediction(
                                        s(t, "team"),
                                        s(t, "winrate"),
                                        s(t, "winner"),
                                        s(t, "icon")));
                            }
                        }
                    }
                }
            }
            return m;
        } catch (Exception e) {
            LOG.warn("Baidu live match parse error for " + matchId, e);
            return null;
        }
    }

    // ============================================================
    //  文字直播 (赛况 Tab)
    // ============================================================

    /**
     * 拉取单场比赛的文字直播 (赛况 tab)
     * <p>
     * 端点: <code>/al/live/detail?matchId=...&amp;tab=赛况</code>
     * <p>
     * 响应路径: <code>tplData.data.tabsList[0].data.graphic_incidents.graphic[]</code>
     * <p>
     * 每条事件字段:
     * <ul>
     *   <li>word - 完整中文描述 (如 "31' - 第2个进球 - 普利西奇(美国) - 射门 (助攻: 麦肯尼)")</li>
     *   <li>time - 比赛分钟 (如 "31'")</li>
     *   <li>teamName - 球队名</li>
     *   <li>icon_type - 事件类型 (进球/射门/射偏/角球/越位/黄牌/红牌/换人/...)</li>
     *   <li>position - "1"=主队, "2"=客队, "0"=中性 (如开赛/中场)</li>
     * </ul>
     *
     * @return 文字直播事件列表, 按服务端顺序 (最新在前). 返回空列表表示无数据.
     */
    public List<TextLiveEvent> fetchMatchTextLive(String matchId) throws IOException {
        String url = HOST + "/al/live/detail"
                + "?matchId=" + enc(matchId)
                + "&tab=" + enc("赛况")
                + "&&async_source=h5&tab_type=single&from=baidu_shoubai_na"
                + "&request__node__params=1&getAll=1";
        String body = httpGet(url);
        if (body == null) return Collections.emptyList();

        List<TextLiveEvent> out = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject tplData = root.has("tplData") && !root.get("tplData").isJsonNull()
                    ? root.getAsJsonObject("tplData") : null;
            if (tplData == null) return out;
            JsonObject data = tplData.has("data") && !tplData.get("data").isJsonNull()
                    ? tplData.getAsJsonObject("data") : null;
            if (data == null) return out;
            JsonArray tabsList = data.has("tabsList") && !data.get("tabsList").isJsonNull()
                    ? data.getAsJsonArray("tabsList") : new JsonArray();
            if (tabsList.size() == 0) return out;

            JsonObject tab0 = tabsList.get(0).getAsJsonObject();
            JsonObject inner = tab0.has("data") && !tab0.get("data").isJsonNull()
                    ? tab0.getAsJsonObject("data") : null;
            if (inner == null) return out;
            JsonObject gi = inner.has("graphic_incidents") && !inner.get("graphic_incidents").isJsonNull()
                    ? inner.getAsJsonObject("graphic_incidents") : null;
            if (gi == null) return out;
            JsonArray graphic = gi.has("graphic") && !gi.get("graphic").isJsonNull()
                    ? gi.getAsJsonArray("graphic") : new JsonArray();

            for (JsonElement el : graphic) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                TextLiveEvent e = new TextLiveEvent();
                e.time = s(o, "time");
                e.iconType = s(o, "icon_type");
                e.teamName = s(o, "teamName");
                e.position = s(o, "position");
                e.content = s(o, "word");
                // 跳过完全空的事件
                if (e.content.isEmpty() && e.time.isEmpty()) continue;
                out.add(e);
            }
        } catch (Exception ex) {
            LOG.warn("Baidu text live parse error for " + matchId, ex);
        }
        return out;
    }

    /**
     * 一条文字直播事件
     */
    public static class TextLiveEvent {
        /** 比赛分钟, 如 "31'" / "0'" (开赛) / "中场" */
        public String time;
        /** 事件类型, 如 "进球" / "射门" / "角球" / "黄牌" / "换人" / "越位" */
        public String iconType;
        /** 球队名, 中性事件为空字符串 */
        public String teamName;
        /** "1"=主队 / "2"=客队 / "0"=中性 */
        public String position;
        /** 完整中文描述, 含分钟和事件 */
        public String content;
    }

    private static String s(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return "";
        try { return o.get(k).getAsString(); } catch (Exception e) { return ""; }
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

    public static class LiveMatch {
        public String matchId;
        public String matchName, matchStage, matchStatusText, time, date, vs, winner;
        public int matchStatus, status, leftGoal, rightGoal;
        public String leftName, leftLogoUrl, rightName, rightLogoUrl;
        public String startColor, endColor, bgColor;
        public String victoryPct, drawPct, lostPct, resultFont;
        public List<Intelligence> intelligences = new ArrayList<>();
        public List<Record> records = new ArrayList<>();
        public List<Prediction> predictions = new ArrayList<>();
    }

    /**
     * 智能预测 (一条 = 有利情报 或 不利情报)
     * <p>
     * 同一标题下有两个角度: 主队情报 + 客队情报, 各自包含多条文字描述
     */
    public static class Intelligence {
        /** 标题, 如 "有利情报" / "不利情报" */
        public String title;
        /** 主队名 (百度字段 intelligenceTeamInfo) */
        public String homeTeamName;
        /** 客队名 (百度字段 intelligenceteamLeaterInfo) */
        public String awayTeamName;
        /** 主队情报列表 (intelligenceteam[].content) */
        public List<String> homeContents = new ArrayList<>();
        /** 客队情报列表 (intelligenceteamleater[].content) */
        public List<String> awayContents = new ArrayList<>();

        // -- 兼容旧字段 --
        public String team;     // = homeTeamName
        public String leater;   // = awayTeamName
    }

    /**
     * 历史交锋 / 主队战绩 / 客队战绩
     * <p>
     * 一个 Record 对应一段独立板块 (历史交锋 / 主队近况 / 客队近况)
     */
    public static class Record {
        /** 板块标题, 如 "历史交锋" / "主队近10场战绩" / "客队近10场战绩" */
        public String title;
        /** 球队名 (历史交锋板块为 null, 主队战绩 = 主队名, ...) */
        public String teamName;
        /** 战绩摘要, 如 "3胜3负0平" */
        public String result;
        /** 胜率 (从 probability[] 汇总), 如 "67% (4胜0平2负)" */
        public String probability;
        /** 球队 logo URL */
        public String img;
        /** "history" / "home" (兼容字段) */
        public String type;
        /** 近期比赛列表 (最多 6 条) */
        public List<HistoryMatch> matches = new ArrayList<>();
    }

    /**
     * 一条历史交锋 / 近期比赛
     */
    public static class HistoryMatch {
        /** 比赛日期, 如 "2025-11-16" */
        public String date;
        /** 主队名 */
        public String leftName;
        /** 主队得分 (字符串, 兼容 "2" / "1") */
        public String leftScore;
        /** 客队名 */
        public String rightName;
        /** 客队得分 */
        public String rightScore;
        /** 比分, 如 "2-1" */
        public String vs;
        /** 主队是否获胜 (用于标色) */
        public Boolean leftWin;
    }

    public static class Prediction {
        public final String team, winrate, winner, icon;
        public Prediction(String t, String w, String wn, String i) { team = t; winrate = w; winner = wn; icon = i; }
    }

    // ============================================================
    //  分析数据解析辅助
    // ============================================================

    /**
     * 从 intelligenceTeamInfo / intelligenceteamLeaterInfo 中提取球队名
     * <p>
     * 响应字段: <code>{link, logo, mid, name, ...}</code>
     */
    private static String teamNameFromInfo(JsonObject intel, String key) {
        if (intel == null || !intel.has(key) || intel.get(key).isJsonNull()) return "";
        JsonObject info = intel.getAsJsonObject(key);
        return s(info, "name");
    }

    /**
     * 读取 <code>{content: "..."}</code> 列表
     */
    private static List<String> readContentList(JsonObject parent, String key) {
        List<String> out = new ArrayList<>();
        if (parent == null || !parent.has(key) || parent.get(key).isJsonNull()) return out;
        JsonArray arr = parent.getAsJsonArray(key);
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                String c = s(el.getAsJsonObject(), "content");
                if (!c.isEmpty()) out.add(c);
            } else if (el.isJsonPrimitive()) {
                out.add(el.getAsString());
            }
        }
        return out;
    }

    /**
     * 把 probability[] (3 条 {title, win, draw, loss}) 汇总成 "胜率 67% (4胜0平2负)"
     */
    private static String summarizeProbability(JsonObject src) {
        if (src == null || !src.has("probability") || src.get("probability").isJsonNull()) return "";
        JsonArray prob = src.getAsJsonArray("probability");
        if (prob.size() == 0) return "";
        JsonObject first = prob.get(0).getAsJsonObject();
        String title = s(first, "title");
        JsonObject w = first.has("win") && first.get("win").isJsonObject()
                ? first.getAsJsonObject("win") : null;
        JsonObject d = first.has("draw") && first.get("draw").isJsonObject()
                ? first.getAsJsonObject("draw") : null;
        JsonObject l = first.has("loss") && first.get("loss").isJsonObject()
                ? first.getAsJsonObject("loss") : null;
        if (w == null && d == null && l == null) return title;
        StringBuilder sb = new StringBuilder();
        if (!title.isEmpty()) sb.append(title).append(" (");
        else sb.append("(");
        if (w != null) sb.append(val(w, "value")).append("胜");
        if (d != null) sb.append(val(d, "value")).append("平");
        if (l != null) sb.append(val(l, "value")).append("负");
        sb.append(")");
        return sb.toString();
    }

    private static String val(JsonObject o, String k) {
        if (o == null) return "0";
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return "0";
        return e.getAsString();
    }

    /**
     * 读取 list[] (近期 6 场), 每条含 date / left{...} / right{...} / vs
     */
    private static List<HistoryMatch> readMatchList(JsonObject src, String key) {
        List<HistoryMatch> out = new ArrayList<>();
        if (src == null || !src.has(key) || src.get(key).isJsonNull()) return out;
        JsonArray arr = src.getAsJsonArray(key);
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            HistoryMatch m = new HistoryMatch();
            m.date = s(o, "date");
            m.vs = s(o, "vs");
            if (o.has("left") && o.get("left").isJsonObject()) {
                JsonObject l = o.getAsJsonObject("left");
                m.leftName = s(l, "name");
                m.leftScore = s(l, "score");
                JsonElement win = l.get("isWin");
                if (win != null && !win.isJsonNull()) m.leftWin = win.getAsBoolean();
            }
            if (o.has("right") && o.get("right").isJsonObject()) {
                JsonObject r = o.getAsJsonObject("right");
                m.rightName = s(r, "name");
                m.rightScore = s(r, "score");
            }
            out.add(m);
        }
        return out;
    }
}
