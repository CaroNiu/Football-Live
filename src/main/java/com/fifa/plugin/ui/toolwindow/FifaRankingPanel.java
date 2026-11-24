package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.i18n.FBundle;
import com.fifa.plugin.api.BaiduFifaRankingsClient;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.fifa.plugin.ui.component.ClickableStatusLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * FIFA 排名面板 - 数据源: 百度体育 /al/match?tab=FIFA排名 JSON API
 * <p>
 * 211 条 FIFA 官方排名 (全国家队), 30 分钟刷新
 */
public class FifaRankingPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(FifaRankingPanel.class);

    private final JBTable table;
    private final DefaultTableModel tableModel;
    private final JBLabel statusLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "FIFA-FifaRanking-Poller");
                t.setDaemon(true);
                return t;
            }
    );

    public FifaRankingPanel() {
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel title = new JLabel(FBundle.message("fifa.subtitle"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        top.add(title);
        top.add(Box.createHorizontalStrut(12));
        JLabel hint = new JLabel(FBundle.message("fifa.hint"));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        top.add(hint);
        add(top, BorderLayout.NORTH);

        String[] columns = {"#", FBundle.message("col.team"), FBundle.message("col.pts"), FBundle.message("col.rank.change")};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JBTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true); // 列头点击排序
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        add(new JBScrollPane(table), BorderLayout.CENTER);

        statusLabel = new ClickableStatusLabel(this::refresh);
        add(statusLabel, BorderLayout.SOUTH);

        refresh();
        scheduler.scheduleWithFixedDelay(() -> { if (visibilityGate.getAsBoolean()) refresh(); }, 30, 30, TimeUnit.MINUTES);
    }

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
        BaiduFifaRankingsClient client = ApplicationManager.getApplication()
                .getService(BaiduFifaRankingsClient.class);
        if (client == null) {
            setStatus(FBundle.message("st.service.missing"));
            return;
        }
        try {
            List<BaiduFifaRankingsClient.FifaRankEntry> rankings = client.fetch(220);
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                if (rankings.isEmpty()) {
                    setStatus(FBundle.message("fifa.none"));
                    return;
                }
                for (BaiduFifaRankingsClient.FifaRankEntry r : rankings) {
                    tableModel.addRow(new Object[]{
                            r.rank,
                            r.name,
                            r.points,
                            formatChange(r.change)
                    });
                }
                setStatus("✓ " + FBundle.message("fifa.loaded", rankings.size()) + " " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            });
        } catch (Exception e) {
            LOG.warn("FIFA ranking refresh error", e);
            SwingUtilities.invokeLater(() ->
                    setStatus(FBundle.message("st.load.fail") + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private String formatChange(String s) {
        if (s == null || s.isEmpty() || "0".equals(s)) return "-";
        if (s.startsWith("+")) return "↑" + s.substring(1);
        if (s.startsWith("-")) return "↓" + s.substring(1);
        return s;
    }


    private void setStatus(String text) { statusLabel.setText(text); }
}
