package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.api.BaiduRankingsClient;
import com.fifa.plugin.core.LeagueRegistry;
import com.fifa.plugin.model.BaiduRankedTeam;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.fifa.plugin.ui.component.ClickableStatusLabel;
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
        table.setAutoCreateRowSorter(true); // 列头点击排序 (积分/净胜球等数值列按数值比)
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        for (int i = 2; i < columns.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(50);
        }
        add(new JBScrollPane(table), BorderLayout.CENTER);

        // 点击球队行 → 回调 (由工厂接线: 跳赛程 Tab 按球队过滤)
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.getSelectedRow();
                if (row < 0 || teamClickListener == null) return;
                int modelRow = table.convertRowIndexToModel(row); // 兼容列头排序后的视图行
                String team = String.valueOf(tableModel.getValueAt(modelRow, 1));
                if (!team.isEmpty() && !"?".equals(team)) teamClickListener.accept(team);
            }
        });

        statusLabel = new ClickableStatusLabel(this::refresh);
        add(statusLabel, BorderLayout.SOUTH);

        // 跟随全局赛事切换
        LeagueRegistry.getInstance().addListener(() -> SwingUtilities.invokeLater(this::refresh));

        refresh();
        scheduler.scheduleWithFixedDelay(() -> { if (visibilityGate.getAsBoolean()) refresh(); }, 60, 60, TimeUnit.SECONDS);
    }

    /** 球队点击回调 (工厂注入; null = 不响应点击) */
    public void setTeamClickListener(java.util.function.Consumer<String> listener) {
        this.teamClickListener = listener;
    }

    private volatile java.util.function.Consumer<String> teamClickListener;

    /** 工具窗可见性闸门 (工厂注入 toolWindow::isVisible): 隐藏时轮询空转省资源 */
    public void setVisibilityGate(java.util.function.BooleanSupplier gate) {
        visibilityGate = gate;
        if (showRefreshListener == null) {
            showRefreshListener = new javax.swing.event.AncestorListener() {
                @Override
                public void ancestorAdded(javax.swing.event.AncestorEvent e) { refresh(); } // 重新显示立即补一次刷新
                @Override
                public void ancestorRemoved(javax.swing.event.AncestorEvent e) {}
                @Override
                public void ancestorMoved(javax.swing.event.AncestorEvent e) {}
            };
            addAncestorListener(showRefreshListener);
        }
    }

    private volatile java.util.function.BooleanSupplier visibilityGate = () -> true;
    private javax.swing.event.AncestorListener showRefreshListener;

    public void refresh() {
        // 网络 IO 在 pooled 线程 (构造器/监听器可能在 EDT 调用进来)
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            BaiduRankingsClient client = ApplicationManager.getApplication()
                    .getService(BaiduRankingsClient.class);
            if (client == null) return;

            String league = LeagueRegistry.getInstance().getSelectedOrWorldCup();
            try {
                List<BaiduRankedTeam> rankings = client.fetchTeamRankings(league);
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    if (rankings.isEmpty()) {
                        setStatus("⚠ 暂无 " + league + " 球队数据");
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
                    setStatus("✓ " + league + " · 已加载 " + rankings.size() + " 支球队 · " + new java.util.Date());
                });
            } catch (Exception e) {
                LOG.warn("Team ranking refresh error", e);
                SwingUtilities.invokeLater(() ->
                        setStatus("⚠ 拉取失败: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        });
    }

    @NotNull
    private static String formatDiff(int d) {
        return d > 0 ? "+" + d : String.valueOf(d);
    }


    private void setStatus(String text) { statusLabel.setText(text); }
}
