package com.fifa.plugin.ui.component;

import com.fifa.plugin.core.LeagueRegistry;
import com.fifa.plugin.model.League;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;

/**
 * 全局赛事选择条 — ToolWindow 顶部的跨 Tab 过滤器
 * <p>
 * 选项 = [全部赛事] + 关注赛事 + 目录其余赛事;
 * 选中后写入 {@link LeagueRegistry} (持久化 + 通知 6 个 Tab)
 */
public class LeagueFilterBar extends JPanel {

    /** 首项: 不过滤 */
    public static final String ALL = "全部赛事";

    private final JComboBox<String> combo;
    private boolean syncing = false;

    public LeagueFilterBar() {
        super(new FlowLayout(FlowLayout.LEFT, 8, 2));

        add(new JBLabel("赛事:"));
        combo = new JComboBox<>();
        combo.setPrototypeDisplayValue("江苏城市足球联赛"); // 控制宽度, 避免长赛事名撑开
        combo.setPreferredSize(new Dimension(200, 28));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (ALL.equals(value)) setText("⚽ " + ALL);
                return this;
            }
        });
        combo.addActionListener(e -> {
            if (syncing) return;
            Object sel = combo.getSelectedItem();
            if (sel == null) return;
            LeagueRegistry.getInstance().setSelected(ALL.equals(sel) ? "" : sel.toString());
        });
        add(combo);
        add(Box.createHorizontalStrut(8));

        // 目录到达 / 关注变更时重填选项
        LeagueRegistry registry = LeagueRegistry.getInstance();
        registry.addListener(this::reloadItems);
        reloadItems();

        // 选中变化 (来自其他入口) 时同步下拉
        registry.addListener(() -> SwingUtilities.invokeLater(this::syncSelection));
    }

    private void reloadItems() {
        // 目录异步刷新回调可能在 pooled 线程, Swing 操作必须回 EDT
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::reloadItems);
            return;
        }
        LeagueRegistry registry = LeagueRegistry.getInstance();
        syncing = true;
        try {
            combo.removeAllItems();
            combo.addItem(ALL);
            // 关注赛事置顶
            for (String key : registry.getFollowed()) {
                if (!ALL.equals(key)) combo.addItem(key);
            }
            // 目录其余赛事 (跳过关注里已有的)
            for (League l : registry.getCatalog()) {
                if (!ALL.equals(l.getBaiduKey()) && !registry.getFollowed().contains(l.getBaiduKey())) {
                    combo.addItem(l.getBaiduKey());
                }
            }
            syncSelection();
        } finally {
            syncing = false;
        }
    }

    private void syncSelection() {
        String selected = LeagueRegistry.getInstance().getSelected();
        String want = selected == null || selected.isEmpty() ? ALL : selected;
        boolean found = false;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (want.equals(combo.getItemAt(i))) {
                found = true;
                break;
            }
        }
        syncing = true;
        try {
            combo.setSelectedItem(found ? want : ALL);
        } finally {
            syncing = false;
        }
    }
}
