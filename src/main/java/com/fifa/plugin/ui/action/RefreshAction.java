package com.fifa.plugin.ui.action;

import com.fifa.plugin.core.TournamentManager;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * 刷新按钮 - 手动触发数据刷新
 */
public class RefreshAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        TournamentManager tm = ApplicationManager.getApplication()
                .getService(TournamentManager.class);

        if (tm == null) return;

        tm.refreshAll();

        // 通知用户
        NotificationGroupManager.getInstance()
                .getNotificationGroup("FIFA.Notification")
                .createNotification(
                        "FIFA World Cup 2026",
                        "数据已刷新 ✓",
                        NotificationType.INFORMATION
                )
                .notify(e.getProject());
    }
}
