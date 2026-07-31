package com.finscope.domain.radar;

import java.util.ArrayList;
import java.util.List;

public class RadarEvidencePlan {
    private String eventType;
    private String subject;
    private String stockCode;
    private List<Action> actions = new ArrayList<Action>();

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public List<Action> getActions() { return actions; }
    public void setActions(List<Action> actions) { this.actions = actions == null ? new ArrayList<Action>() : actions; }

    public static class Action {
        private String toolCode;
        private String materialType;
        private String stockCode;
        private String query;

        public String getToolCode() { return toolCode; }
        public void setToolCode(String toolCode) { this.toolCode = toolCode; }
        public String getMaterialType() { return materialType; }
        public void setMaterialType(String materialType) { this.materialType = materialType; }
        public String getStockCode() { return stockCode; }
        public void setStockCode(String stockCode) { this.stockCode = stockCode; }
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }
}
