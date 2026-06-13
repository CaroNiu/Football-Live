package com.fifa.plugin.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Component;
import java.awt.Dimension;

/**
 * 插件设置页面 (File → Settings → Tools → FIFA World Cup 2026)
 * <p>
 * 数据源已切换为 TheSportsDB 公共 API, 用户无需配置 Token,
 * 这里只保留 UI 偏好和关注球队
 */
public class FifaSettingsConfigurable implements Configurable {

    private JCheckBox showLiveScoreCheck;
    private JCheckBox enableNotificationCheck;
    private JBTextField favoriteTeamField;

    @Nls
    @Override
    public String getDisplayName() {
        return "FIFA World Cup 2026";
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
        JLabel source1 = new JBLabel("  TheSportsDB 公共 API · 实时赛程 / 比分 / 积分榜");
        JLabel source2 = new JBLabel("  数据每日自动更新, 离线缓存 5-10 分钟, 无需配置");
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

        // 关注球队
        JPanel favPanel = new JPanel();
        favPanel.setLayout(new BoxLayout(favPanel, BoxLayout.X_AXIS));
        favPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        favPanel.add(new JBLabel("关注球队: "));
        favoriteTeamField = new JBTextField(20);
        favPanel.add(favoriteTeamField);
        favPanel.add(Box.createHorizontalGlue());

        JLabel favHint = new JBLabel(" ");
        favHint.setFont(favHint.getFont().deriveFont(10f));
        favHint.setForeground(UIManager.getColor("Label.disabledForeground"));

        panel.add(favPanel);
        panel.add(favHint);

        return panel;
    }

    @Override
    public boolean isModified() {
        FifaSettingsState.State state = FifaSettingsState.getInstance().getState();
        return showLiveScoreCheck.isSelected() != state.showLiveScoreInStatusBar
                || enableNotificationCheck.isSelected() != state.enableMatchNotification
                || !favoriteTeamField.getText().equals(state.favoriteTeam);
    }

    @Override
    public void apply() {
        FifaSettingsState settings = FifaSettingsState.getInstance();
        settings.setShowLiveScoreInStatusBar(showLiveScoreCheck.isSelected());
        settings.setEnableMatchNotification(enableNotificationCheck.isSelected());
        settings.setFavoriteTeam(favoriteTeamField.getText().trim());
    }

    @Override
    public void reset() {
        FifaSettingsState.State state = FifaSettingsState.getInstance().getState();
        showLiveScoreCheck.setSelected(state.showLiveScoreInStatusBar);
        enableNotificationCheck.setSelected(state.enableMatchNotification);
        favoriteTeamField.setText(state.favoriteTeam);
    }
}
