package com.fifa.plugin.model;

import com.google.gson.annotations.SerializedName;

/**
 * 从赛果实时计算的积分榜条目
 * <p>
 * TheSportsDB 的 lookuptable 接口对 World Cup 2026 返回的是 "Playoffs" 全 0 占位数据,
 * 不可用, 所以这里从 rounds 1-3 的比赛结果动态聚合
 */
public class ComputedStanding {

    @SerializedName("idTeam")
    public String teamId;

    @SerializedName("strTeam")
    public String teamName;

    @SerializedName("strTeamBadge")
    public String teamBadge;

    public int played;
    public int won;
    public int draw;
    public int lost;
    public int goalsFor;
    public int goalsAgainst;
    public int goalDifference;
    public int points;

    public ComputedStanding() {
    }

    public ComputedStanding(String teamName, String teamId, String teamBadge) {
        this.teamName = teamName;
        this.teamId = teamId;
        this.teamBadge = teamBadge;
    }

    /**
     * 应用一场已结束的比赛
     */
    public void applyResult(int gf, int ga) {
        this.played++;
        this.goalsFor += gf;
        this.goalsAgainst += ga;
        this.goalDifference = this.goalsFor - this.goalsAgainst;
        if (gf > ga) {
            this.won++;
            this.points += 3;
        } else if (gf == ga) {
            this.draw++;
            this.points += 1;
        } else {
            this.lost++;
        }
    }
}
