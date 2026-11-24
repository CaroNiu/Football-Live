package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.i18n.FBundle;
import com.fifa.plugin.api.FootballDataClient;
import com.fifa.plugin.core.LeagueRegistry;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.fifa.plugin.ui.component.ClickableStatusLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 新闻面板 - 数据源: 百度体育 /al/api/realtime?pn=10&word=世界杯 JSON API
 */
public class NewsPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(NewsPanel.class);

    private final JPanel listPanel;
    private final JBLabel statusLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "FIFA-News-Poller");
                t.setDaemon(true);
                return t;
            }
    );

    public NewsPanel() {
        setLayout(new BorderLayout());

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(UIManager.getColor("Panel.background"));
        add(new JBScrollPane(listPanel), BorderLayout.CENTER);

        statusLabel = new ClickableStatusLabel(this::refresh);
        add(statusLabel, BorderLayout.SOUTH);

        // 跟随全局赛事切换 (新闻关键词跟随)
        LeagueRegistry.getInstance().addListener(() -> SwingUtilities.invokeLater(this::refresh));

        refresh();
        scheduler.scheduleWithFixedDelay(() -> { if (visibilityGate.getAsBoolean()) refresh(); }, 300, 300, TimeUnit.SECONDS);
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
        // 网络 IO 在 pooled 线程 (构造器/监听器可能在 EDT 调用进来)
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            FootballDataClient client = ApplicationManager.getApplication()
                    .getService(FootballDataClient.class);
            if (client == null) return;

            String league = LeagueRegistry.getInstance().getSelected();
            try {
                List<FootballDataClient.NewsItem> news = client.fetchBaiduNews(league, 20);
                SwingUtilities.invokeLater(() -> {
                    listPanel.removeAll();
                    if (news.isEmpty()) {
                        listPanel.add(centerLabel(FBundle.message("news.none")));
                    } else {
                        for (FootballDataClient.NewsItem n : news) {
                            listPanel.add(buildCard(n));
                            listPanel.add(Box.createVerticalStrut(8));
                        }
                    }
                    listPanel.revalidate();
                    listPanel.repaint();
                    setStatus("✓ " + FBundle.message("news.loaded", news.size()) + " " + new java.util.Date());
                });
            } catch (Exception e) {
                LOG.warn("News refresh error", e);
                SwingUtilities.invokeLater(() -> {
                    listPanel.removeAll();
                    listPanel.add(centerLabel(FBundle.message("st.load.fail") + e.getMessage()));
                    listPanel.revalidate();
                    listPanel.repaint();
                });
            }
        });
    }

    @NotNull
    private JComponent centerLabel(String text) {
        JLabel l = new JLabel(text);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        return l;
    }

    /**
     * 一条新闻卡片: 标题 + 来源/时间, 点击打开 URL
     */
    private JComponent buildCard(FootballDataClient.NewsItem n) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.separator"), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        card.setBackground(UIManager.getColor("Panel.background"));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        JLabel title = new JLabel("<html><b style='font-size:13px'>" + esc(n.title) + "</b></html>");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);

        if (n.imageUrl != null && !n.imageUrl.isEmpty()) {
            try {
                // JDK 20+ 已废弃 URL(String), 改用 URI.create().toURL()
                java.net.URL url = java.net.URI.create(n.imageUrl).toURL();
                ImageIcon icon = new ImageIcon(url);
                Image scaled = icon.getImage().getScaledInstance(400, -1, Image.SCALE_SMOOTH);
                JLabel img = new JLabel(new ImageIcon(scaled));
                img.setAlignmentX(Component.LEFT_ALIGNMENT);
                img.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
                card.add(img);
            } catch (Exception ignored) {}
        }

        JLabel meta = new JLabel("<html><small style='color:#888'>"
                + esc(n.source != null ? n.source : "")
                + (n.publishedAt != null && !n.publishedAt.isEmpty() ? "  ·  " + esc(n.publishedAt) : "")
                + "</small></html>");
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(meta);

        if (n.url != null && !n.url.isEmpty()) {
            final String url = n.url;
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    BrowserUtil.browse(url);
                }
            });
        }

        return card;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }


    private void setStatus(String text) { statusLabel.setText(text); }
}
