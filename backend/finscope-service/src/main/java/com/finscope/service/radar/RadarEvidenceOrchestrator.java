package com.finscope.service.radar;

import com.finscope.dao.radar.RadarEvidenceRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarEvidencePlan;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RadarEvidenceOrchestrator {
    private static final int AUTO_SCORE_THRESHOLD = 75;
    private static final int MAX_ACTIONS = 2;
    private final RadarEvidencePlanAgent planner;
    private final RadarEvidenceToolAdapter tools;
    private final RadarEvidenceRepository evidence;
    private final RadarEvidenceSynthesisAgent synthesis;
    private final RadarAgentTraceRecorder traces;

    public RadarEvidenceOrchestrator(RadarEvidencePlanAgent planner, RadarEvidenceToolAdapter tools,
                                    RadarEvidenceRepository evidence) {
        this(planner,tools,evidence,null,null);
    }

    @Autowired
    public RadarEvidenceOrchestrator(RadarEvidencePlanAgent planner, RadarEvidenceToolAdapter tools,
                                    RadarEvidenceRepository evidence, RadarEvidenceSynthesisAgent synthesis,
                                    RadarAgentTraceRecorder traces) {
        this.planner=planner; this.tools=tools; this.evidence=evidence; this.synthesis=synthesis; this.traces=traces;
    }

    public Outcome enrich(RadarEvent event, List<RadarSignal> signals) {
        if (event == null || event.getId() == null || event.getPriorityScore() < AUTO_SCORE_THRESHOLD) {
            return Outcome.skipped();
        }
        String fingerprint = fingerprint(event);
        if (fingerprint.equals(event.getEvidenceFingerprint())) {
            return new Outcome("CACHED", text(event.getEvidenceSummary()), text(event.getEvidenceWarning()),
                    event.getEvidenceCount(), event.getEvidenceSourceCount(), fingerprint);
        }
        RadarEvidencePlan plan = planner.plan(event, signals);
        List<RadarEvidence> collected = new ArrayList<RadarEvidence>(); List<String> warnings = new ArrayList<String>();
        int executed = 0;
        if (plan != null && plan.getActions() != null) for (RadarEvidencePlan.Action action : plan.getActions()) {
            if (executed >= MAX_ACTIONS) break;
            String validation = validate(action); if (validation != null) { warnings.add(validation); continue; }
            executed++;
            long started=System.currentTimeMillis();
            RadarEvidenceToolAdapter.ToolResult result = tools.execute(action);
            collected.addAll(result.getEvidence()); warnings.addAll(result.getWarnings());
            if(traces!=null)traces.record("radar-tool-"+action.getToolCode(),"RADAR_EVENT",event.getId(),
                    result.getWarnings().isEmpty()?"SUCCESS":"DEGRADED","query="+text(action.getQuery()),
                    "evidence="+result.getEvidence().size(),result.getWarnings().isEmpty()?null:"TOOL_WARNING",
                    result.getWarnings().isEmpty()?null:String.join("；",result.getWarnings()),System.currentTimeMillis()-started,
                    "{\"tool\":\""+action.getToolCode()+"\"}");
        }
        collected = deduplicate(collected); evidence.replaceForEvent(event.getId(), collected);
        Set<String> sources = new HashSet<String>();
        for (RadarEvidence item : collected) if (item.getSourceName()!=null&&!item.getSourceName().trim().isEmpty()) sources.add(item.getSourceName().trim().toLowerCase());
        String status = collected.isEmpty() ? (warnings.isEmpty()?"NO_PROGRESS":"DEGRADED") : "SUCCESS";
        String summary = collected.isEmpty()?"暂未检索到新的外部证据":"已补充"+collected.size()+"条证据，来自"+sources.size()+"个来源";
        String nextObservation="";
        if(!collected.isEmpty()&&synthesis!=null){RadarEvidenceSynthesisAgent.Result result=synthesis.synthesize(event,collected);
            summary=result.getSummary()+"；主要驱动："+result.getMainDriver();nextObservation=result.getNextObservation();
            if(!text(result.getConflictOrGap()).isEmpty())warnings.add("待确认："+result.getConflictOrGap());}
        return new Outcome(status, summary, String.join("；",warnings), collected.size(), sources.size(), fingerprint,nextObservation);
    }

    private String validate(RadarEvidencePlan.Action action) {
        if (action==null) return "已忽略空证据动作"; String tool=text(action.getToolCode());
        if (!("research_material_search".equals(tool)||"public_news_search".equals(tool))) return "已忽略非白名单工具";
        String query=text(action.getQuery()); if(query.isEmpty()||query.length()>180||query.contains("://")) return "已忽略不安全检索词";
        if("research_material_search".equals(tool)&&!text(action.getStockCode()).matches("\\d{6}")) return "缺少可确认的六位股票代码，未检索结构化资料";
        return null;
    }
    private List<RadarEvidence> deduplicate(List<RadarEvidence> values){List<RadarEvidence> result=new ArrayList<RadarEvidence>();Set<String> seen=new HashSet<String>();for(RadarEvidence value:values){String key=text(value.getUrl());if(key.isEmpty())key=text(value.getSourceName())+"|"+text(value.getTitle());if(seen.add(key))result.add(value);}return result;}
    private String fingerprint(RadarEvent event){String value=text(event.getEventKey())+"|"+text(event.getCanonicalTitle())+"|"+event.getSourceCount()+"|"+event.getSignalCount()+"|"+event.getLastSeenAt();try{byte[]digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format("%02x",b));return hex.toString();}catch(Exception error){throw new IllegalStateException(error);}}
    private String text(Object value){return value==null?"":String.valueOf(value).trim();}

    public static final class Outcome {
        private final String status,summary,warning,fingerprint,nextObservation; private final int evidenceCount,sourceCount;
        Outcome(String status,String summary,String warning,int evidenceCount,int sourceCount,String fingerprint){this(status,summary,warning,evidenceCount,sourceCount,fingerprint,"");}
        Outcome(String status,String summary,String warning,int evidenceCount,int sourceCount,String fingerprint,String nextObservation){this.status=status;this.summary=summary;this.warning=warning;this.evidenceCount=evidenceCount;this.sourceCount=sourceCount;this.fingerprint=fingerprint;this.nextObservation=nextObservation;}
        static Outcome skipped(){return new Outcome("SKIPPED","低优先级事件不自动补充证据","",0,0,"");}
        public String getStatus(){return status;} public String getSummary(){return summary;} public String getWarning(){return warning;}
        public int getEvidenceCount(){return evidenceCount;} public int getSourceCount(){return sourceCount;} public String getFingerprint(){return fingerprint;} public String getNextObservation(){return nextObservation;}
    }
}
