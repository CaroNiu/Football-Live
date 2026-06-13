package com.fifa.plugin.ui.statusbar;

import com.fifa.plugin.core.TournamentManager;
import com.fifa.plugin.model.Match;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;

/**
 * StatusBar 实时比分 Widget
 * <p>
 * 格式: ⚽ ARG 2:1 FRA (67')  或  ⚽ No live matches
 */
public class LiveScoreWidgetFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull String getId() {
        return "FIFA.LiveScoreWidget";
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
        return "FIFA Live Score";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new LiveScoreWidget();
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
    }

    /**
     * 实际的 Widget 实现
     * <p>
     * 直接实现 {@link StatusBarWidget} + {@link StatusBarWidget.TextPresentation},
     * 不继承 EditorBasedWidget(其构造器已 scheduled-for-removal),
     * 也不再使用 MultipleTextValuesPresentation(其 getMaxValue() 已 deprecated)
     */
    private static final class LiveScoreWidget implements StatusBarWidget, StatusBarWidget.TextPresentation {

        private String currentText = "⚽";
        private @Nullable StatusBar myStatusBar;

        @Override
        public @NotNull String ID() {
            return "FIFA.LiveScoreWidget";
        }

        @Override
        public @Nullable WidgetPresentation getPresentation() {
            return this;
        }

        @Override
        public void install(@NotNull StatusBar statusBar) {
            this.myStatusBar = statusBar;
            // 启动轮询
            TournamentManager tm = ApplicationManager.getApplication()
                    .getService(TournamentManager.class);
            if (tm != null) {
                tm.startPolling();
            }
        }

        @Override
        public void dispose() {
            this.myStatusBar = null;
        }

        // ---- TextPresentation ----

        @Override
        public @NotNull String getText() {
            return currentText;
        }

        @Override
        public float getAlignment() {
            return 1.0f;
        }

        @Nullable
        @Override
        public String getTooltipText() {
            return "FIFA World Cup 2026 Live Score - Click to refresh";
        }

        @Nullable
        @Override
        public Consumer<MouseEvent> getClickConsumer() {
            return event -> {
                // 点击时刷新数据
                TournamentManager tm = ApplicationManager.getApplication()
                        .getService(TournamentManager.class);
                if (tm != null) {
                    tm.refreshAll();
                    update();
                }
            };
        }

        // ---- 内部 ----

        /**
         * 由点击处理器调用: 重新计算文本并通知 StatusBar 重绘
         */
        private void update() {
            updateText();
            if (myStatusBar != null) {
                myStatusBar.updateWidget(ID());
            }
        }

        /**
         * 更新显示文本
         */
        private void updateText() {
            TournamentManager tm = ApplicationManager.getApplication()
                    .getService(TournamentManager.class);
            if (tm == null) return;

            List<Match> liveMatches = tm.getLiveMatches();
            if (liveMatches.isEmpty()) {
                currentText = "⚽ No live matches";
            } else if (liveMatches.size() == 1) {
                Match m = liveMatches.get(0);
                currentText = formatSingleMatch(m);
            } else {
                Match m = liveMatches.get(0);
                currentText = formatSingleMatch(m) + " ...";
            }
        }

        private String formatSingleMatch(Match m) {
            if (m.getScore() != null && m.getScore().getFullTime() != null) {
                Match.ScoreDetail ft = m.getScore().getFullTime();
                String minute = m.getMinute() != null ? " (" + m.getMinute() + "')" : "";
                return "⚽ " + m.getHomeTeam().getTla() + " " + ft.getHome()
                        + ":" + ft.getAway() + " " + m.getAwayTeam().getTla() + minute;
            }
            return "⚽ " + m.getHomeTeam().getTla() + " vs " + m.getAwayTeam().getTla();
        }
    }
}
