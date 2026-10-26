package com.fifa.plugin.core;

import com.fifa.plugin.api.BaiduMatchListClient;
import com.fifa.plugin.model.League;
import com.fifa.plugin.settings.FifaSettingsState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 赛事注册表 — 全赛事插件的核心状态
 * <ul>
 *   <li>目录: 从百度 /al/match/list 懒加载 (30 分钟缓存), 失败回退内置列表</li>
 *   <li>关注: 持久化在 FifaSettingsState.followedLeagues</li>
 *   <li>选中: 全局赛事过滤器 ("" = 全部赛事), 6 个 Tab 共享, 支持变更监听</li>
 * </ul>
 */
@Service
public final class LeagueRegistry {

    private static final Logger LOG = Logger.getInstance(LeagueRegistry.class);

    /** 目录拉取失败时的内置兜底 (常见足球赛事) */
    private static final List<String> DEFAULT_LEAGUES = List.of(
            "世界杯", "英超", "西甲", "德甲", "意甲", "法甲", "欧冠", "亚冠", "中超");

    /** 默认关注 (首装) */
    private static final List<String> DEFAULT_FOLLOWED = List.of(
            "英超", "西甲", "德甲", "意甲", "法甲", "欧冠", "世界杯");

    private static final long CATALOG_TTL_MS = TimeUnit.MINUTES.toMillis(30);

    private final Object catalogLock = new Object();
    private volatile List<League> catalog = Collections.emptyList();
    private final AtomicLong catalogFetchedAt = new AtomicLong(0);
    private volatile boolean catalogLoading = false;

    private volatile String selectedLeague = "";
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public static LeagueRegistry getInstance() {
        return ApplicationManager.getApplication().getService(LeagueRegistry.class);
    }

    // ---- 目录 ----

    /**
     * 获取目录 (不阻塞: 未加载/过期时返回缓存或兜底列表, 并异步刷新)
     */
    public List<League> getCatalog() {
        List<League> cached = catalog;
        long now = System.currentTimeMillis();
        if ((cached.isEmpty() || now - catalogFetchedAt.get() > CATALOG_TTL_MS) && !catalogLoading) {
            catalogLoading = true;
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    BaiduMatchListClient client = ApplicationManager.getApplication()
                            .getService(BaiduMatchListClient.class);
                    List<League> fresh = client != null ? client.fetchFootballLeagues() : Collections.emptyList();
                    if (fresh != null && !fresh.isEmpty()) {
                        catalog = fresh;
                        catalogFetchedAt.set(System.currentTimeMillis());
                        fireChanged();
                    }
                } catch (Exception e) {
                    LOG.warn("league catalog refresh failed", e);
                } finally {
                    catalogLoading = false;
                }
            });
        }
        return cached.isEmpty() ? defaultCatalog() : cached;
    }

    private static List<League> defaultCatalog() {
        List<League> out = new ArrayList<>();
        for (int i = 0; i < DEFAULT_LEAGUES.size(); i++) {
            out.add(new League(DEFAULT_LEAGUES.get(i), null, -1, 0, i));
        }
        return out;
    }

    /**
     * 目录里是否真的有该赛事 (用于判断过滤器选项有效性)
     */
    public boolean hasLeague(String baiduKey) {
        for (League l : catalog) {
            if (l.getBaiduKey().equals(baiduKey)) return true;
        }
        return false;
    }

    // ---- 关注 ----

    public List<String> getFollowed() {
        List<String> followed = FifaSettingsState.getInstance().getFollowedLeagues();
        return followed.isEmpty() ? DEFAULT_FOLLOWED : followed;
    }

    public void setFollowed(List<String> leagues) {
        FifaSettingsState.getInstance().setFollowedLeagues(
                leagues == null ? Collections.emptyList() : leagues);
    }

    // ---- 全局选中 (跨 Tab 共享过滤器) ----

    /** @return 当前选中赛事名; "" = 全部赛事 */
    public String getSelected() {
        return selectedLeague;
    }

    /**
     * 排名类 Tab 需要"具体赛事"时的兜底: 未选则用世界杯 (分组/单表形态都验证过)
     */
    public String getSelectedOrWorldCup() {
        String s = selectedLeague;
        return (s == null || s.isEmpty()) ? "世界杯" : s;
    }

    public void setSelected(String baiduKey) {
        String next = baiduKey == null ? "" : baiduKey;
        if (!next.equals(selectedLeague)) {
            selectedLeague = next;
            FifaSettingsState.getInstance().setLastSelectedLeague(next);
            fireChanged();
        }
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private void fireChanged() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception e) {
                LOG.warn("league change listener error", e);
            }
        }
    }
}
