package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.api.BaiduLiveMatchClient;
import com.fifa.plugin.api.BaiduSportsClient;
import com.fifa.plugin.model.Match;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 直播面板 - 数据源: 百度体育 /al/live/detail (分析 tab)
 * <p>
 * 上半部: 赛程里正在直播 / 即将开始的比赛 (含 FINISHED 回顾)
 * 点击行 → 弹出 LiveMatchDialog 拉详情
 */
public class LivePanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(LivePanel.class);

    private final DefaultTableModel matchListModel;
    private final JBLabel statusLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "FIFA-Live-Poller");
                t.setDaemon(true);
                return t;
            }
    );

    /** matchId → Match (供详情查) */
    private final Map<String, Match> matchCache = new LinkedHashMap<>();
    /** matchId → 详情缓存 (LiveMatch, 分析 tab) */
    private final Map<String, BaiduLiveMatchClient.LiveMatch> detailCache = new LinkedHashMap<>();
    /** matchId → 文字直播事件列表 (赛况 tab) */
    private final Map<String, List<BaiduLiveMatchClient.TextLiveEvent>> textLiveCache = new LinkedHashMap<>();

    public LivePanel() {
        setLayout(new BorderLayout());

        // ---- 顶部 ----
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel title = new JLabel("⚽ 直播中 / 即将开赛 / 已结束");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        top.add(title);
        top.add(Box.createHorizontalStrut(12));
        JLabel hint = new JLabel("<html><small>数据来源: 百度体育 tiyu.baidu.com · 点击行查看详情 · 60 秒刷新</small></html>");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        top.add(hint);
        add(top, BorderLayout.NORTH);

        // ---- 比赛列表 ----
        // 6 列显示 + 1 隐藏列存 baiduMatchId
        String[] columns = {"时间", "主队", "比分", "客队", "状态", "阶段", "baiduMatchId"};
        matchListModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JBTable matchTable = new JBTable(matchListModel);
        matchTable.setRowHeight(24);
        matchTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        matchTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        matchTable.getColumnModel().getColumn(2).setPreferredWidth(50);
        matchTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        matchTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        matchTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        // 隐藏 baiduMatchId 列
        matchTable.getColumnModel().getColumn(6).setMinWidth(0);
        matchTable.getColumnModel().getColumn(6).setMaxWidth(0);
        matchTable.getColumnModel().getColumn(6).setPreferredWidth(0);

        // 双击 / 单击 → 弹出详情
        matchTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    int row = matchTable.rowAtPoint(e.getPoint());
                    if (row < 0) return;
                    int viewRow = matchTable.convertRowIndexToModel(row);
                    String matchId = (String) matchListModel.getValueAt(viewRow, 6);
                    if (matchId == null || matchId.isEmpty()) return;
                    openDetailDialog(matchId);
                }
            }
        });

        add(new JBScrollPane(matchTable), BorderLayout.CENTER);

        // ---- 状态栏 ----
        statusLabel = new JBLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        loadMatches();
        scheduler.scheduleWithFixedDelay(this::loadMatches, 60, 60, TimeUnit.SECONDS);
    }

    private void loadMatches() {
        BaiduSportsClient baidu = ApplicationManager.getApplication()
                .getService(BaiduSportsClient.class);
        if (baidu == null) return;
        try {
            BaiduSportsClient.ScheduleBundle bundle = baidu.fetchSchedule();
            // 收集正在直播 / 已结束 / 24h 内即将开赛
            java.util.Date now = new java.util.Date();
            List<Match> liveOrUpcoming = new ArrayList<>();
            matchCache.clear();
            for (Match m : bundle.matches) {
                String status = m.getStatus() != null ? m.getStatus() : "";
                if ("IN_PLAY".equals(status) || "FINISHED".equals(status)) {
                    liveOrUpcoming.add(m);
                } else if ("SCHEDULED".equals(status) && m.getUtcDate() != null) {
                    try {
                        java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(m.getUtcDate());
                        long diff = zdt.toInstant().toEpochMilli() - now.getTime();
                        if (diff < 24 * 60 * 60 * 1000L) {
                            liveOrUpcoming.add(m);
                        }
                    } catch (Exception ignored) {}
                }
            }
            // 按时间排序
            liveOrUpcoming.sort((a, b) -> {
                String ta = a.getUtcDate() != null ? a.getUtcDate() : "";
                String tb = b.getUtcDate() != null ? b.getUtcDate() : "";
                return ta.compareTo(tb);
            });

            SwingUtilities.invokeLater(() -> {
                matchListModel.setRowCount(0);
                for (Match m : liveOrUpcoming) {
                    String home = m.getHomeTeam() != null ? m.getHomeTeam().getShortName() : "?";
                    String away = m.getAwayTeam() != null ? m.getAwayTeam().getShortName() : "?";
                    String mid = m.getBaiduMatchId();
                    if (mid == null || mid.isEmpty()) {
                        mid = m.getUtcDate() + "|" + home + "|" + away;
                    }
                    matchCache.put(mid, m);
                    matchListModel.addRow(new Object[]{
                            m.getVenue() != null ? m.getVenue() : "-",
                            home,
                            formatScore(m),
                            away,
                            formatStatus(m.getStatus()),
                            formatStage(m.getStage()),
                            mid
                    });
                }
                if (liveOrUpcoming.isEmpty()) {
                    setStatus("⚠ 当前无直播 / 即将开赛 / 已结束的比赛 (或百度接口未返回)");
                } else {
                    setStatus("✓ 百度体育 · " + liveOrUpcoming.size() + " 场可关注 · " + new java.util.Date());
                }
            });
        } catch (Exception e) {
            LOG.warn("loadMatches failed", e);
        }
    }

    /**
     * 拉详情并弹出 dialog
     */
    private void openDetailDialog(String matchId) {
        // 立即弹一个"加载中"的 dialog (占位)
        Match meta = matchCache.get(matchId);
        BaiduLiveMatchClient.LiveMatch placeholder = new BaiduLiveMatchClient.LiveMatch();
        if (meta != null) {
            placeholder.matchId = matchId;
            placeholder.leftName = meta.getHomeTeam() != null ? meta.getHomeTeam().getShortName() : "?";
            placeholder.rightName = meta.getAwayTeam() != null ? meta.getAwayTeam().getShortName() : "?";
            placeholder.leftGoal = (meta.getScore() != null && meta.getScore().getFullTime() != null
                    && meta.getScore().getFullTime().getHome() != null)
                    ? meta.getScore().getFullTime().getHome() : 0;
            placeholder.rightGoal = (meta.getScore() != null && meta.getScore().getFullTime() != null
                    && meta.getScore().getFullTime().getAway() != null)
                    ? meta.getScore().getFullTime().getAway() : 0;
            placeholder.matchStatusText = formatStatus(meta.getStatus());
            placeholder.matchStage = formatStage(meta.getStage());
            placeholder.time = meta.getVenue() != null ? meta.getVenue() : "";
            placeholder.date = "";
        }

        // 先看缓存
        if (detailCache.containsKey(matchId)) {
            new LiveMatchDialog(detailCache.get(matchId),
                    textLiveCache.getOrDefault(matchId, Collections.emptyList())).show();
            return;
        }

        // 异步拉
        setStatus("⚡ 拉取比赛详情: " + matchId.substring(0, Math.min(20, matchId.length())) + "...");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                BaiduLiveMatchClient client = ApplicationManager.getApplication()
                        .getService(BaiduLiveMatchClient.class);
                if (client == null) return;
                BaiduLiveMatchClient.LiveMatch fetched = client.fetch(matchId);
                final BaiduLiveMatchClient.LiveMatch finalM = fetched != null ? fetched : placeholder;
                detailCache.put(matchId, finalM);

                // 文字直播: 失败时不阻塞, 用空列表
                List<BaiduLiveMatchClient.TextLiveEvent> liveEvents;
                try {
                    liveEvents = client.fetchMatchTextLive(matchId);
                } catch (Exception ex) {
                    LOG.warn("text live fetch failed for " + matchId, ex);
                    liveEvents = Collections.emptyList();
                }
                final List<BaiduLiveMatchClient.TextLiveEvent> finalLive = liveEvents;
                textLiveCache.put(matchId, finalLive);

                SwingUtilities.invokeLater(() -> {
                    new LiveMatchDialog(finalM, finalLive).show();
                    setStatus("✓ 详情已加载 · " + new java.util.Date());
                });
            } catch (Exception e) {
                LOG.warn("openDetailDialog failed", e);
                SwingUtilities.invokeLater(() -> {
                    new LiveMatchDialog(placeholder, Collections.emptyList()).show();
                    setStatus("⚠ 详情拉取失败, 已显示本地元数据");
                });
            }
        });
    }

    private String formatScore(Match m) {
        if (m.getScore() != null && m.getScore().getFullTime() != null) {
            Match.ScoreDetail ft = m.getScore().getFullTime();
            Integer h = ft.getHome();
            Integer a = ft.getAway();
            if (h != null && a != null) return h + " - " + a;
        }
        return "vs";
    }

    private String formatStatus(String s) {
        if (s == null) return "";
        return switch (s) {
            case "IN_PLAY" -> "🔴 直播中";
            case "FINISHED" -> "✅ 已结束";
            case "SCHEDULED" -> "⏳ 未开赛";
            default -> s;
        };
    }

    private String formatStage(String s) {
        if (s == null) return "";
        return switch (s) {
            case "GROUP_STAGE" -> "小组赛";
            case "LAST_16" -> "16强";
            case "QUARTER_FINALS" -> "8强";
            case "SEMI_FINALS" -> "半决赛";
            case "FINAL" -> "决赛";
            default -> s;
        };
    }

    public void refresh() {
        loadMatches();
    }

    private void setStatus(String text) { statusLabel.setText(text); }
}
