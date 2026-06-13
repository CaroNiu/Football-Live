package com.fifa.plugin.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * TheSportsDB eventsday.php 响应包装
 * <pre>
 * {
 *   "events": [ {Match}, {Match} ... ]
 * }
 * </pre>
 */
public class MatchListResponse {
    @SerializedName("events")
    private List<Match> matches;

    public List<Match> getMatches() { return matches; }
    public void setMatches(List<Match> matches) { this.matches = matches; }
}
