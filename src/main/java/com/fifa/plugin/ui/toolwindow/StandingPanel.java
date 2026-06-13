package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.api.BaiduRankingsClient;
import com.fifa.plugin.model.BaiduRankedTeam;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 积分榜面板 - 数据源: 百度体育 /al/match?tab=排名 JSON API
 * <p>
 * 12 组积分榜 (A-L), 每组 4 队, 含队徽 / 场次 / 胜平负 / 进失球 / 积分 / 晋级状态
 */
public class StandingPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(StandingPanel.class);

    private static final String[] GROUPS = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};

    private final JComboBox<String> groupSelector;
    private final JBTable table;
    private final DefaultTableModel tableModel;
    private final JBLabel statusLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "FIFA-Standings-Poller");
                t.setDaemon(true);
                return t;
            }
    );

    /** 缓存: group 字母 → 该组 4 队 */
    private Map<String, List<BaiduRankedTeam>> cache = new java.util.LinkedHashMap<>();

    public StandingPanel() {
        setLayout(new BorderLayout());

        // ---- 顶部: 组选择器 ----
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(new JBLabel("小组:"));

        groupSelector = new JComboBox<>(GROUPS);
        groupSelector.setPreferredSize(new Dimension(80, 28));
        groupSelector.addActionListener(e -> {
            String g = (String) groupSelector.getSelectedItem();
            if (g != null) renderGroup(g);
        });
        toolbar.add(groupSelector);

        JButton todayBtn = new JButton("今天");
        todayBtn.addActionListener(e -> {
            String g = (String) groupSelector.getSelectedItem();
            if (g != null) renderGroup(g);
        });
        toolbar.add(todayBtn);

        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> {
            cache.clear();
            loadAndShow();
        });
        toolbar.add(refreshBtn);

        toolbar.add(Box.createHorizontalStrut(12));

        add(toolbar, BorderLayout.NORTH);

        // ---- 表格 ----
        String[] columns = {"#", "球队", "场", "胜", "平", "负", "进", "失", "净胜", "积分", "状态"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JBTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        for (int i = 2; i < columns.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(50);
        }

        // 不设自定义 renderer, 用 JTable 默认渲染 — 之前的高亮跟 focus 边框冲突
        // 会让 #1-2 行文字看不见, 直接去掉最稳
        add(new JBScrollPane(table), BorderLayout.CENTER);

        // ---- 状态栏 ----
        statusLabel = new JBLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        loadAndShow();
        scheduler.scheduleWithFixedDelay(this::loadAndShow, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 拉取数据, 填缓存, 默认显示 A 组
     */
    private void loadAndShow() {
        BaiduRankingsClient client = ApplicationManager.getApplication()
                .getService(BaiduRankingsClient.class);
        if (client == null) {
            setStatus("⚠ 服务未注册");
            return;
        }
        try {
            BaiduRankingsClient.RankingBundle bundle = client.fetch();
            SwingUtilities.invokeLater(() -> {
                cache = bundle.groups;
                if (cache.isEmpty()) {
                    setStatus("⚠ 暂无积分榜数据");
                } else {
                    setStatus("✓ 已加载 " + cache.size() + " 个小组 · " + new java.util.Date());
                }
                String sel = (String) groupSelector.getSelectedItem();
                if (sel == null) sel = "A";
                renderGroup(sel);
            });
        } catch (Exception e) {
            LOG.warn("loadAndShow failed", e);
            SwingUtilities.invokeLater(() ->
                    setStatus("⚠ 拉取失败: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /**
     * 渲染指定组 (A-L)
     */
    private void renderGroup(String group) {
        List<BaiduRankedTeam> teams = cache.get(group);
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            if (teams == null || teams.isEmpty()) {
                setStatus("⚠ Group " + group + " 数据尚未返回 (赛事可能尚未开始)");
                return;
            }
            for (BaiduRankedTeam t : teams) {
                int gd = t.goalsFor - t.goalsAgainst;
                String status = t.qualified ? "✓ 晋级" : (t.uncertain ? "待定" : "-");
                tableModel.addRow(new Object[]{
                        t.rank,
                        t.name,
                        t.played, t.won, t.draw, t.lost,
                        t.goalsFor, t.goalsAgainst,
                        formatDiff(gd),
                        t.points,
                        status
                });
            }
            setStatus("✓ Group " + group + " · " + teams.size() + " 队");
        });
    }

    @NotNull
    private static String formatDiff(int d) {
        return d > 0 ? "+" + d : String.valueOf(d);
    }

    public void refresh() {
        cache.clear();
        loadAndShow();
    }

    private void setStatus(String text) { statusLabel.setText(text); }

    @org.jetbrains.annotations.NotNull
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    private @interface NotNull {}
}
