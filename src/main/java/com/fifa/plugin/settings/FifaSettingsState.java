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
        public String favoriteTeam = "";       // (旧) 关注球队名 — 保留兼容已有配置, 迁移到 favoriteTeams
        /** 关注球队列表 (如 皇马,巴萨); 通知优先级 / 状态栏比分优先展示 */
        public java.util.List<String> favoriteTeams = new java.util.ArrayList<>();
        /** 关注的赛事 (百度赛事名, 如 "英超"); 空则插件用内置默认 */
        public java.util.List<String> followedLeagues = new java.util.ArrayList<>();
        /** 上次选中的赛事过滤器; "" = 全部赛事 */
        public String lastSelectedLeague = "";
        /** 首次使用日期 (ISO, 评分引导用); "" = 尚未记录 */
        public String firstUseDate = "";
        /** 是否已弹过评分引导 (只弹一次) */
        public boolean ratingPrompted = false;
        /** 界面语言: "" = 跟随 IDE 区域, "en" = English, "zh" = 中文 */
        public String uiLanguage = "";
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

    /** 关注球队列表 (旧单字段自动迁移: 列表为空且旧值非空时以旧值播种) */
    public java.util.List<String> getFavoriteTeams() {
        if ((myState.favoriteTeams == null || myState.favoriteTeams.isEmpty())
                && myState.favoriteTeam != null && !myState.favoriteTeam.isEmpty()) {
            return java.util.Collections.singletonList(myState.favoriteTeam);
        }
        return myState.favoriteTeams == null ? java.util.Collections.emptyList() : myState.favoriteTeams;
    }

    public void setFavoriteTeams(java.util.List<String> teams) {
        myState.favoriteTeams = new java.util.ArrayList<>(teams);
        // 同步旧字段为第一队, 兼容旧版本读配置
        myState.favoriteTeam = teams.isEmpty() ? "" : teams.get(0);
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

    public String getFirstUseDate() {
        return myState.firstUseDate == null ? "" : myState.firstUseDate;
    }

    public void setFirstUseDate(String date) {
        myState.firstUseDate = date == null ? "" : date;
    }

    public boolean isRatingPrompted() {
        return myState.ratingPrompted;
    }

    public void setRatingPrompted(boolean prompted) {
        myState.ratingPrompted = prompted;
    }

    public String getUiLanguage() {
        return myState.uiLanguage == null ? "" : myState.uiLanguage;
    }

    public void setUiLanguage(String lang) {
        myState.uiLanguage = lang == null ? "" : lang;
    }
}
