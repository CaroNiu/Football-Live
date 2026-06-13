package com.fifa.plugin.core;

import com.fifa.plugin.api.FootballDataClient;
import com.fifa.plugin.cache.MatchCache;
import com.fifa.plugin.model.Match;
import com.fifa.plugin.model.StandingResponse;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 赛事管理器 - 核心调度逻辑
 */
@Service
public final class TournamentManager {

    private static final Logger LOG = Logger.getInstance(TournamentManager.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "FIFA-Poller")
    );

    private volatile List<Match> liveMatches = Collections.emptyList();
    private volatile List<Match> todayMatches = Collections.emptyList();
    private volatile StandingResponse standings;

    private final FootballDataClient apiClient;
    private final MatchCache cache;

    public TournamentManager() {
        this.apiClient = ApplicationManager.getApplication().getService(FootballDataClient.class);
        this.cache = ApplicationManager.getApplication().getService(MatchCache.class);
    }

    /**
     * 启动后台轮询
     */
    public void startPolling() {
        // 首次加载
        refreshAll();

        // 比赛日: 每30秒轮询实时比分
        // 非比赛日: 每5分钟轮询
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                refreshLiveMatches();
            } catch (Exception e) {
                LOG.warn("Failed to refresh live matches", e);
            }
        }, 30, 30, TimeUnit.SECONDS);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                refreshTodayMatches();
            } catch (Exception e) {
                LOG.warn("Failed to refresh today matches", e);
            }
        }, 5, 5, TimeUnit.MINUTES);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                refreshStandings();
            } catch (Exception e) {
                LOG.warn("Failed to refresh standings", e);
            }
        }, 10, 10, TimeUnit.MINUTES);
    }

    /**
     * 停止轮询
     */
    public void stopPolling() {
        scheduler.shutdown();
    }

    /**
     * 刷新全部数据
     */
    public void refreshAll() {
        try {
            refreshLiveMatches();
            refreshTodayMatches();
            refreshStandings();
        } catch (Exception e) {
            LOG.error("Failed to refresh all data", e);
        }
    }

    // ---- 数据获取 ----

    private void refreshLiveMatches() {
        try {
            liveMatches = apiClient.fetchLiveMatches();
        } catch (IOException e) {
            LOG.warn("Live matches fetch error", e);
        }
    }

    private void refreshTodayMatches() {
        try {
            todayMatches = apiClient.fetchTodayMatches();
        } catch (IOException e) {
            LOG.warn("Today matches fetch error", e);
        }
    }

    private void refreshStandings() {
        try {
            standings = apiClient.fetchStandings();
            cache.putStandings(apiClient.getCompetitionCode(), standings);
        } catch (IOException e) {
            LOG.warn("Standings fetch error", e);
            // 尝试从缓存获取
            standings = cache.getStandings(apiClient.getCompetitionCode());
        }
    }

    // ---- Getters ----

    public List<Match> getLiveMatches() {
        return liveMatches;
    }

    public List<Match> getTodayMatches() {
        return todayMatches;
    }

    public StandingResponse getStandings() {
        if (standings == null) {
            standings = cache.getStandings(apiClient.getCompetitionCode());
        }
        return standings;
    }
}
