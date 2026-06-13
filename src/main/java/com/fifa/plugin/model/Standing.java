package com.fifa.plugin.model;

import com.google.gson.annotations.SerializedName;

/**
 * 积分榜条目 (TheSportsDB lookuptable.php 响应)
 */
public class Standing {

    @SerializedName("idStanding")
    private String idStanding;

    @SerializedName("intRank")
    private String rank;

    @SerializedName("idTeam")
    private String teamId;

    @SerializedName("strTeam")
    private String teamName;

    @SerializedName("strTeamBadge")
    private String teamBadge;

    @SerializedName("strForm")
    private String form;

    @SerializedName("intPlayed")
    private String played;

    @SerializedName("intWin")
    private String win;

    @SerializedName("intDraw")
    private String draw;

    @SerializedName("intLoss")
    private String loss;

    @SerializedName("intGoalsFor")
    private String goalsFor;

    @SerializedName("intGoalsAgainst")
    private String goalsAgainst;

    @SerializedName("intGoalDifference")
    private String goalDifference;

    @SerializedName("intPoints")
    private String points;

    @SerializedName("intGroupPosition")   // TheSportsDB 偶有按组返回
    private String groupPosition;

    @SerializedName("idLeague")
    private String leagueId;

    // ---- UI 期望的视图字段 ----

    private transient int position;
    private transient TeamRef team;
    private transient int playedGames;
    private transient int won;
    private transient int drawGames;
    private transient int lost;
    private transient int pointsValue;
    private transient int goalsForValue;
    private transient int goalsAgainstValue;
    private transient int goalDifferenceValue;

    public void normalize() {
        this.position = parseInt(rank, 0);
        this.playedGames = parseInt(played, 0);
        this.won = parseInt(win, 0);
        this.drawGames = parseInt(draw, 0);
        this.lost = parseInt(loss, 0);
        this.pointsValue = parseInt(points, 0);
        this.goalsForValue = parseInt(goalsFor, 0);
        this.goalsAgainstValue = parseInt(goalsAgainst, 0);
        this.goalDifferenceValue = parseInt(goalDifference, 0);

        this.team = new TeamRef();
        this.team.id = parseLong(teamId);
        this.team.name = teamName;
        this.team.shortName = teamName;
        this.team.crest = teamBadge;
        this.team.tla = deriveTla(teamName);
    }

    private static int parseInt(String s, int def) {
        try { return s != null && !s.isEmpty() ? Integer.parseInt(s) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private static long parseLong(String s) {
        try { return s != null && !s.isEmpty() ? Long.parseLong(s) : 0L; }
        catch (NumberFormatException e) { return 0L; }
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

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public TeamRef getTeam() { return team; }
    public void setTeam(TeamRef team) { this.team = team; }

    public int getPlayedGames() { return playedGames; }
    public void setPlayedGames(int playedGames) { this.playedGames = playedGames; }

    public int getWon() { return won; }
    public void setWon(int won) { this.won = won; }

    public int getDraw() { return drawGames; }
    public void setDraw(int draw) { this.drawGames = draw; }

    public int getLost() { return lost; }
    public void setLost(int lost) { this.lost = lost; }

    public int getPoints() { return pointsValue; }
    public void setPoints(int points) { this.pointsValue = points; }

    public int getGoalsFor() { return goalsForValue; }
    public void setGoalsFor(int goalsFor) { this.goalsForValue = goalsFor; }

    public int getGoalsAgainst() { return goalsAgainstValue; }
    public void setGoalsAgainst(int goalsAgainst) { this.goalsAgainstValue = goalsAgainst; }

    public int getGoalDifference() { return goalDifferenceValue; }
    public void setGoalDifference(int goalDifference) { this.goalDifferenceValue = goalDifference; }

    public static class TeamRef {
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
}
