package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.core.LeagueRegistry;
import com.fifa.plugin.ui.component.LeagueFilterBar;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * 足球直播工具窗口 - 单页面 5 Tab (全赛事版)
 * <p>
 * 顶部全局赛事选择条对所有 Tab 生效:
 * 1. 赛程    - 全部足球赛事近 3 天赛程 (按日期切换, 跨联赛)
 * 2. 直播    - 直播 / 即将开赛 / 已结束比赛 + 详情弹窗
 * 3. 球队榜  - 积分榜 flatten 跨组/跨榜排序 (取代原独立排名 Tab)
 * 4. FIFA排名 - FIFA 官方排名
 * 5. 新闻    - 百度体育实时新闻 (关键词跟随所选赛事)
 */
public class FifaToolWindowFactory implements ToolWindowFactory {

    private SchedulePanel schedulePanel;
    private TeamRankingPanel teamRankingPanel;
    private FifaRankingPanel fifaRankingPanel;
    private NewsPanel newsPanel;
    private LivePanel livePanel;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel root = new JPanel(new BorderLayout());

        // ---- 顶部: 标题栏 + 全局赛事选择条 (纵向组合) ----
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(6, 12, 2, 8));

        JLabel title = new JLabel("⚽ 足球直播");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        header.add(title, BorderLayout.WEST);

        JButton refreshButton = new JButton("刷新", AllIcons.Actions.Refresh);
        header.add(refreshButton, BorderLayout.EAST);

        LeagueFilterBar filterBar = new LeagueFilterBar();
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));

        JPanel north = new JPanel(new BorderLayout());
        north.add(header, BorderLayout.NORTH);
        north.add(filterBar, BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);

        // ---- 中部五 Tab ----
        schedulePanel = new SchedulePanel();
        teamRankingPanel = new TeamRankingPanel();
        fifaRankingPanel = new FifaRankingPanel();
        newsPanel = new NewsPanel();
        livePanel = new LivePanel();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("赛程", schedulePanel);
        tabs.addTab("直播", livePanel);
        tabs.addTab("球队榜", teamRankingPanel);
        tabs.addTab("FIFA排名", fifaRankingPanel);
        tabs.addTab("新闻", newsPanel);
        root.add(tabs, BorderLayout.CENTER);

        // 单按钮触发当前可见 Tab 刷新
        refreshButton.addActionListener(e -> {
            int idx = tabs.getSelectedIndex();
            switch (idx) {
                case 0 -> schedulePanel.refresh();
                case 1 -> livePanel.refresh();
                case 2 -> teamRankingPanel.refresh();
                case 3 -> fifaRankingPanel.refresh();
                case 4 -> newsPanel.refresh();
            }
        });

        // 切换 Tab 时立即按当前选择刷新一次 (各面板另有周期轮询)
        tabs.addChangeListener(e -> refreshButton.doClick());

        Content content = ContentFactory.getInstance().createContent(root, "", false);
        toolWindow.getContentManager().addContent(content);

        // Stripe 显示名: id 仍为 FIFA (升级兼容), 展示名改为全赛事定位
        try {
            toolWindow.setStripeTitle("足球直播");
        } catch (Exception ignored) {
        }
    }
}
