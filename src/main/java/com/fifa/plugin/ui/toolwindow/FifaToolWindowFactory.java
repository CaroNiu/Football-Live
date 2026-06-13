package com.fifa.plugin.ui.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * FIFA 工具窗口 - 单页面 5 Tab
 * <p>
 * 1. 赛程   - 今日比赛日程 (按日期切换)
 * 2. 排名   - 12 小组积分榜 (按组切换)
 * 3. 球队榜 - 全部 47 队跨组排名
 * 4. FIFA排名 - FIFA 官方排名
 * 5. 新闻   - 百度体育实时新闻
 */
public class FifaToolWindowFactory implements ToolWindowFactory {

    private SchedulePanel schedulePanel;
    private StandingPanel standingsPanel;
    private TeamRankingPanel teamRankingPanel;
    private FifaRankingPanel fifaRankingPanel;
    private NewsPanel newsPanel;
    private LivePanel livePanel;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel root = new JPanel(new BorderLayout());

        // ---- 顶部标题栏 ----
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));

        JLabel title = new JLabel("⚽ FIFA World Cup 2026");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        header.add(title, BorderLayout.WEST);

        JButton refreshButton = new JButton("刷新", AllIcons.Actions.Refresh);
        header.add(refreshButton, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // ---- 中部五 Tab ----
        schedulePanel = new SchedulePanel();
        standingsPanel = new StandingPanel();
        teamRankingPanel = new TeamRankingPanel();
        fifaRankingPanel = new FifaRankingPanel();
        newsPanel = new NewsPanel();
        livePanel = new LivePanel();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("赛程", schedulePanel);
        tabs.addTab("直播", livePanel);
        tabs.addTab("排名", standingsPanel);
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
                case 2 -> standingsPanel.refresh();
                case 3 -> teamRankingPanel.refresh();
                case 4 -> fifaRankingPanel.refresh();
                case 5 -> newsPanel.refresh();
            }
        });

        // (底部数据来源条已移除)

        Content content = ContentFactory.getInstance().createContent(root, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
