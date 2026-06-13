package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.api.BaiduLiveMatchClient;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * 直播详情弹出框
 * <p>
 * 顶部: 比赛基础信息 (阶段 / 状态 / 对阵 + 比分, 比分只展示不强调)
 * 主体: JTabbedPane 二选一
 * <ul>
 *   <li><b>分析</b> - 网友预测 / 智能预测 (主客队情报) / 历史交锋 + 主客队战绩</li>
 *   <li><b>文字直播</b> - 按时间正序的事件流 (进球 / 射门 / 角球 / 黄牌 / 越位 / 换人 / ...)</li>
 * </ul>
 */
public class LiveMatchDialog extends DialogWrapper {

    private final BaiduLiveMatchClient.LiveMatch match;
    private final List<BaiduLiveMatchClient.TextLiveEvent> textLive;

    public LiveMatchDialog(BaiduLiveMatchClient.LiveMatch match,
                           List<BaiduLiveMatchClient.TextLiveEvent> textLive) {
        super(true);  // modal
        this.match = match;
        this.textLive = textLive;
        setTitle("比赛详情 · " + (match != null && match.matchName != null ? match.matchName : "直播"));
        setSize(720, 640);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout());

        // ---- 头部: 比赛信息 ----
        root.add(buildHeaderPanel(), BorderLayout.NORTH);

        // ---- 主体: Tab ----
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("分析", buildAnalysisPanel());
        tabs.addTab("文字直播", buildTextLivePanel());
        root.add(tabs, BorderLayout.CENTER);

        return root;
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(10, 14, 8, 14));
        // 不再设硬编码背景, 跟随 DialogWrapper 主题, 避免暗色主题下白字浅底不可读

        // 阶段 + 状态
        JLabel stage = new JBLabel("【" + (match.matchStage != null ? match.matchStage : "未知阶段") + "】  "
                + (match.matchStatusText != null ? match.matchStatusText : ""));
        stage.setFont(stage.getFont().deriveFont(Font.BOLD, 14f));
        stage.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(stage);
        header.add(Box.createVerticalStrut(6));

        // 对阵 + 比分 (只展示, 沿用默认前景色)
        JPanel matchup = new JPanel();
        matchup.setOpaque(false);
        matchup.setLayout(new BoxLayout(matchup, BoxLayout.X_AXIS));
        matchup.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel leftTeam = new JBLabel(match.leftName != null ? match.leftName : "主队");
        leftTeam.setFont(leftTeam.getFont().deriveFont(Font.PLAIN, 15f));
        JLabel scoreSep = new JBLabel("  ");
        JLabel score = new JBLabel(match.leftGoal + " - " + match.rightGoal);
        score.setFont(score.getFont().deriveFont(Font.PLAIN, 15f));
        JLabel rightTeam = new JBLabel("  " + (match.rightName != null ? match.rightName : "客队"));
        rightTeam.setFont(rightTeam.getFont().deriveFont(Font.PLAIN, 15f));

        matchup.add(leftTeam);
        matchup.add(scoreSep);
        matchup.add(score);
        matchup.add(rightTeam);
        matchup.add(Box.createHorizontalGlue());
        header.add(matchup);

        // 时间 + 日期
        header.add(Box.createVerticalStrut(2));
        String timeStr = (match.time != null ? match.time : "") + " " + (match.date != null ? match.date : "");
        JLabel timeLbl = new JBLabel(timeStr);
        timeLbl.setFont(timeLbl.getFont().deriveFont(11f));
        timeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(timeLbl);

        // 头部底部分隔线, 强化与下方 Tab 的视觉分区
        JSeparator sep = new JSeparator();
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        header.add(Box.createVerticalStrut(4));
        header.add(sep);

        return header;
    }

    /**
     * 分析 tab - 网友预测 / 智能预测 / 历史交锋
     */
    private JComponent buildAnalysisPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(UIManager.getColor("Panel.background"));
        root.setBorder(new EmptyBorder(8, 12, 8, 12));

        if (match == null) {
            root.add(centerLabel("⚠ 拉取失败"));
            root.add(Box.createVerticalGlue());
        } else {
            // 网友预测
            JComponent userPred = buildUserPredictionSection();
            if (userPred != null) {
                root.add(userPred);
                root.add(Box.createVerticalStrut(8));
            }
            // 智能预测
            JComponent intel = buildIntelligenceSection();
            if (intel != null) {
                root.add(intel);
                root.add(Box.createVerticalStrut(8));
            }
            // 历史交锋 / 主队战绩 / 客队战绩
            JComponent hist = buildHistorySection();
            if (hist != null) {
                root.add(hist);
            }
            // 若全空, 给占位
            if (userPred == null && intel == null && hist == null) {
                root.add(centerLabel("⚠ 该比赛尚无可用分析数据 (可能为未来比赛, 百度未填充)"));
            }
            root.add(Box.createVerticalGlue());
        }

        JScrollPane scroll = new JBScrollPane(root);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(700, 460));
        return scroll;
    }

    /**
     * 网友预测: 主胜 / 平 / 客胜 % + 各队胜率
     */
    private @Nullable JComponent buildUserPredictionSection() {
        boolean hasPct = match.victoryPct != null && !match.victoryPct.isEmpty();
        boolean hasPred = !match.predictions.isEmpty();
        if (!hasPct && !hasPred) return null;

        JPanel sec = newSection("📊 网友预测");

        if (hasPct) {
            JPanel pct = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            pct.setOpaque(false);
            pct.setAlignmentX(Component.LEFT_ALIGNMENT);
            pct.add(makePctLabel("主胜", match.victoryPct, new Color(0xE6, 0x49, 0x49)));
            pct.add(makePctLabel("平", match.drawPct, new Color(0xE0, 0xA8, 0x1E)));
            pct.add(makePctLabel("客胜", match.lostPct, new Color(0x52, 0x8B, 0xFF)));
            sec.add(pct);
            sec.add(Box.createVerticalStrut(6));
        }
        if (hasPred) {
            for (BaiduLiveMatchClient.Prediction p : match.predictions) {
                String arrow = "↑".equals(p.winner) ? "↑"
                        : "↓".equals(p.winner) ? "↓" : "·";
                sec.add(bulletLine(arrow + "  " + (p.team != null ? p.team : "?")
                        + "  —  " + (p.winrate != null ? p.winrate : "")));
            }
        }
        return sec;
    }

    private JLabel makePctLabel(String name, String pct, Color color) {
        JLabel l = new JBLabel("<html><span style='color:#888'>" + esc(name) + "</span> "
                + "<b style='color:rgb(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue()
                + ");font-size:14px'>" + esc(pct != null ? pct : "") + "</b></html>");
        return l;
    }

    /**
     * 智能预测: 多个板块 (有利情报 / 不利情报), 每个板块有主客两队内容
     */
    private @Nullable JComponent buildIntelligenceSection() {
        if (match.intelligences == null || match.intelligences.isEmpty()) return null;
        // 过滤掉主客队都没内容的空板块
        boolean hasAny = match.intelligences.stream().anyMatch(i ->
                !i.homeContents.isEmpty() || !i.awayContents.isEmpty()
                        || (i.homeTeamName != null && !i.homeTeamName.isEmpty())
                        || (i.awayTeamName != null && !i.awayTeamName.isEmpty()));
        if (!hasAny) return null;

        JPanel sec = newSection("🧠 智能预测");
        for (BaiduLiveMatchClient.Intelligence ig : match.intelligences) {
            // 板块子标题
            JLabel sub = new JBLabel((ig.title != null && !ig.title.isEmpty() ? ig.title : "情报"));
            sub.setFont(sub.getFont().deriveFont(Font.BOLD, 12f));
            sub.setForeground(new Color(0x33, 0x66, 0xCC));
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            sec.add(sub);
            sec.add(Box.createVerticalStrut(2));

            // 主队情报
            if (ig.homeTeamName != null && !ig.homeTeamName.isEmpty()) {
                sec.add(teamBlock(ig.homeTeamName, ig.homeContents, true));
            }
            // 客队情报
            if (ig.awayTeamName != null && !ig.awayTeamName.isEmpty()) {
                sec.add(teamBlock(ig.awayTeamName, ig.awayContents, false));
            }
            sec.add(Box.createVerticalStrut(6));
        }
        return sec;
    }

    private JComponent teamBlock(String teamName, List<String> contents, boolean isHome) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(new EmptyBorder(0, 8, 0, 0));

        JLabel name = new JBLabel((isHome ? "🏠 " : "✈️ ") + esc(teamName));
        name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(name);

        if (contents == null || contents.isEmpty()) {
            JLabel none = new JBLabel("  — 暂无数据");
            none.setForeground(UIManager.getColor("Label.disabledForeground"));
            none.setFont(none.getFont().deriveFont(11f));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(none);
        } else {
            for (String c : contents) {
                p.add(bulletLine(c));
            }
        }
        p.add(Box.createVerticalStrut(2));
        return p;
    }

    /**
     * 历史交锋 / 主队战绩 / 客队战绩: 每段一个 record, 含战绩摘要 + 近期 6 场
     */
    private @Nullable JComponent buildHistorySection() {
        if (match.records == null || match.records.isEmpty()) return null;
        boolean hasAny = match.records.stream().anyMatch(r ->
                (r.title != null && !r.title.isEmpty())
                        || !r.matches.isEmpty()
                        || (r.result != null && !r.result.isEmpty()));
        if (!hasAny) return null;

        JPanel sec = newSection("📜 历史交锋 / 主客队战绩");
        for (BaiduLiveMatchClient.Record rec : match.records) {
            // 子标题
            String headTitle = (rec.title != null && !rec.title.isEmpty()) ? rec.title
                    : (rec.teamName != null && !rec.teamName.isEmpty()) ? rec.teamName + " 战绩" : "战绩";
            JLabel sub = new JBLabel(headTitle);
            sub.setFont(sub.getFont().deriveFont(Font.BOLD, 12f));
            sub.setForeground(new Color(0x33, 0x66, 0xCC));
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            sec.add(sub);
            sec.add(Box.createVerticalStrut(2));

            // 战绩摘要 + 胜率
            String summary = joinNonEmpty("  ", rec.result, rec.probability);
            if (!summary.isEmpty()) {
                JLabel sum = new JBLabel(summary);
                sum.setAlignmentX(Component.LEFT_ALIGNMENT);
                sec.add(sum);
                sec.add(Box.createVerticalStrut(4));
            }

            // 近期比赛列表
            if (!rec.matches.isEmpty()) {
                for (BaiduLiveMatchClient.HistoryMatch m : rec.matches) {
                    String scoreStr = (m.leftScore != null ? m.leftScore : "?")
                            + " - " + (m.rightScore != null ? m.rightScore : "?");
                    String line = "  • " + (m.date != null ? m.date : "")
                            + "  " + (m.leftName != null ? m.leftName : "?")
                            + " <b>" + scoreStr + "</b> "
                            + (m.rightName != null ? m.rightName : "?");
                    JLabel l = new JBLabel("<html><span style='font-size:11px'>" + line + "</span></html>");
                    l.setAlignmentX(Component.LEFT_ALIGNMENT);
                    if (Boolean.TRUE.equals(m.leftWin)) {
                        l.setForeground(new Color(0xC0, 0x39, 0x2B));
                    } else if (Boolean.FALSE.equals(m.leftWin)) {
                        l.setForeground(new Color(0x66, 0x66, 0x66));
                    }
                    sec.add(l);
                }
            } else {
                JLabel none = new JBLabel("  — 暂无近期比赛数据");
                none.setForeground(UIManager.getColor("Label.disabledForeground"));
                none.setFont(none.getFont().deriveFont(11f));
                none.setAlignmentX(Component.LEFT_ALIGNMENT);
                sec.add(none);
            }
            sec.add(Box.createVerticalStrut(6));
        }
        return sec;
    }

    /**
     * 文字直播 tab - 按时间正序的事件流
     */
    private JComponent buildTextLivePanel() {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(UIManager.getColor("Panel.background"));
        list.setBorder(new EmptyBorder(8, 12, 8, 12));

        if (textLive == null || textLive.isEmpty()) {
            JLabel empty = new JBLabel("⚠ 暂无文字直播数据 (比赛未开始 / 已结束 / 接口未返回)");
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setBorder(new EmptyBorder(40, 0, 40, 0));
            empty.setForeground(UIManager.getColor("Label.disabledForeground"));
            list.add(empty);
            list.add(Box.createVerticalGlue());
        } else {
            for (int i = textLive.size() - 1; i >= 0; i--) {
                list.add(buildEventCard(textLive.get(i)));
                list.add(Box.createVerticalStrut(6));
            }
            list.add(Box.createVerticalGlue());
        }

        JScrollPane scroll = new JBScrollPane(list);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(700, 460));
        return scroll;
    }

    private JComponent buildEventCard(BaiduLiveMatchClient.TextLiveEvent e) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.X_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.separator"), 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        card.setBackground(UIManager.getColor("Panel.background"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JLabel time = new JBLabel(e.time != null && !e.time.isEmpty() ? e.time : "-");
        time.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        time.setForeground(accentFor(e.iconType));
        time.setPreferredSize(new Dimension(58, 20));
        time.setMinimumSize(new Dimension(58, 20));
        time.setMaximumSize(new Dimension(58, 20));
        card.add(time);
        card.add(Box.createHorizontalStrut(10));

        JLabel type = new JBLabel(" " + (e.iconType != null && !e.iconType.isEmpty() ? e.iconType : "事件") + " ");
        type.setOpaque(true);
        type.setBackground(accentFor(e.iconType));
        type.setForeground(Color.WHITE);
        type.setFont(type.getFont().deriveFont(Font.BOLD, 11f));
        type.setBorder(new EmptyBorder(2, 6, 2, 6));
        type.setAlignmentY(Component.CENTER_ALIGNMENT);
        card.add(type);
        card.add(Box.createHorizontalStrut(10));

        JLabel content = new JBLabel("<html><span style='font-size:12px'>"
                + (e.teamName != null && !e.teamName.isEmpty() ? "<b>" + esc(e.teamName) + "</b> · " : "")
                + esc(e.content != null ? e.content : "")
                + "</span></html>");
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(content);
        card.add(Box.createHorizontalGlue());

        return card;
    }

    private Color accentFor(String iconType) {
        if (iconType == null) return new Color(0x60, 0x66, 0x6F);
        if (iconType.contains("进球")) return new Color(0xE6, 0x49, 0x49);
        if (iconType.contains("红牌")) return new Color(0xC0, 0x1B, 0x1B);
        if (iconType.contains("黄牌")) return new Color(0xE0, 0xA8, 0x1E);
        if (iconType.contains("换人")) return new Color(0x52, 0x8B, 0xFF);
        if (iconType.contains("点球")) return new Color(0x8E, 0x44, 0xAD);
        if (iconType.contains("VAR")) return new Color(0x33, 0x33, 0x33);
        return new Color(0x60, 0x66, 0x6F);
    }

    // ============================================================
    //  UI 工具
    // ============================================================

    private JPanel newSection(String title) {
        JPanel sec = new JPanel();
        sec.setLayout(new BoxLayout(sec, BoxLayout.Y_AXIS));
        sec.setOpaque(true);
        sec.setBackground(UIManager.getColor("Panel.background"));
        sec.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.separator"), 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        sec.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel h = new JBLabel(title);
        h.setFont(h.getFont().deriveFont(Font.BOLD, 13f));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        sec.add(h);
        sec.add(Box.createVerticalStrut(6));
        return sec;
    }

    private JComponent bulletLine(String text) {
        JLabel l = new JBLabel("  • " + esc(text));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 2, 0));
        return l;
    }

    private JComponent centerLabel(String text) {
        JLabel l = new JBLabel(text);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setBorder(new EmptyBorder(40, 0, 40, 0));
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        return l;
    }

    private static String joinNonEmpty(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (!first) sb.append(sep);
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
