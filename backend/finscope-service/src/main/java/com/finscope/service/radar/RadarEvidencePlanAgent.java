package com.finscope.service.radar;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEvidencePlan;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RadarEvidencePlanAgent {
    private static final Pattern STOCK_CODE = Pattern.compile("(?<!\\d)([036]\\d{5})(?!\\d)");
    private static final int MAX_ACTIONS = 2;
    private final LlmChatClient llm;
    private final ObjectMapper json;
    private final RadarAgentTraceRecorder traces;

    public RadarEvidencePlanAgent(LlmChatClient llm, ObjectMapper json, RadarAgentTraceRecorder traces) {
        this.llm = llm;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.traces = traces;
    }

    public RadarEvidencePlan plan(RadarEvent event, List<RadarSignal> signals) {
        long started = System.currentTimeMillis();
        if (llm == null || !llm.isConfigured()) return fallback(event, signals, started, "MODEL_DISABLED", null);
        try {
            String raw = llm.complete(systemPrompt(), json.writeValueAsString(payload(event, signals)), 15_000, 500);
            RadarEvidencePlan plan = json.readValue(extractJson(raw), RadarEvidencePlan.class);
            validate(plan, extractStockCode(event, signals));
            trace(event, plan, "SUCCESS", null, null, started);
            return plan;
        } catch (Exception error) {
            return fallback(event, signals, started, "INVALID_OUTPUT", error.getClass().getSimpleName());
        }
    }

    private RadarEvidencePlan fallback(RadarEvent event, List<RadarSignal> signals, long started,
                                       String reason, String errorType) {
        RadarEvidencePlan plan = new RadarEvidencePlan(); plan.setEventType("GENERAL_EVENT");
        plan.setSubject(compact(event == null ? "" : event.getCanonicalTitle(), 60));
        String stockCode = extractStockCode(event, signals); plan.setStockCode(stockCode);
        List<RadarEvidencePlan.Action> actions = new ArrayList<RadarEvidencePlan.Action>();
        if (!stockCode.isEmpty()) actions.add(action("research_material_search", "ANNOUNCEMENT", stockCode,
                compact(event == null ? "" : event.getCanonicalTitle(), 80)));
        actions.add(action("public_news_search", null, null, compact(event == null ? "市场事件" : event.getCanonicalTitle(), 120)));
        plan.setActions(actions);
        trace(event, plan, "FALLBACK", errorType, reason, started);
        return plan;
    }

    private void validate(RadarEvidencePlan plan, String inputStockCode) {
        if (plan == null || plan.getActions() == null || plan.getActions().size() > MAX_ACTIONS) {
            throw new IllegalArgumentException("证据计划动作数不合法");
        }
        for (RadarEvidencePlan.Action action : plan.getActions()) {
            String tool = text(action.getToolCode()); String query = text(action.getQuery());
            if (!("public_news_search".equals(tool) || "research_material_search".equals(tool))
                    || query.isEmpty() || query.length() > 180 || query.contains("://")) {
                throw new IllegalArgumentException("证据计划动作不在白名单内");
            }
            if ("research_material_search".equals(tool)) {
                if (text(inputStockCode).isEmpty() || !text(action.getStockCode()).equals(inputStockCode)
                        || !isMaterialType(action.getMaterialType())) throw new IllegalArgumentException("结构化资料动作不合法");
            }
        }
    }

    private Map<String, Object> payload(RadarEvent event, List<RadarSignal> signals) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("title", event == null ? "" : event.getCanonicalTitle());
        value.put("summary", compact(event == null ? "" : event.getSummary(), 500));
        List<Map<String, String>> items = new ArrayList<Map<String, String>>();
        if (signals != null) for (RadarSignal signal : signals) { Map<String, String> item = new LinkedHashMap<String, String>();
            item.put("title", compact(signal.getTitle(), 160)); item.put("content", compact(signal.getContent(), 320)); items.add(item); }
        value.put("signals", items); return value;
    }

    private String systemPrompt() {
        return "你是个人研究雷达的证据规划器。只输出纯JSON：eventType、subject、stockCode、actions。"
                + "actions最多2项，toolCode只能是research_material_search或public_news_search。"
                + "结构化资料还需materialType（ANNOUNCEMENT|INTERACTION|BROKER_REPORT|NEWS_FLASH）和六位股票代码；"
                + "无法从输入确认代码时不得规划结构化资料。query必须是输入事实可支持的简短检索词，不得给投资建议。";
    }

    private RadarEvidencePlan.Action action(String tool, String type, String code, String query) {
        RadarEvidencePlan.Action value = new RadarEvidencePlan.Action(); value.setToolCode(tool);
        value.setMaterialType(type); value.setStockCode(code); value.setQuery(query); return value;
    }
    private String extractStockCode(RadarEvent event, List<RadarSignal> signals) {
        StringBuilder text = new StringBuilder(); if (event != null) text.append(event.getCanonicalTitle()).append(' ').append(event.getSummary());
        if (signals != null) for (RadarSignal signal : signals) text.append(' ').append(signal.getTitle()).append(' ').append(signal.getContent());
        Matcher matcher = STOCK_CODE.matcher(text.toString()); return matcher.find() ? matcher.group(1) : "";
    }
    private boolean isMaterialType(String value) { String type=text(value); return "ANNOUNCEMENT".equals(type)||"INTERACTION".equals(type)||"BROKER_REPORT".equals(type)||"NEWS_FLASH".equals(type); }
    private String extractJson(String raw) { if(raw==null)return ""; int start=raw.indexOf('{'),end=raw.lastIndexOf('}'); return start>=0&&end>=start?raw.substring(start,end+1):raw.trim(); }
    private String compact(String value,int max){String v=text(value).replaceAll("[\\r\\n\\t]+"," ");return v.length()<=max?v:v.substring(0,max);}
    private String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private void trace(RadarEvent event,RadarEvidencePlan plan,String status,String error,String fallback,long started){
        traces.record("radar-evidence-plan","RADAR_EVENT",event==null?null:event.getId(),status,
                "event="+(event==null?"":event.getCanonicalTitle()),"actions="+plan.getActions().size(),error,fallback,
                System.currentTimeMillis()-started,"{\"budget\":2}");
    }
}
