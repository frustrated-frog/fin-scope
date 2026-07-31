package com.finscope.service.radar;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RadarEvidenceSynthesisAgent {
    private final LlmChatClient llm;
    private final ObjectMapper json;
    private final RadarAgentTraceRecorder traces;

    public RadarEvidenceSynthesisAgent(LlmChatClient llm,ObjectMapper json,RadarAgentTraceRecorder traces){
        this.llm=llm;this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.traces=traces;
    }

    public Result synthesize(RadarEvent event,List<RadarEvidence> evidence){
        long started=System.currentTimeMillis();
        if(llm==null||!llm.isConfigured())return fallback(event,evidence,started,"MODEL_DISABLED",null);
        try{
            String raw=llm.complete(systemPrompt(),json.writeValueAsString(payload(event,evidence)),15_000,500);
            Draft draft=json.readValue(extractJson(raw),Draft.class);validate(draft);
            Result result=new Result(compact(draft.summary,240),compact(draft.mainDriver,120),
                    compact(draft.conflictOrGap,180),compact(draft.nextObservation,180),true,null);
            trace(event,result,"SUCCESS",null,started);return result;
        }catch(Exception error){return fallback(event,evidence,started,"INVALID_OUTPUT",error.getClass().getSimpleName());}
    }

    private Result fallback(RadarEvent event,List<RadarEvidence> evidence,long started,String reason,String error){
        int count=evidence==null?0:evidence.size();java.util.Set<String> sources=new java.util.HashSet<String>();
        if(evidence!=null)for(RadarEvidence item:evidence)if(!text(item.getSourceName()).isEmpty())sources.add(text(item.getSourceName()).toLowerCase());
        Result result=new Result("已补充"+count+"条外部证据，来自"+sources.size()+"个来源","等待人工核对主要驱动",
                "Agent综合不可用，请以原始证据为准",event==null?"继续观察后续公开信息":text(event.getNextObservation()),false,reason);
        trace(event,result,"FALLBACK",error,started);return result;
    }
    private void validate(Draft draft){if(draft==null||blank(draft.summary)||blank(draft.mainDriver)||blank(draft.conflictOrGap)||blank(draft.nextObservation)
            ||draft.summary.length()>240||draft.mainDriver.length()>120||draft.conflictOrGap.length()>180||draft.nextObservation.length()>180
            ||containsAdvice(draft.summary+draft.mainDriver+draft.conflictOrGap+draft.nextObservation))throw new IllegalArgumentException("证据综合输出不合法");}
    private boolean containsAdvice(String value){return value.contains("买入")||value.contains("卖出")||value.contains("加仓")||value.contains("减仓")||value.contains("目标价");}
    private String systemPrompt(){return "你是个人投资初学者的证据编辑。只能根据给定证据总结，不得补充外部事实。输出纯JSON，"
            +"只允许summary、mainDriver、conflictOrGap、nextObservation。清楚区分已确认事实和信息缺口，不得给出买卖、仓位或目标价建议。";}
    private Map<String,Object> payload(RadarEvent event,List<RadarEvidence> evidence){Map<String,Object> value=new LinkedHashMap<String,Object>();
        value.put("eventTitle",event==null?"":event.getCanonicalTitle());List<Map<String,String>> items=new ArrayList<Map<String,String>>();
        if(evidence!=null)for(RadarEvidence item:evidence){Map<String,String> row=new LinkedHashMap<String,String>();row.put("type",item.getEvidenceType());
            row.put("source",item.getSourceName());row.put("title",compact(item.getTitle(),160));row.put("content",compact(item.getSummary(),500));items.add(row);}value.put("evidence",items);return value;}
    private void trace(RadarEvent event,Result result,String status,String error,long started){traces.record("radar-evidence-synthesis","RADAR_EVENT",event==null?null:event.getId(),status,
            "evidence synthesis","summary="+result.summary,error,result.fallbackReason,System.currentTimeMillis()-started,"{}");}
    private String extractJson(String raw){if(raw==null)return"";int start=raw.indexOf('{'),end=raw.lastIndexOf('}');return start>=0&&end>=start?raw.substring(start,end+1):raw.trim();}
    private String compact(String value,int max){String result=text(value).replaceAll("[\\r\\n\\t]+"," ");return result.length()<=max?result:result.substring(0,max);}
    private String text(Object value){return value==null?"":String.valueOf(value).trim();}private boolean blank(String value){return text(value).isEmpty();}
    private static final class Draft{public String summary;public String mainDriver;public String conflictOrGap;public String nextObservation;}
    public static final class Result{private final String summary,mainDriver,conflictOrGap,nextObservation,fallbackReason;private final boolean generated;
        Result(String summary,String mainDriver,String conflictOrGap,String nextObservation,boolean generated,String fallbackReason){this.summary=summary;this.mainDriver=mainDriver;this.conflictOrGap=conflictOrGap;this.nextObservation=nextObservation;this.generated=generated;this.fallbackReason=fallbackReason;}
        public String getSummary(){return summary;}public String getMainDriver(){return mainDriver;}public String getConflictOrGap(){return conflictOrGap;}
        public String getNextObservation(){return nextObservation;}public boolean isGenerated(){return generated;}public String getFallbackReason(){return fallbackReason;}}
}
