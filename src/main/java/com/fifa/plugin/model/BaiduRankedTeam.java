package com.fifa.plugin.model;

/**
 * 百度体育积分榜条目 - 小组赛阶段
 * <p>
 * 字段来源: <code>list[].record[]</code> 解包 + 顶层的 fillsName / bgColor
 */
public class BaiduRankedTeam {

    public String teamId;
    public String name;       // 中文队名 (record[0].name)
    public String logo;       // 队徽 URL (record[0].logo)
    public int rank;          // record[0].rank ("1" "2" "3" "4")
    public int stroke;        // 排名粗体标记 (0 / 1)

    public int played;        // 场次 record[1]
    public int won, draw, lost;  // record[2] 形如 "1/0/0"
    public int goalsFor, goalsAgainst;  // record[3] 形如 "2/0"
    public int points;        // 积分 record[4]

    public boolean qualified;  // 晋级32强 (bgColor=#456de6)
    public boolean uncertain;  // 晋级待定 (bgColor=#858585)
    public String statusText; // "晋级32强" / "晋级待定" / null
}
