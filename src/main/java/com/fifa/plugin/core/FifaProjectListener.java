package com.fifa.plugin.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * 项目打开/关闭监听 - 管理 TournamentManager 生命周期
 */
@Service
public final class FifaProjectListener implements ProjectManagerListener {

    @Override
    @SuppressWarnings("removal")
    public void projectOpened(@NotNull Project project) {
        // IDE 启动时开启数据轮询
        TournamentManager tm = ApplicationManager.getApplication()
                .getService(TournamentManager.class);
        if (tm != null) {
            tm.startPolling();
        }
    }

    @Override
    public void projectClosed(@NotNull Project project) {
        // 最后一个项目关闭时停止轮询
        if (ProjectManager.getInstance().getOpenProjects().length > 0) {
            return;
        }
        TournamentManager tm = ApplicationManager.getApplication()
                .getService(TournamentManager.class);
        if (tm != null) {
            tm.stopPolling();
        }
    }
}
