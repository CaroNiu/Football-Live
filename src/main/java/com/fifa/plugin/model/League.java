package com.fifa.plugin.model;

/**
 * 赛事 (联赛/杯赛) 模型 — 全赛事直播的一等公民
 * <p>
 * 主键是百度赛事名 (baiduKey, 如 "英超" / "世界杯"),
 * 所有百度接口 (schedules / 排名 / 新闻) 都用它作 match= 参数
 */
public class League {

    /** 百度赛事名, 同时是各百度接口的 match= 参数 */
    private final String baiduKey;

    /** 赛事 logo */
    private final String logoUrl;

    /** 今日场次 ("今日1场" 解析出的数字, -1 = 未知) */
    private final int todayCount;

    /** 今日进行中场次 */
    private final int todayLiveCount;

    /** 官方排序权重 (百度目录 Order, 越小越靠前) */
    private final int order;

    public League(String baiduKey, String logoUrl, int todayCount, int todayLiveCount, int order) {
        this.baiduKey = baiduKey;
        this.logoUrl = logoUrl;
        this.todayCount = todayCount;
        this.todayLiveCount = todayLiveCount;
        this.order = order;
    }

    public String getBaiduKey() { return baiduKey; }
    public String getLogoUrl() { return logoUrl; }
    public int getTodayCount() { return todayCount; }
    public int getTodayLiveCount() { return todayLiveCount; }
    public int getOrder() { return order; }

    @Override
    public boolean equals(Object o) {
        return o instanceof League && baiduKey.equals(((League) o).baiduKey);
    }

    @Override
    public int hashCode() { return baiduKey.hashCode(); }

    @Override
    public String toString() { return baiduKey; }
}
