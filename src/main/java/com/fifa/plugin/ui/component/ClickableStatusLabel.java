package com.fifa.plugin.ui.component;

import com.fifa.plugin.i18n.FBundle;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 面板底部状态栏 — 文本以 ⚠ 开头 (拉取失败/无数据) 时可点击, 触发重试
 * <p>
 * 替代各面板裸的 JBLabel, 统一失败重试入口, 不用再找刷新按钮
 */
public class ClickableStatusLabel extends JBLabel {

    private final Runnable retryAction;

    public ClickableStatusLabel(Runnable retryAction) {
        super(" ");
        this.retryAction = retryAction;
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setToolTipText(FBundle.message("click.retry.tooltip"));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String text = getText();
                if (text != null && text.startsWith("⚠") && retryAction != null) {
                    retryAction.run();
                }
            }
        });
    }
}
