package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.api.BaiduRankingsClient;
import com.fifa.plugin.model.BaiduRankedTeam;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 球队榜: 全部参赛队按 积分 / 净胜球 / 进球数 跨组排序
 * <p>
 * 数据源: 百度体育 /al/match?tab=排名 (复用积分榜, 12 组 flatten + 跨组排序).
 * 之前用 TheSportsDB 本地从 rounds 1-3 算, 在比赛未开打时拿不到数据 — 改用百度直接给的小组赛果.
 */
public class TeamRankingPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(TeamRankingPanel.class);

    private final JBTable table;
    private final DefaultTableModel tableModel;
    private final JBLabel statusLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "FIFA-TeamRanking-Poller");
                t.setDaemon(true);
                return t;
            }
    );

    public TeamRankingPanel() {
        setLayout(new BorderLayout());

        String[] columns = {"#", "球队", "场", "胜", "平", "负", "进球", "失球", "净胜", "积分"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JBTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        for (int i = 2; i < columns.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(50);
        }
        add(new JBScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JBLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        refresh();
        scheduler.scheduleWithFixedDelay(this::refresh, 60, 60, TimeUnit.SECONDS);
    }

    public void refresh() {
        BaiduRankingsClient client = ApplicationManager.getApplication()
                .getService(BaiduRankingsClient.class);
        if (client == null) return;

        try {
            List<BaiduRankedTeam> rankings = client.fetchTeamRankings();
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                if (rankings.isEmpty()) {
                    setStatus("⚠ 暂无球队数据");
                    return;
                }
                int rank = 1;
                for (BaiduRankedTeam t : rankings) {
                    int diff = t.goalsFor - t.goalsAgainst;
                    tableModel.addRow(new Object[]{
                            rank++,
                            t.name != null ? t.name : "?",
                            t.played, t.won, t.draw, t.lost,
                            t.goalsFor, t.goalsAgainst,
                            formatDiff(diff),
                            t.points
                    });
                }
                setStatus("✓ 已加载 " + rankings.size() + " 支球队 · " + new java.util.Date());
            });
        } catch (Exception e) {
            LOG.warn("Team ranking refresh error", e);
            SwingUtilities.invokeLater(() ->
                    setStatus("⚠ 拉取失败: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @NotNull
    private static String formatDiff(int d) {
        return d > 0 ? "+" + d : String.valueOf(d);
    }

    private void setStatus(String text) { statusLabel.setText(text); }
}
