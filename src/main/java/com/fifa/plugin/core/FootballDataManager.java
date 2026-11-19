package com.fifa.plugin.core;

import com.fifa.plugin.api.BaiduMatchListClient;
import com.fifa.plugin.cache.MatchCache;
import com.fifa.plugin.model.Match;
import com.fifa.plugin.settings.FifaSettingsState;
import com.fifa.plugin.ui.toolwindow.FifaToolWindowFactory;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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

    /** 数据刷新完成后的 UI 回调 (StatusBar widget 自动重绘等); 回调内部自行回 EDT */
    private final CopyOnWriteArrayList<Runnable> uiRefreshListeners = new CopyOnWriteArrayList<>();

    /** 进球/开赛检测: baiduMatchId → "h:a|status" (上一轮快照) */
    private final Map<String, String> lastLiveState = new ConcurrentHashMap<>();
    /** 防止重复注册轮询调度器 (多项目状态栏各 install 一次) */
    private volatile boolean pollingStarted = false;

    public FootballDataManager() {
        this.cache = ApplicationManager.getApplication().getService(MatchCache.class);
    }

    /**
     * 启动后台轮询 (幂等: 多个 widget install 只注册一次调度器)
     */
    public void startPolling() {
        refreshAll();
        if (pollingStarted) return;
        pollingStarted = true;
        maybePromptRating();
        // 60s: 单请求覆盖 live + 今日 + 近 3 天赛程
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                refreshAll();
            } catch (Exception e) {
                LOG.warn("Failed to refresh all football data", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    // ---- 评分引导 (一次性, 使用满 7 天后弹一次) ----

    /**
     * 首次运行记录起始日期; 满 7 天且未弹过时温和提示一次去 Marketplace 评分。
     * 只弹一次 (ratingPrompted 持久化), 不重复骚扰。
     */
    private void maybePromptRating() {
        try {
            FifaSettingsState settings = FifaSettingsState.getInstance();
            if (settings.isRatingPrompted()) return;
            java.time.LocalDate today = java.time.LocalDate.now();
            String first = settings.getFirstUseDate();
            if (first.isEmpty()) {
                settings.setFirstUseDate(today.toString());
                return;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.parse(first), today);
            if (days < 7) return;
            settings.setRatingPrompted(true);
            Notification n = NotificationGroupManager.getInstance()
                    .getNotificationGroup("FIFA.Notification")
                    .createNotification("⚽ 用 Football Live 一周了",
                            "如果它陪你看了几场好球, 去 Marketplace 给个评价吧 — 这对独立插件很重要",
                            NotificationType.INFORMATION);
            n.addAction(new AnAction("打开 Marketplace") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(
                                "https://plugins.jetbrains.com/search?search=Football+Live&plugins=true"));
                    } catch (Exception ex) {
                        LOG.warn("open marketplace failed", ex);
                    }
                }
            });
            n.notify(null);
        } catch (Exception ex) {
            LOG.warn("rating prompt failed", ex);
        }
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
            detectMatchEvents(live, all);
            notifyUiListeners();
        } catch (IOException e) {
            LOG.warn("All-football fetch error", e);
        }
    }

    // ---- 进球 / 开赛检测 (BALLOON 通知) ----

    /**
     * 对比相邻两轮快照: 关注范围 (关注赛事 ∪ 选中赛事 ∪ 关注球队) 内的比赛
     * 进球 → 进球通知; SCHEDULED/TIMED → IN_PLAY → 开赛通知。
     * 首轮观测只建快照不通知, 避免启动轰炸。
     */
    private void detectMatchEvents(List<Match> live, List<Match> all) {
        if (!FifaSettingsState.getInstance().isEnableMatchNotification()) return;

        Set<String> scope = new HashSet<>(LeagueRegistry.getInstance().getFollowed());
        String selected = LeagueRegistry.getInstance().getSelected();
        if (selected != null && !selected.isEmpty()) scope.add(selected);
        String favoriteTeam = FifaSettingsState.getInstance().getFavoriteTeam();

        for (Match m : live) {
            String id = m.getBaiduMatchId();
            if (id == null || id.isEmpty()) continue;

            Integer sh = null, sa = null;
            if (m.getScore() != null && m.getScore().getFullTime() != null) {
                sh = m.getScore().getFullTime().getHome();
                sa = m.getScore().getFullTime().getAway();
            }
            String score = (sh == null ? "-" : sh) + ":" + (sa == null ? "-" : sa);
            String status = m.getStatus() == null ? "" : m.getStatus();
            String state = score + "|" + status;

            String prev = lastLiveState.put(id, state);
            if (prev == null) continue; // 首次见到, 只建基线

            // 关注范围过滤
            String home = m.getHomeTeam() != null && m.getHomeTeam().getName() != null
                    ? m.getHomeTeam().getName() : "";
            String away = m.getAwayTeam() != null && m.getAwayTeam().getName() != null
                    ? m.getAwayTeam().getName() : "";
            String league = m.getLeagueName() == null ? "" : m.getLeagueName();
            boolean teamHit = favoriteTeam != null && !favoriteTeam.isEmpty()
                    && (home.contains(favoriteTeam) || away.contains(favoriteTeam));
            boolean leagueHit = scope.contains(league);
            if (!teamHit && !leagueHit) continue;

            int sep = prev.indexOf('|');
            String prevScore = sep > 0 ? prev.substring(0, sep) : prev;
            String prevStatus = sep > 0 ? prev.substring(sep + 1) : "";

            // 开赛提醒 (保持设置项"比赛开始时发送通知提醒"的语义)
            if (!"IN_PLAY".equals(prevStatus) && "IN_PLAY".equals(status)) {
                notifyMatch("🔴 开赛: " + home + " vs " + away,
                        league + (m.getRoundName() != null ? " · " + m.getRoundName() : ""));
            }
            // 进球提醒 (进行中且比分变化; 一次快照内连进两球显示为一次 旧→新)
            if ("IN_PLAY".equals(status) && !prevScore.equals(score)) {
                String minute = m.getMinute() != null ? m.getMinute() + "' " : "";
                notifyMatch("⚽ 进球! " + home + " " + score + " " + away,
                        league + (m.getRoundName() != null ? " · " + m.getRoundName() : "")
                                + " · " + minute + "(原比分 " + prevScore + ")");
            }
        }

        // 清理已结束比赛的快照条目, 防止 map 膨胀
        Set<String> liveIds = new HashSet<>();
        for (Match m : live) {
            if (m.getBaiduMatchId() != null) liveIds.add(m.getBaiduMatchId());
        }
        for (String id : lastLiveState.keySet()) {
            if (liveIds.contains(id)) continue;
            for (Match m : all) {
                if (id.equals(m.getBaiduMatchId()) && "FINISHED".equals(m.getStatus())) {
                    lastLiveState.remove(id);
                    break;
                }
            }
        }
    }

    private void notifyMatch(@NotNull String title, @NotNull String content) {
        try {
            Notification n = NotificationGroupManager.getInstance()
                    .getNotificationGroup("FIFA.Notification")
                    .createNotification(title, content, NotificationType.INFORMATION);
            n.addAction(new AnAction("打开直播") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    openLiveToolWindow();
                }
            });
            n.notify(null); // 无 project → 全局 BALLOON
        } catch (Exception ex) {
            LOG.warn("notify failed", ex);
        }
    }

    /** 打开 (第一个打开的项目) 的足球直播工具窗并切到直播 Tab */
    private void openLiveToolWindow() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length == 0) return;
        Project p = projects[0];
        ToolWindow tw = ToolWindowManager.getInstance(p).getToolWindow("FIFA");
        if (tw != null) {
            FifaToolWindowFactory.showLiveTab(p);
            tw.activate(() -> FifaToolWindowFactory.showLiveTab(p));
        }
    }

    // ---- UI 刷新回调 (widget 自动重绘) ----

    public void addUiRefreshListener(Runnable r) {
        uiRefreshListeners.add(r);
    }

    public void removeUiRefreshListener(Runnable r) {
        uiRefreshListeners.remove(r);
    }

    private void notifyUiListeners() {
        for (Runnable r : uiRefreshListeners) {
            try {
                java.awt.EventQueue.invokeLater(r); // 轮询线程 → EDT
            } catch (Exception e) {
                LOG.warn("ui refresh listener error", e);
            }
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
