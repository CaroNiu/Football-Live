package com.fifa.plugin.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * 插件持久化设置 (储存在 IDE 配置目录)
 * <p>
 * 数据源已切换为 TheSportsDB 公共 API (key=3), 用户无需再配置 API Token
 */
@State(
        name = "FifaWorldCupSettings",
        storages = @Storage("fifa-worldcup.xml")
)
public class FifaSettingsState implements PersistentStateComponent<FifaSettingsState.State> {

    public static class State {
        public boolean showLiveScoreInStatusBar = true;
        public boolean enableMatchNotification = true;
        public int liveScorePollIntervalSec = 30;
        public String favoriteTeam = "";       // 关注球队名
        /** 关注的赛事 (百度赛事名, 如 "英超"); 空则插件用内置默认 */
        public java.util.List<String> followedLeagues = new java.util.ArrayList<>();
        /** 上次选中的赛事过滤器; "" = 全部赛事 */
        public String lastSelectedLeague = "";
    }

    private State myState = new State();

    public static FifaSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(FifaSettingsState.class);
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.myState = state;
    }

    // ---- 便捷访问 ----

    public boolean isShowLiveScoreInStatusBar() {
        return myState.showLiveScoreInStatusBar;
    }

    public void setShowLiveScoreInStatusBar(boolean show) {
        myState.showLiveScoreInStatusBar = show;
    }

    public boolean isEnableMatchNotification() {
        return myState.enableMatchNotification;
    }

    public void setEnableMatchNotification(boolean enable) {
        myState.enableMatchNotification = enable;
    }

    public int getLiveScorePollIntervalSec() {
        return myState.liveScorePollIntervalSec;
    }

    public String getFavoriteTeam() {
        return myState.favoriteTeam;
    }

    public void setFavoriteTeam(String team) {
        myState.favoriteTeam = team;
    }

    public java.util.List<String> getFollowedLeagues() {
        return myState.followedLeagues == null
                ? java.util.Collections.emptyList() : myState.followedLeagues;
    }

    public void setFollowedLeagues(java.util.List<String> leagues) {
        myState.followedLeagues = new java.util.ArrayList<>(leagues);
    }

    public String getLastSelectedLeague() {
        return myState.lastSelectedLeague == null ? "" : myState.lastSelectedLeague;
    }

    public void setLastSelectedLeague(String league) {
        myState.lastSelectedLeague = league == null ? "" : league;
    }
}
