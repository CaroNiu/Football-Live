package com.fifa.plugin.settings;

import com.fifa.plugin.i18n.FBundle;
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
    private JBTextArea favoriteTeamsField;
    private JBTextArea followedLeaguesField;
    private JComboBox<String> languageCombo;

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

        // 界面语言 (手动覆盖 IDE 区域; 改后重启生效)
        JPanel langPanel = new JPanel();
        langPanel.setLayout(new BoxLayout(langPanel, BoxLayout.X_AXIS));
        langPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        langPanel.add(new JBLabel(FBundle.message("set.lang")));
        languageCombo = new JComboBox<>(new String[]{"", "en", "zh"});
        languageCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String code = (String) value;
                setText(code == null || code.isEmpty() ? FBundle.message("set.lang.auto")
                        : "en".equals(code) ? FBundle.message("set.lang.en")
                        : FBundle.message("set.lang.zh"));
                return this;
            }
        });
        languageCombo.setPreferredSize(new Dimension(220, 28));
        langPanel.add(Box.createHorizontalStrut(8));
        langPanel.add(languageCombo);
        panel.add(langPanel);
        JLabel langHint = new JBLabel(FBundle.message("set.lang.hint"));
        langHint.setFont(langHint.getFont().deriveFont(10f));
        langHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        langHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(langHint);
        panel.add(Box.createVerticalStrut(15));

        // 数据源信息 (只读)
        JPanel dataSourcePanel = new JPanel();
        dataSourcePanel.setLayout(new BoxLayout(dataSourcePanel, BoxLayout.Y_AXIS));
        dataSourcePanel.setBorder(BorderFactory.createTitledBorder(FBundle.message("set.ds.title")));
        JLabel source1 = new JBLabel(FBundle.message("set.ds.line1"));
        JLabel source2 = new JBLabel(FBundle.message("set.ds.line2"));
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
        showLiveScoreCheck = new JCheckBox(FBundle.message("set.statusbar"));
        showLiveScoreCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(showLiveScoreCheck);
        panel.add(Box.createVerticalStrut(10));

        // 通知开关
        enableNotificationCheck = new JCheckBox(FBundle.message("set.notify"));
        enableNotificationCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(enableNotificationCheck);
        panel.add(Box.createVerticalStrut(10));

        // 关注赛事 (逗号分隔, 与工具窗口选择条同步)
        JPanel leaguesPanel = new JPanel(new BorderLayout());
        leaguesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        leaguesPanel.add(new JBLabel(FBundle.message("set.leagues")), BorderLayout.NORTH);
        followedLeaguesField = new JBTextArea(3, 30);
        followedLeaguesField.setLineWrap(true);
        leaguesPanel.add(followedLeaguesField, BorderLayout.CENTER);
        JLabel leaguesHint = new JBLabel(FBundle.message("set.leagues.hint"));
        leaguesHint.setFont(leaguesHint.getFont().deriveFont(10f));
        leaguesHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        leaguesPanel.add(leaguesHint, BorderLayout.SOUTH);
        panel.add(leaguesPanel);
        panel.add(Box.createVerticalStrut(10));

        // 关注球队 (逗号分隔)
        JPanel favPanel = new JPanel(new BorderLayout());
        favPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        favPanel.add(new JBLabel(FBundle.message("set.teams")), BorderLayout.NORTH);
        favoriteTeamsField = new JBTextArea(3, 30);
        favoriteTeamsField.setLineWrap(true);
        favPanel.add(favoriteTeamsField, BorderLayout.CENTER);
        JLabel favHint = new JBLabel(FBundle.message("set.teams.hint"));
        favHint.setFont(favHint.getFont().deriveFont(10f));
        favHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        favPanel.add(favHint, BorderLayout.SOUTH);
        panel.add(favPanel);

        return panel;
    }

    @Override
    public boolean isModified() {
        FifaSettingsState.State state = FifaSettingsState.getInstance().getState();
        return showLiveScoreCheck.isSelected() != state.showLiveScoreInStatusBar
                || enableNotificationCheck.isSelected() != state.enableMatchNotification
                || !String.valueOf(languageCombo.getSelectedItem()).equals(state.uiLanguage)
                || !joined(splitCsv(favoriteTeamsField.getText())).equals(joined(state.favoriteTeams))
                || !joinedFollowed().equals(joined(state.followedLeagues));
    }

    @Override
    public void apply() {
        FifaSettingsState settings = FifaSettingsState.getInstance();
        settings.setShowLiveScoreInStatusBar(showLiveScoreCheck.isSelected());
        settings.setEnableMatchNotification(enableNotificationCheck.isSelected());
        settings.setUiLanguage((String) languageCombo.getSelectedItem());
        settings.setFavoriteTeams(splitCsv(favoriteTeamsField.getText()));
        settings.setFollowedLeagues(splitCsv(followedLeaguesField.getText()));
    }

    @Override
    public void reset() {
        FifaSettingsState.State state = FifaSettingsState.getInstance().getState();
        showLiveScoreCheck.setSelected(state.showLiveScoreInStatusBar);
        enableNotificationCheck.setSelected(state.enableMatchNotification);
        languageCombo.setSelectedItem(FifaSettingsState.getInstance().getUiLanguage());
        favoriteTeamsField.setText(joined(FifaSettingsState.getInstance().getFavoriteTeams()));
        followedLeaguesField.setText(joined(state.followedLeagues));
    }

    private static List<String> splitCsv(String text) {
        List<String> out = new ArrayList<>();
        for (String s : text.split("[,，]")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private String joinedFollowed() {
        return joined(FifaSettingsState.getInstance().getFollowedLeagues());
    }

    private static String joined(java.util.List<String> leagues) {
        return leagues == null ? "" : String.join(",", leagues);
    }
}
