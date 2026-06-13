package com.fifa.plugin.model;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分榜响应 (合并原始 + 计算)
 * <p>
 * - {@link #standings} 来自 lookuptable 接口 (TheSportsDB 对 World Cup 2026 返回全 0 占位, 仅作为兜底)
 * - {@link #groupStandings} 由 FootballDataClient 从 rounds 1-3 赛果动态计算 (优先使用)
 */
public class StandingResponse {

    @SerializedName("table")
    private List<Standing> table;

    /** group 字母 ("A", "B"...) → 该组 4 支队伍的实时积分 (按积分/净胜球/进球数排序) */
    private Map<String, List<ComputedStanding>> groupStandings = new LinkedHashMap<>();

    public List<Standing> getStandings() { return table; }
    public void setStandings(List<Standing> table) { this.table = table; }

    public Map<String, List<ComputedStanding>> getGroupStandings() { return groupStandings; }
    public void setGroupStandings(Map<String, List<ComputedStanding>> groupStandings) {
        this.groupStandings = groupStandings;
    }
}
