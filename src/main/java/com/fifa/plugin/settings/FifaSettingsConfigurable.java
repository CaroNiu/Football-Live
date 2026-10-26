package com.fifa.plugin.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件设置页面 (File → Settings → Tools → 足球直播 Football Live)
 * <p>
 * 全赛事版: 公共数据源零配置, 这里维护 UI 偏好 / 关注赛事 / 关注球队
 */
public class FifaSettingsConfigurable implements Configurable {

    private JCheckBox showLiveScoreCheck;
    private JCheckBox enableNotificationCheck;
    private JBTextField favoriteTeamField;
    private JBTextArea followedLeaguesField;

    @Nls
    @Override
    public String getDisplayName() {
        return "足球直播 Football Live";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(10));

        // 数据源信息 (只读)
        JPanel dataSourcePanel = new JPanel();
        dataSourcePanel.setLayout(new BoxLayout(dataSourcePanel, BoxLayout.Y_AXIS));
        dataSourcePanel.setBorder(BorderFactory.createTitledBorder("数据源"));
        JLabel source1 = new JBLabel("  百度体育 (tiyu.baidu.com) · 全部足球赛事赛程 / 比分 / 积分榜 / 新闻");
        JLabel source2 = new JBLabel("  公共数据源, 客户端缓存 1-5 分钟, 无需任何配置");
        source1.setFont(source1.getFont().deriveFont(12f));
        source2.setFont(source2.getFont().deriveFont(11f));
        source2.setForeground(UIManager.getColor("Label.disabledForeground"));
        dataSourcePanel.add(source1);
        dataSourcePanel.add(source2);
        dataSourcePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dataSourcePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        panel.add(dataSourcePanel);
        panel.add(Box.createVerticalStrut(15));

        // StatusBar 开关
        showLiveScoreCheck = new JCheckBox("在状态栏显示实时比分");
        showLiveScoreCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(showLiveScoreCheck);
        panel.add(Box.createVerticalStrut(10));

        // 通知开关
        enableNotificationCheck = new JCheckBox("比赛开始时发送通知提醒");
        enableNotificationCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(enableNotificationCheck);
        panel.add(Box.createVerticalStrut(10));

        // 关注赛事 (逗号分隔, 与工具窗口选择条同步)
        JPanel leaguesPanel = new JPanel(new BorderLayout());
        leaguesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        leaguesPanel.add(new JBLabel("关注赛事 (逗号分隔, 如: 英超,西甲,欧冠): "), BorderLayout.NORTH);
        followedLeaguesField = new JBTextArea(3, 30);
        followedLeaguesField.setLineWrap(true);
        leaguesPanel.add(followedLeaguesField, BorderLayout.CENTER);
        JLabel leaguesHint = new JBLabel("  工具窗口顶部的赛事选择条可随时快捷切换");
        leaguesHint.setFont(leaguesHint.getFont().deriveFont(10f));
        leaguesHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        leaguesPanel.add(leaguesHint, BorderLayout.SOUTH);
        panel.add(leaguesPanel);
        panel.add(Box.createVerticalStrut(10));

        // 关注球队
        JPanel favPanel = new JPanel();
        favPanel.setLayout(new BoxLayout(favPanel, BoxLayout.X_AXIS));
        favPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        favPanel.add(new JBLabel("关注球队: "));
        favoriteTeamField = new JBTextField(20);
        favPanel.add(favoriteTeamField);
        favPanel.add(Box.createHorizontalGlue());

        panel.add(favPanel);

        return panel;
    }

    @Override
    public boolean isModified() {
        FifaSettingsState.State state = FifaSettingsState.getInstance().getState();
        return showLiveScoreCheck.isSelected() != state.showLiveScoreInStatusBar
                || enableNotificationCheck.isSelected() != state.enableMatchNotification
                || !favoriteTeamField.getText().equals(state.favoriteTeam)
                || !joinedFollowed().equals(joined(state.followedLeagues));
    }

    @Override
    public void apply() {
        FifaSettingsState settings = FifaSettingsState.getInstance();
        settings.setShowLiveScoreInStatusBar(showLiveScoreCheck.isSelected());
        settings.setEnableMatchNotification(enableNotificationCheck.isSelected());
        settings.setFavoriteTeam(favoriteTeamField.getText().trim());
        List<String> leagues = new ArrayList<>();
        for (String s : followedLeaguesField.getText().split("[,，]")) {
            String t = s.trim();
            if (!t.isEmpty()) leagues.add(t);
        }
        settings.setFollowedLeagues(leagues);
    }

    @Override
    public void reset() {
        FifaSettingsState.State state = FifaSettingsState.getInstance().getState();
        showLiveScoreCheck.setSelected(state.showLiveScoreInStatusBar);
        enableNotificationCheck.setSelected(state.enableMatchNotification);
        favoriteTeamField.setText(state.favoriteTeam);
        followedLeaguesField.setText(joined(state.followedLeagues));
    }

    private String joinedFollowed() {
        return joined(FifaSettingsState.getInstance().getFollowedLeagues());
    }

    private static String joined(java.util.List<String> leagues) {
        return leagues == null ? "" : String.join(",", leagues);
    }
}
