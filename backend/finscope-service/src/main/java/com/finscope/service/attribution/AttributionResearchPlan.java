package com.finscope.service.attribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 归因任务的结构化研究计划，仅描述执行合同，不执行外部调用。 */
public class AttributionResearchPlan {
    private int version = 1;
    private String objective;
    private Budget budget = new Budget();
    private List<Track> tracks = new ArrayList<Track>();

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public Budget getBudget() { return budget; }
    public void setBudget(Budget budget) { this.budget = budget; }
    public List<Track> getTracks() { return Collections.unmodifiableList(tracks); }
    public void setTracks(List<Track> tracks) { this.tracks = tracks == null ? new ArrayList<Track>() : new ArrayList<Track>(tracks); }
    public boolean hasTrack(String code) {
        for (Track track : tracks) {
            if (code.equals(track.getCode())) return true;
        }
        return false;
    }

    public static class Budget {
        private int maxQueries = 8;
        private int maxQueriesPerTrack = 2;
        private int maxRunSeconds = 90;
        public int getMaxQueries() { return maxQueries; }
        public void setMaxQueries(int maxQueries) { this.maxQueries = maxQueries; }
        public int getMaxQueriesPerTrack() { return maxQueriesPerTrack; }
        public void setMaxQueriesPerTrack(int maxQueriesPerTrack) { this.maxQueriesPerTrack = maxQueriesPerTrack; }
        public int getMaxRunSeconds() { return maxRunSeconds; }
        public void setMaxRunSeconds(int maxRunSeconds) { this.maxRunSeconds = maxRunSeconds; }
    }

    public static class Track {
        private String code;
        private String successCriteria;
        private int maxQueries;
        private List<String> queries = new ArrayList<String>();
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getSuccessCriteria() { return successCriteria; }
        public void setSuccessCriteria(String successCriteria) { this.successCriteria = successCriteria; }
        public int getMaxQueries() { return maxQueries; }
        public void setMaxQueries(int maxQueries) { this.maxQueries = maxQueries; }
        public List<String> getQueries() { return Collections.unmodifiableList(queries); }
        public void setQueries(List<String> queries) { this.queries = queries == null ? new ArrayList<String>() : new ArrayList<String>(queries); }
    }
}
