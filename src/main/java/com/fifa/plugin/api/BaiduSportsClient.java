package com.fifa.plugin.api;

import com.fifa.plugin.model.Match;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 百度体育 tiyu.baidu.com 公共 JSON API 客户端
 * <p>
 * 端点: <code>/al/api/match/schedules?from=self&amp;match={name}&amp;date=YYYY-MM-DD&amp;direction=after&amp;isAsync=1</code>
 * <p>
 * 响应结构:
 * <pre>
 * {
 *   "data": [                       // 按日期分组的比赛列表
 *     {
 *       "time": "2026-06-12",       // 日期
 *       "weekday": "今天",
 *       "list": [
 *         {
 *           "time": "03:00",        // 当地开球时间 HH:mm
 *           "startTime": "2026-06-12 03:00:00",
 *           "matchName": "世界杯小组赛A组第1轮",
 *           "vsLine": "2-0",         // 比分
 *           "matchStatus": "2",      // 0=未开赛 1=进行中 2=已结束
 *           "matchStatusText": "已结束",
 *           "leftLogo":  { "name": "墨西哥", "score": "2", "logo": "url" },
 *           "rightLogo": { "name": "南非",   "score": "0", "logo": "url" },
 *           "scoreInfo": { "leftRegularScore": "2", "rightRegularScore": "0" }
 *         }
 *       ]
 *     }
 *   ],
 *   "select": {
 *     "labels": [                    // 全部有比赛的日期 (用于日期选择器)
 *       { "date": "2026-06-12", "suffix": "2场", "desc": "小组赛" },
 *       ...
 *     ]
 *   }
 * }
 * </pre>
 */
@Service
public final class BaiduSportsClient {

    private static final Logger LOG = Logger.getInstance(BaiduSportsClient.class);
    private static final String HOST = "https://tiyu.baidu.com";
    private static final String MATCH_NAME = "世界杯";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new GsonBuilder().create();

    public BaiduSportsClient() {
    }

    /**
     * 拉取赛程 (从今天起), 返回按日期分组的全部比赛
     */
    public ScheduleBundle fetchSchedule() throws IOException {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String url = HOST + "/al/api/match/schedules"
                + "?from=self"
                + "&match=" + enc(MATCH_NAME)
                + "&date=" + date
                + "&direction=after"
                + "&isAsync=1";

        String body = httpGet(url);
        if (body == null) return new ScheduleBundle(Collections.emptyList(), Collections.emptyList());

        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            return parseScheduleJson(root);
        } catch (Exception e) {
            LOG.warn("Baidu schedule parse error", e);
            return new ScheduleBundle(Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * 拉取某天赛程 (向后查询, 服务端一次返多日, 客户端按 date 过滤)
     */
    public List<Match> fetchScheduleByDate(LocalDate date) throws IOException {
        ScheduleBundle bundle = fetchSchedule();
        List<Match> out = new ArrayList<>();
        for (Match m : bundle.matches) {
            if (m.getUtcDate() != null && m.getUtcDate().startsWith(date.toString())) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * 解析 JSON, 同时抽取 matches (扁平) 和 dates (有比赛的日期列表)
     */
    private ScheduleBundle parseScheduleJson(JsonObject root) {
        List<Match> allMatches = new ArrayList<>();
        List<DateLabel> dateLabels = new ArrayList<>();

        // ---- data[].list[] → 比赛 ----
        if (root.has("data") && !root.get("data").isJsonNull()) {
            JsonArray data = root.getAsJsonArray("data");
            for (JsonElement dayEl : data) {
                if (!dayEl.isJsonObject()) continue;
                JsonObject day = dayEl.getAsJsonObject();
                String dayDate = day.has("time") && !day.get("time").isJsonNull()
                        ? day.get("time").getAsString() : null;
                if (dayDate == null) continue;

                if (day.has("list") && !day.get("list").isJsonNull()) {
                    JsonArray list = day.getAsJsonArray("list");
                    for (JsonElement mEl : list) {
                        Match m = parseBaiduMatch(mEl, dayDate);
                        if (m != null) allMatches.add(m);
                    }
                }
            }
        }

        // ---- select.labels[] → 日期选择器数据 ----
        if (root.has("select") && !root.get("select").isJsonNull()) {
            JsonObject sel = root.getAsJsonObject("select");
            if (sel.has("labels") && !sel.get("labels").isJsonNull()) {
                JsonArray labels = sel.getAsJsonArray("labels");
                for (JsonElement lbl : labels) {
                    if (!lbl.isJsonObject()) continue;
                    JsonObject o = lbl.getAsJsonObject();
                    String date = o.has("date") && !o.get("date").isJsonNull() ? o.get("date").getAsString() : null;
                    if (date == null) continue;
                    DateLabel dl = new DateLabel();
                    dl.date = LocalDate.parse(date);
                    dl.suffix = o.has("suffix") && !o.get("suffix").isJsonNull() ? o.get("suffix").getAsString() : "";
                    dl.desc = o.has("desc") && !o.get("desc").isJsonNull() ? o.get("desc").getAsString() : "";
                    dl.disabled = o.has("disabled") && "1".equals(o.get("disabled").getAsString());
                    dateLabels.add(dl);
                }
            }
        }

        return new ScheduleBundle(allMatches, dateLabels);
    }

    /**
     * 解析单场比赛 JSON
     */
    private Match parseBaiduMatch(JsonElement mEl, String dayDate) {
        if (!mEl.isJsonObject()) return null;
        JsonObject m = mEl.getAsJsonObject();

        // 只保留世界杯
        String game = m.has("game") && !m.get("game").isJsonNull() ? m.get("game").getAsString() : "";
        if (game == null || !game.contains("世界杯")) return null;

        Match result = new Match();

        // 百度 matchId (base64 编码), 用于拉 /al/live/detail
        String key = m.has("key") && !m.get("key").isJsonNull() ? m.get("key").getAsString() : "";
        if (key != null && !key.isEmpty()) {
            result.setBaiduMatchId(key);
        }
        result.setId(System.nanoTime() / 1000);

        // 完整开球时间 (带时区)
        String startTime = m.has("startTime") && !m.get("startTime").isJsonNull()
                ? m.get("startTime").getAsString() : null;
        if (startTime != null && !startTime.isEmpty()) {
            result.setUtcDate(startTime.replace(" ", "T") + "+08:00");
        } else {
            result.setUtcDate(dayDate + "T00:00:00+08:00");
        }

        // 时间 (HH:mm)
        String time = m.has("time") && !m.get("time").isJsonNull() ? m.get("time").getAsString() : null;
        if (time != null) result.setVenue(time);

        // 阶段
        String stage = m.has("matchStage") && !m.get("matchStage").isJsonNull()
                ? m.get("matchStage").getAsString() : null;
        if (stage != null) {
            result.setStage(mapStage(stage));
            result.setGroup(mapGroup(stage));
        }

        // 状态
        String status = m.has("matchStatus") && !m.get("matchStatus").isJsonNull()
                ? m.get("matchStatus").getAsString() : "0";
        result.setStatus(mapMatchStatus(status));

        // 主队
        if (m.has("leftLogo") && !m.get("leftLogo").isJsonNull()) {
            JsonObject left = m.getAsJsonObject("leftLogo");
            String name = left.has("name") && !left.get("name").isJsonNull() ? left.get("name").getAsString() : "";
            com.fifa.plugin.model.Match.TeamInfo t = new com.fifa.plugin.model.Match.TeamInfo();
            t.setName(name);
            t.setShortName(name);
            t.setTla(deriveTla(name));
            if (left.has("logo") && !left.get("logo").isJsonNull()) {
                t.setCrest(left.get("logo").getAsString());
            }
            result.setHomeTeam(t);
        }
        // 客队
        if (m.has("rightLogo") && !m.get("rightLogo").isJsonNull()) {
            JsonObject right = m.getAsJsonObject("rightLogo");
            String name = right.has("name") && !right.get("name").isJsonNull() ? right.get("name").getAsString() : "";
            com.fifa.plugin.model.Match.TeamInfo t = new com.fifa.plugin.model.Match.TeamInfo();
            t.setName(name);
            t.setShortName(name);
            t.setTla(deriveTla(name));
            if (right.has("logo") && !right.get("logo").isJsonNull()) {
                t.setCrest(right.get("logo").getAsString());
            }
            result.setAwayTeam(t);
        }

        // 比分 (优先用 scoreInfo, fallback 到 vsLine)
        Integer h = null, a = null;
        if (m.has("scoreInfo") && !m.get("scoreInfo").isJsonNull()) {
            JsonObject si = m.getAsJsonObject("scoreInfo");
            if (si.has("leftRegularScore") && !si.get("leftRegularScore").isJsonNull()) {
                try { h = Integer.parseInt(si.get("leftRegularScore").getAsString()); } catch (Exception ignored) {}
            }
            if (si.has("rightRegularScore") && !si.get("rightRegularScore").isJsonNull()) {
                try { a = Integer.parseInt(si.get("rightRegularScore").getAsString()); } catch (Exception ignored) {}
            }
        }
        if ((h == null || a == null) && m.has("vsLine") && !m.get("vsLine").isJsonNull()) {
            String vs = m.get("vsLine").getAsString();
            if (vs != null && vs.contains("-")) {
                try {
                    String[] parts = vs.split("-");
                    h = Integer.parseInt(parts[0].trim());
                    a = Integer.parseInt(parts[1].trim());
                } catch (Exception ignored) {}
            }
        }
        com.fifa.plugin.model.Match.MatchScore ms = new com.fifa.plugin.model.Match.MatchScore();
        com.fifa.plugin.model.Match.ScoreDetail ft = new com.fifa.plugin.model.Match.ScoreDetail();
        ft.setHome(h);
        ft.setAway(a);
        ms.setFullTime(ft);
        result.setScore(ms);

        return result;
    }

    private static String mapStage(String s) {
        if (s == null) return "GROUP_STAGE";
        if (s.contains("小组赛")) return "GROUP_STAGE";
        if (s.contains("淘汰赛")) return "LAST_16";
        if (s.contains("1/8") || s.contains("16强")) return "LAST_16";
        if (s.contains("1/4") || s.contains("8强") || s.contains("四分之一")) return "QUARTER_FINALS";
        if (s.contains("半决赛")) return "SEMI_FINALS";
        if (s.contains("决赛") && !s.contains("季军")) return "FINAL";
        if (s.contains("季军")) return "THIRD_PLACE";
        return "GROUP_STAGE";
    }

    private static String mapGroup(String s) {
        if (s == null) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'L') || (c >= 'a' && c <= 'l')) {
                if (c >= 'a' && c <= 'z') c = (char) (c - 'a' + 'A');
                return String.valueOf(c);
            }
        }
        return null;
    }

    private static String mapMatchStatus(String s) {
        if (s == null) return "SCHEDULED";
        return switch (s) {
            case "0" -> "SCHEDULED";
            case "1" -> "IN_PLAY";
            case "2" -> "FINISHED";
            default -> "SCHEDULED";
        };
    }

    private static String deriveTla(String name) {
        if (name == null || name.isEmpty()) return "???";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length() && sb.length() < 3; i++) {
            char c = name.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fa5) sb.append(c);
            else if (Character.isUpperCase(c)) sb.append(c);
        }
        return sb.length() > 0 ? sb.toString() : name;
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

    /**
     * 返回的赛程 + 日期标签 (用于日期选择器)
     */
    public static class ScheduleBundle {
        public final List<Match> matches;
        public final List<DateLabel> dateLabels;

        public ScheduleBundle(List<Match> matches, List<DateLabel> dateLabels) {
            this.matches = matches;
            this.dateLabels = dateLabels;
        }
    }

    public static class DateLabel {
        public LocalDate date;
        public String suffix;  // "2场"
        public String desc;    // "小组赛" / "决赛" / ""
        public boolean disabled;
    }
}
