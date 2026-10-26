package com.fifa.plugin.core;

import com.fifa.plugin.api.BaiduMatchListClient;
import com.fifa.plugin.cache.MatchCache;
import com.fifa.plugin.model.Match;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 足球数据管理器 — 核心调度逻辑 (全赛事版, 替代原世界杯单赛事 TournamentManager)
 * <p>
 * 数据源: 百度首页 SSR s-data (一次请求 = 近 3 天全部足球比赛, 跨所有联赛)。
 * 60s 轮询同时供: StatusBar 实时比分 / 今日赛程 / 直播列表, 请求量不随联赛数增长。
 * 积分榜由各面板自行轮询 (BaiduRankingsClient), 不在此处重复。
 */
@Service
public final class FootballDataManager {

    private static final Logger LOG = Logger.getInstance(FootballDataManager.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "FIFA-Poller")
    );

    private volatile List<Match> allFootball = Collections.emptyList();
    private volatile List<Match> liveMatches = Collections.emptyList();
    private volatile List<Match> todayMatches = Collections.emptyList();

    private final MatchCache cache;

    public FootballDataManager() {
        this.cache = ApplicationManager.getApplication().getService(MatchCache.class);
    }

    /**
     * 启动后台轮询
     */
    public void startPolling() {
        refreshAll();
        // 60s: 单请求覆盖 live + 今日 + 近 3 天赛程
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                refreshAll();
            } catch (Exception e) {
                LOG.warn("Failed to refresh all football data", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 停止轮询
     */
    public void stopPolling() {
        scheduler.shutdown();
    }

    /**
     * 刷新全部数据 (一次请求)
     */
    public void refreshAll() {
        try {
            BaiduMatchListClient client = ApplicationManager.getApplication()
                    .getService(BaiduMatchListClient.class);
            if (client == null) return;
            List<Match> all = client.fetchAllFootball().matches;
            allFootball = all;

            List<Match> live = new java.util.ArrayList<>();
            List<Match> today = new java.util.ArrayList<>();
            String todayStr = LocalDate.now().toString();
            for (Match m : all) {
                if (m.isLive()) live.add(m);
                if (m.getUtcDate() != null && m.getUtcDate().startsWith(todayStr)) today.add(m);
            }
            liveMatches = live;
            todayMatches = today;
            cache.putMatches("all-football", all);
        } catch (IOException e) {
            LOG.warn("All-football fetch error", e);
        }
    }

    // ---- Getters ----

    /** 近 3 天全部足球比赛 (跨联赛) */
    public List<Match> getAllFootball() {
        return allFootball;
    }

    /** 进行中 / 暂停的比赛 (StatusBar 用) */
    public List<Match> getLiveMatches() {
        return liveMatches;
    }

    /** 今日比赛 */
    public List<Match> getTodayMatches() {
        return todayMatches;
    }
}
