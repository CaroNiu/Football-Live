package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.api.FootballDataClient;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;

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

        statusLabel = new JBLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        refresh();
        scheduler.scheduleWithFixedDelay(this::refresh, 300, 300, TimeUnit.SECONDS);
    }

    public void refresh() {
        FootballDataClient client = ApplicationManager.getApplication()
                .getService(FootballDataClient.class);
        if (client == null) return;

        try {
            List<FootballDataClient.NewsItem> news = client.fetchBaiduNews(20);
            SwingUtilities.invokeLater(() -> {
                listPanel.removeAll();
                if (news.isEmpty()) {
                    listPanel.add(centerLabel("暂无新闻数据"));
                } else {
                    for (FootballDataClient.NewsItem n : news) {
                        listPanel.add(buildCard(n));
                        listPanel.add(Box.createVerticalStrut(8));
                    }
                }
                listPanel.revalidate();
                listPanel.repaint();
                setStatus("✓ 已加载 " + news.size() + " 条新闻 · " + new java.util.Date());
            });
        } catch (Exception e) {
            LOG.warn("News refresh error", e);
            SwingUtilities.invokeLater(() -> {
                listPanel.removeAll();
                listPanel.add(centerLabel("⚠ 拉取失败: " + e.getMessage()));
                listPanel.revalidate();
                listPanel.repaint();
            });
        }
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

    @org.jetbrains.annotations.NotNull
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    private @interface NotNull {}

    private void setStatus(String text) { statusLabel.setText(text); }
}
