package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.api.BaiduSportsClient;
import com.fifa.plugin.model.Match;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 赛程面板 - 数据源: 百度体育 JSON API (/al/api/match/schedules)
 * <p>
 * 顶部日期下拉来自 API 响应里的 select.labels[] (只有真有比赛的日期)
 */
public class SchedulePanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(SchedulePanel.class);

    private final JComboBox<BaiduSportsClient.DateLabel> dateSelector;
    private final JBTable table;
    private final DefaultTableModel tableModel;
    private final JBLabel statusLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "FIFA-Schedule-Poller");
                t.setDaemon(true);
                return t;
            }
    );

    /** 缓存: 日期 → 该日比赛列表 */
    private final Map<LocalDate, List<Match>> cache = new LinkedHashMap<>();
    /** 全部有比赛的日期 (来自 API 的 select.labels[]) */
    private List<BaiduSportsClient.DateLabel> dateLabels = new ArrayList<>();

    public SchedulePanel() {
        setLayout(new BorderLayout());

        // ---- 顶部: 日期选择器 ----
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(new JBLabel("日期:"));

        dateSelector = new JComboBox<>();
        dateSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BaiduSportsClient.DateLabel dl) {
                    LocalDate today = LocalDate.now();
                    String label = dl.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    if (dl.suffix != null && !dl.suffix.isEmpty()) label += " · " + dl.suffix;
                    if (dl.desc != null && !dl.desc.isEmpty()) label += " · " + dl.desc;
                    if (dl.date.equals(today)) label += " (今天)";
                    else if (dl.date.equals(today.plusDays(1))) label += " (明天)";
                    setText(label);
                }
                return this;
            }
        });
        dateSelector.setPreferredSize(new Dimension(240, 28));
        dateSelector.addActionListener(e -> {
            BaiduSportsClient.DateLabel dl = (BaiduSportsClient.DateLabel) dateSelector.getSelectedItem();
            if (dl != null) showForDate(dl.date);
        });
        toolbar.add(dateSelector);

        JButton todayBtn = new JButton("今天");
        todayBtn.addActionListener(e -> {
            LocalDate today = LocalDate.now();
            for (int i = 0; i < dateSelector.getItemCount(); i++) {
                if (dateSelector.getItemAt(i).date.equals(today)) {
                    dateSelector.setSelectedIndex(i);
                    return;
                }
            }
            dateSelector.setSelectedIndex(0);
        });
        toolbar.add(todayBtn);

        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> {
            cache.clear();
            loadAndShow();
        });
        toolbar.add(refreshBtn);

        toolbar.add(Box.createHorizontalStrut(12));
        JBLabel hint = new JBLabel("<html><small>数据来源: 百度体育 tiyu.baidu.com/al/api · 30 秒刷新</small></html>");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        toolbar.add(hint);

        add(toolbar, BorderLayout.NORTH);

        // ---- 表格 ----
        String[] columns = {"时间", "阶段", "组", "主队", "比分", "客队", "状态"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JBTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(40);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getColumnModel().getColumn(4).setPreferredWidth(60);
        table.getColumnModel().getColumn(5).setPreferredWidth(140);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);
        add(new JBScrollPane(table), BorderLayout.CENTER);

        // ---- 状态栏 ----
        statusLabel = new JBLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        loadAndShow();
        scheduler.scheduleWithFixedDelay(this::loadAndShow, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 拉取一次数据, 填好日期下拉, 缓存, 显示今天
     */
    private void loadAndShow() {
        BaiduSportsClient baidu = ApplicationManager.getApplication()
                .getService(BaiduSportsClient.class);
        if (baidu == null) {
            setStatus("⚠ BaiduSportsClient 服务未注册");
            return;
        }
        try {
            BaiduSportsClient.ScheduleBundle bundle = baidu.fetchSchedule();
            SwingUtilities.invokeLater(() -> {
                // 缓存
                cache.clear();
                for (Match m : bundle.matches) {
                    if (m.getUtcDate() == null) continue;
                    try {
                        String day = m.getUtcDate().substring(0, 10);
                        LocalDate ld = LocalDate.parse(day);
                        cache.computeIfAbsent(ld, k -> new ArrayList<>()).add(m);
                    } catch (Exception ignored) {}
                }
                // 日期下拉
                dateLabels = bundle.dateLabels;
                dateSelector.removeAllItems();
                LocalDate today = LocalDate.now();
                int todayIdx = 0;
                int i = 0;
                for (BaiduSportsClient.DateLabel dl : dateLabels) {
                    if (dl.disabled) continue;  // 跳过 disabled
                    dateSelector.addItem(dl);
                    if (dl.date.equals(today)) todayIdx = i;
                    i++;
                }
                if (dateSelector.getItemCount() > 0) {
                    dateSelector.setSelectedIndex(todayIdx);
                }
                if (bundle.matches.isEmpty()) {
                    setStatus("⚠ 百度体育 API 未返回数据");
                } else {
                    setStatus("✓ 百度体育 · 已加载 " + bundle.matches.size() + " 场比赛 · " + new java.util.Date());
                }
            });
        } catch (Exception e) {
            LOG.warn("loadAndShow failed", e);
            SwingUtilities.invokeLater(() ->
                    setStatus("⚠ 拉取失败: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /**
     * 显示指定日期的赛程
     */
    private void showForDate(LocalDate date) {
        List<Match> matches = cache.get(date);
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            if (matches == null || matches.isEmpty()) {
                setStatus("⚠ " + date + " 当日无比赛 (或尚未从 API 加载)");
                return;
            }
            // 按开球时间排序
            matches.sort((x, y) -> {
                String xt = x.getUtcDate() != null ? x.getUtcDate() : "";
                String yt = y.getUtcDate() != null ? y.getUtcDate() : "";
                return xt.compareTo(yt);
            });
            for (Match m : matches) {
                tableModel.addRow(new Object[]{
                        m.getVenue() != null ? m.getVenue() : "-",
                        formatStage(m.getStage()),
                        m.getGroup() != null ? m.getGroup() : "-",
                        m.getHomeTeam() != null ? m.getHomeTeam().getShortName() : "?",
                        formatScore(m),
                        m.getAwayTeam() != null ? m.getAwayTeam().getShortName() : "?",
                        formatStatus(m.getStatus())
                });
            }
            setStatus("✓ " + date + " · " + matches.size() + " 场比赛 · 百度体育 JSON API");
        });
    }

    /**
     * 外部 "刷新全部" 按钮调用
     */
    public void refresh() {
        cache.clear();
        loadAndShow();
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

    private String formatStage(String stage) {
        if (stage == null) return "";
        return switch (stage) {
            case "GROUP_STAGE" -> "小组赛";
            case "LAST_32" -> "32强";
            case "LAST_16" -> "16强";
            case "QUARTER_FINALS" -> "8强";
            case "SEMI_FINALS" -> "半决赛";
            case "FINAL" -> "决赛";
            case "THIRD_PLACE" -> "季军赛";
            default -> stage;
        };
    }

    private String formatStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "SCHEDULED" -> "⏳ 未开赛";
            case "TIMED" -> "🕐 已定时";
            case "IN_PLAY" -> "🔴 进行中";
            case "PAUSED" -> "⏸ 暂停";
            case "FINISHED" -> "✅ 已结束";
            default -> status;
        };
    }

    private void setStatus(String text) { statusLabel.setText(text); }
}
