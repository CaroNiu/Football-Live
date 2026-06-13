package com.fifa.plugin.model;

import com.google.gson.annotations.SerializedName;

/**
 * 比赛数据模型 (TheSportsDB API v1 公共数据源)
 * <p>
 * 公共 key = "3" 无需注册, https://www.thesportsdb.com/api/v1/json/3
 */
public class Match {

    @SerializedName("idEvent")
    private String id;

    @SerializedName("dateEvent")
    private String dateEvent;       // YYYY-MM-DD

    @SerializedName("strTime")
    private String strTime;         // HH:mm:ss (UTC)

    @SerializedName("strTimeLocal")
    private String strTimeLocal;    // 本地时间, 优先使用

    @SerializedName("strTimestamp")
    private String strTimestamp;    // ISO-8601, 最完整

    @SerializedName("strStatus")
    private String rawStatus;       // "Not Started" / "Match Finished" / "1H" / "2H" / "HT" ...

    @SerializedName("intRound")
    private String intRound;        // "1" / "2" / "3" / "Quarter Final" 等

    @SerializedName("strVenue")
    private String venue;

    @SerializedName("idHomeTeam")
    private String homeTeamId;

    @SerializedName("strHomeTeam")
    private String homeTeamName;

    @SerializedName("strHomeTeamBadge")
    private String homeTeamBadge;

    @SerializedName("intHomeScore")
    private String homeScore;

    @SerializedName("idAwayTeam")
    private String awayTeamId;

    @SerializedName("strAwayTeam")
    private String awayTeamName;

    @SerializedName("strAwayTeamBadge")
    private String awayTeamBadge;

    @SerializedName("intAwayScore")
    private String awayScore;

    @SerializedName("strLeague")
    private String league;

    @SerializedName("strSeason")
    private String season;

    @SerializedName("strEvent")
    private String eventName;       // "Team A vs Team B"

    /** 百度体育的 matchId (base64), 用来拉 /al/live/detail */
    private transient String baiduMatchId;

    public String getBaiduMatchId() { return baiduMatchId; }
    public void setBaiduMatchId(String id) { this.baiduMatchId = id; }

    // ---- 直读 getter (供 fallback 使用) ----

    public String getHomeTeamName() { return homeTeamName; }
    public String getAwayTeamName() { return awayTeamName; }
    public String getHomeTeamId() { return homeTeamId; }
    public String getAwayTeamId() { return awayTeamId; }
    public String getHomeTeamBadge() { return homeTeamBadge; }
    public String getAwayTeamBadge() { return awayTeamBadge; }

    @SerializedName("strGroup")
    private String strGroup;        // "A" / "B" / ... (小组赛) / null (淘汰赛)

    // ---- 内部视图模型 (UI 期望的字段) ----

    private transient long idLong;
    private transient String utcDate;
    private transient String status;     // SCHEDULED | IN_PLAY | PAUSED | FINISHED
    private transient Integer minute;
    private transient String stage;      // GROUP_STAGE | LAST_16 | QUARTER_FINALS | ...
    private transient String group;
    private transient TeamInfo homeTeam;
    private transient TeamInfo awayTeam;
    private transient MatchScore score;
    private transient String lastUpdated;

    /**
     * 在反序列化后由 FootballDataClient 调用, 把 TheSportsDB 字段映射到 UI 模型
     */
    public void normalize() {
        try {
            this.idLong = id != null ? Long.parseLong(id) : 0L;
        } catch (NumberFormatException e) {
            this.idLong = 0L;
        }

        // 构造 utcDate: 优先 strTimeLocal, 其次 strTimestamp, 再 dateEvent + strTime
        if (strTimeLocal != null && !strTimeLocal.isEmpty()) {
            this.utcDate = strTimeLocal;
        } else if (strTimestamp != null && !strTimestamp.isEmpty()) {
            this.utcDate = strTimestamp;
        } else if (dateEvent != null) {
            this.utcDate = dateEvent + (strTime != null ? "T" + strTime : "T00:00:00");
        }

        // 状态映射
        this.status = mapStatus(rawStatus);

        // 阶段映射
        this.stage = mapStage(intRound);

        // 小组: TheSportsDB 的 strGroup 字段直接给出 (例如 "A" / "B")
        this.group = (strGroup != null && !strGroup.isEmpty()) ? strGroup : null;

        // 队伍
        this.homeTeam = new TeamInfo();
        this.homeTeam.id = parseLong(homeTeamId);
        this.homeTeam.name = homeTeamName;
        this.homeTeam.shortName = homeTeamName;
        this.homeTeam.tla = deriveTla(homeTeamName);
        this.homeTeam.crest = homeTeamBadge;

        this.awayTeam = new TeamInfo();
        this.awayTeam.id = parseLong(awayTeamId);
        this.awayTeam.name = awayTeamName;
        this.awayTeam.shortName = awayTeamName;
        this.awayTeam.tla = deriveTla(awayTeamName);
        this.awayTeam.crest = awayTeamBadge;

        // 比分
        this.score = new MatchScore();
        ScoreDetail ft = new ScoreDetail();
        ft.home = parseIntOrNull(homeScore);
        ft.away = parseIntOrNull(awayScore);
        this.score.fullTime = ft;
    }

    private static String mapStatus(String raw) {
        if (raw == null) return "SCHEDULED";
        String r = raw.trim().toUpperCase();
        return switch (r) {
            case "NOT STARTED", "TIMED", "SCHEDULED", "POSTPONED" -> "SCHEDULED";
            case "1H", "2H", "ET", "LIVE", "IN PLAY", "EXTRA TIME" -> "IN_PLAY";
            case "HT", "PAUSED", "HALF TIME", "BREAK" -> "PAUSED";
            case "FT", "MATCH FINISHED", "AET", "FINISHED", "AFTER PENALTIES" -> "FINISHED";
            default -> "SCHEDULED";
        };
    }

    private static String mapStage(String round) {
        if (round == null) return "GROUP_STAGE";
        String r = round.trim();
        // 数字 1-3 → 小组赛
        try {
            int n = Integer.parseInt(r);
            if (n >= 1 && n <= 3) return "GROUP_STAGE";
        } catch (NumberFormatException ignored) {
        }
        String upper = r.toUpperCase();
        if (upper.contains("QUARTER")) return "QUARTER_FINALS";
        if (upper.contains("SEMI")) return "SEMI_FINALS";
        if (upper.contains("FINAL") && !upper.contains("QUARTER") && !upper.contains("SEMI")) return "FINAL";
        if (upper.contains("LAST 16") || upper.contains("ROUND OF 16") || upper.contains("16")) return "LAST_16";
        if (upper.contains("LAST 32") || upper.contains("ROUND OF 32") || upper.contains("32")) return "LAST_32";
        return "GROUP_STAGE";
    }

    private static String deriveTla(String name) {
        if (name == null || name.isEmpty()) return "???";
        String[] parts = name.split("\\s+");
        if (parts.length == 1) {
            return parts[0].length() >= 3
                    ? parts[0].substring(0, 3).toUpperCase()
                    : parts[0].toUpperCase();
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
        }
        String tla = sb.toString();
        return tla.length() > 3 ? tla.substring(0, 3) : tla;
    }

    private static long parseLong(String s) {
        try { return s != null ? Long.parseLong(s) : 0L; } catch (NumberFormatException e) { return 0L; }
    }

    private static Integer parseIntOrNull(String s) {
        try { return s != null && !s.isEmpty() ? Integer.parseInt(s) : null; }
        catch (NumberFormatException e) { return null; }
    }

    // ---- UI 期望的 Getter / Setter ----

    public long getId() { return idLong; }
    public void setId(long id) { this.idLong = id; }

    public String getUtcDate() { return utcDate; }
    public void setUtcDate(String utcDate) { this.utcDate = utcDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getMinute() { return minute; }
    public void setMinute(Integer minute) { this.minute = minute; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public TeamInfo getHomeTeam() { return homeTeam; }
    public void setHomeTeam(TeamInfo homeTeam) { this.homeTeam = homeTeam; }

    public TeamInfo getAwayTeam() { return awayTeam; }
    public void setAwayTeam(TeamInfo awayTeam) { this.awayTeam = awayTeam; }

    public MatchScore getScore() { return score; }
    public void setScore(MatchScore score) { this.score = score; }

    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }

    public boolean isLive() {
        return "IN_PLAY".equals(status) || "PAUSED".equals(status);
    }

    public boolean isFinished() {
        return "FINISHED".equals(status);
    }

    public boolean isScheduled() {
        return "SCHEDULED".equals(status) || "TIMED".equals(status);
    }

    @Override
    public String toString() {
        return (homeTeam != null ? homeTeam.getShortName() : "?")
                + " vs " + (awayTeam != null ? awayTeam.getShortName() : "?");
    }

    // ---- 内部类 ----

    public static class TeamInfo {
        private long id;
        private String name;
        private String shortName;
        private String tla;
        private String crest;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getShortName() { return shortName; }
        public void setShortName(String shortName) { this.shortName = shortName; }

        public String getTla() { return tla; }
        public void setTla(String tla) { this.tla = tla; }

        public String getCrest() { return crest; }
        public void setCrest(String crest) { this.crest = crest; }
    }

    public static class MatchScore {
        private String winner;
        private String duration;
        private ScoreDetail fullTime;
        private ScoreDetail halfTime;

        public String getWinner() { return winner; }
        public void setWinner(String winner) { this.winner = winner; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public ScoreDetail getFullTime() { return fullTime; }
        public void setFullTime(ScoreDetail fullTime) { this.fullTime = fullTime; }

        public ScoreDetail getHalfTime() { return halfTime; }
        public void setHalfTime(ScoreDetail halfTime) { this.halfTime = halfTime; }
    }

    public static class ScoreDetail {
        private Integer home;
        private Integer away;

        public Integer getHome() { return home; }
        public void setHome(Integer home) { this.home = home; }

        public Integer getAway() { return away; }
        public void setAway(Integer away) { this.away = away; }
    }
}
