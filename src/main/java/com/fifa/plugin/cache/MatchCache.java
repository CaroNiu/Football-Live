package com.fifa.plugin.cache;

import com.fifa.plugin.model.Match;
import com.fifa.plugin.model.StandingResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.components.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 内存缓存层 - 减少 API 调用频率
 */
@Service
public final class MatchCache {

    private final Cache<String, List<Match>> matchCache;
    private final Cache<String, StandingResponse> standingCache;

    public MatchCache() {
        // 赛程数据: 5分钟过期
        this.matchCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();

        // 积分榜: 10分钟过期
        this.standingCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10)
                .build();
    }

    // ---- 赛程缓存 ----

    public List<Match> getMatches(String key) {
        List<Match> matches = matchCache.getIfPresent(key);
        return matches != null ? matches : Collections.emptyList();
    }

    public void putMatches(String key, List<Match> matches) {
        matchCache.put(key, matches);
    }

    public void clearMatches() {
        matchCache.invalidateAll();
    }

    // ---- 积分榜缓存 ----

    public StandingResponse getStandings(String competitionCode) {
        return standingCache.getIfPresent(competitionCode);
    }

    public void putStandings(String competitionCode, StandingResponse standings) {
        standingCache.put(competitionCode, standings);
    }

    public void clearStandings() {
        standingCache.invalidateAll();
    }

    // ---- 全部清除 ----

    public void clearAll() {
        matchCache.invalidateAll();
        standingCache.invalidateAll();
    }
}
