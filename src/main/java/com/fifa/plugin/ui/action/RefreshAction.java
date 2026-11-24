package com.fifa.plugin.ui.action;

import com.fifa.plugin.i18n.FBundle;
import com.fifa.plugin.core.FootballDataManager;
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
        FootballDataManager dm = ApplicationManager.getApplication()
                .getService(FootballDataManager.class);

        if (dm == null) return;

        dm.refreshAll();

        // 通知用户
        NotificationGroupManager.getInstance()
                .getNotificationGroup("FIFA.Notification")
                .createNotification(
                        FBundle.message("act.title"),
                        FBundle.message("notif.refreshed"),
                        NotificationType.INFORMATION
                )
                .notify(e.getProject());
    }
}
