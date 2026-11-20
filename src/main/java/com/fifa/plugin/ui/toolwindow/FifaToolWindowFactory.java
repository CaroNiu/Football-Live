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
import java.util.Map;
import java.util.WeakHashMap;

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

    /** project → Tab 容器 (供状态栏 widget 点击跳转直播 Tab; WeakHashMap 防泄漏) */
    private static final Map<Project, JTabbedPane> PROJECT_TABS = new WeakHashMap<>();

    /** 直播 Tab 下标 (与 addTab 顺序一致) */
    private static final int LIVE_TAB_INDEX = 1;

    /**
     * 选中直播 Tab (工具窗未初始化时静默忽略 — activate 回调阶段会再试一次)
     */
    public static void showLiveTab(@NotNull Project project) {
        JTabbedPane tabs = PROJECT_TABS.get(project);
        if (tabs != null) {
            tabs.setSelectedIndex(LIVE_TAB_INDEX);
        }
    }

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
        PROJECT_TABS.put(project, tabs);

        // 球队榜点击球队 → 跳赛程 Tab 并按球队过滤 (同窗口内面板互调, 无需 project)
        teamRankingPanel.setTeamClickListener(team -> {
            tabs.setSelectedIndex(0);
            schedulePanel.setTeamFilter(team);
        });

        // 面板轮询跟随工具窗可见性: 隐藏暂停, 显示恢复 (含立即刷新)
        // 2024.3 API: ToolWindowManagerListener 在 wm.ex 包, 事件走 stateChanged 三参重载
        com.intellij.openapi.wm.ex.ToolWindowManagerEx.getInstanceEx(project)
                .addToolWindowManagerListener(new com.intellij.openapi.wm.ex.ToolWindowManagerListener() {
                    @Override
                    public void stateChanged(@NotNull com.intellij.openapi.wm.ToolWindowManager manager,
                                             @NotNull com.intellij.openapi.wm.ToolWindow window,
                                             @NotNull com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType type) {
                        if (!"FIFA".equals(window.getId())) return;
                        boolean activate = type == com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.ActivateToolWindow;
                        boolean hide = type == com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow;
                        if (!activate && !hide) return;
                        if (schedulePanel == null) return; // 首次激活时内容尚未创建
                        com.intellij.openapi.diagnostic.Logger.getInstance(FifaToolWindowFactory.class)
                                .info("toolwindow " + type + " → pollers " + (activate ? "resumed" : "paused"));
                        schedulePanel.setPollingActive(activate);
                        livePanel.setPollingActive(activate);
                        teamRankingPanel.setPollingActive(activate);
                        fifaRankingPanel.setPollingActive(activate);
                        newsPanel.setPollingActive(activate);
                    }
                });

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
