package com.finscope.service.marketintel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalHypothesis;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CapitalInterpretationAgent {
    private static final String PROMPT_VERSION="capital-interpret-v1";
    private final LlmChatClient llm;private final ObjectMapper json;private final CapitalHypothesisGate gate;private final CapitalFactAssembler facts;
    public CapitalInterpretationAgent(LlmChatClient llm,ObjectMapper json,CapitalHypothesisGate gate,CapitalFactAssembler facts){this.llm=llm;this.json=json;this.gate=gate;this.facts=facts;}
    public CapitalInterpretation interpret(CapitalBehaviorSnapshot snapshot,CapitalRuleExplanation rules){
        if(!llm.isConfigured())return fallback(snapshot,rules,"LLM_NOT_CONFIGURED");
        try{String output=llm.complete(systemPrompt(),input(snapshot),15000);JsonNode root=json.readTree(output);validate(root);CapitalInterpretation result=base(snapshot,rules);
            result.setStatus("SUCCEEDED");result.setPlainSummary(requiredText(root,"plainSummary"));result.setHypotheses(gate.apply(snapshot,parseHypotheses(root.path("hypotheses"))));
            result.setDataGaps(strings(root.path("dataGaps")));result.setObservationPoints(strings(root.path("observationPoints")));result.setDisclaimer(requiredText(root,"disclaimer"));result.setOutputHash(JdkFinanceHttpClient.sha256(output));return result;
        }catch(Exception e){return fallback(snapshot,rules,"INVALID_MODEL_OUTPUT");}
    }
    private CapitalInterpretation base(CapitalBehaviorSnapshot snapshot,CapitalRuleExplanation rules){CapitalInterpretation value=new CapitalInterpretation();value.setInterpretationType("AGENT");value.setRuleVersion(rules.getRuleVersion());value.setModelName(llm.modelName());value.setPromptVersion(PROMPT_VERSION);value.setFacts(facts.assemble(snapshot));return value;}
    private CapitalInterpretation fallback(CapitalBehaviorSnapshot snapshot,CapitalRuleExplanation rules,String reason){CapitalInterpretation value=base(snapshot,rules);value.setStatus("FALLBACK");value.setPlainSummary(rules.getSummary());value.setHypotheses(Collections.emptyList());value.setDataGaps(rules.getDataGaps());value.setObservationPoints(Collections.singletonList("继续观察成交金额、换手率与主力净流向是否保持连续。"));value.setDisclaimer("规则解释和模型假设仅用于研究，不构成投资建议。");value.setFallbackReason(reason);return value;}
    private String systemPrompt(){return "你是A股资金行为研究Agent。只输出JSON；事实不可改写；假设必须引用metricRefs；拆单和隐藏资金在无Level-2时只能LOW；第一阶段不得输出HIGH；不得给出买卖建议。";}
    private String input(CapitalBehaviorSnapshot snapshot){StringBuilder value=new StringBuilder("{\"fingerprint\":\"").append(snapshot.getFingerprint()).append("\",\"metrics\":[");boolean first=true;for(CapitalFlowPoint p:snapshot.getFacts()){if(!first)value.append(',');first=false;value.append("{\"at\":\"").append(p.getObservedAt()).append("\",\"price\":").append(number(p.getPrice())).append(",\"amount\":").append(number(p.getIntervalTradeAmount())).append(",\"mainNet\":").append(number(p.getMainNetInflow())).append(",\"refs\":[\"").append(p.getId()==null?"":p.metricRef("mainNetInflow")).append("\"]}");}return value.append("]}").toString();}
    private String number(Object value){return value==null?"null":value.toString();}
    private void validate(JsonNode root){if(!root.isObject()||!root.path("hypotheses").isArray()||!root.path("dataGaps").isArray()||!root.path("observationPoints").isArray())throw new IllegalArgumentException("invalid agent JSON contract");requiredText(root,"plainSummary");requiredText(root,"disclaimer");}
    private String requiredText(JsonNode root,String name){JsonNode value=root.get(name);if(value==null||!value.isTextual()||value.asText().trim().isEmpty())throw new IllegalArgumentException("missing "+name);return value.asText();}
    private List<CapitalHypothesis> parseHypotheses(JsonNode nodes){List<CapitalHypothesis> values=new ArrayList<CapitalHypothesis>();for(JsonNode node:nodes){CapitalHypothesis h=new CapitalHypothesis();h.setType(requiredText(node,"type"));h.setClaim(requiredText(node,"claim"));h.setConfidence(requiredText(node,"confidence"));h.setSupportingMetricRefs(strings(node.path("supportingMetricRefs")));h.setCounterEvidence(strings(node.path("counterEvidence")));h.setDataGaps(strings(node.path("dataGaps")));values.add(h);}return values;}
    private List<String> strings(JsonNode nodes){if(!nodes.isArray())throw new IllegalArgumentException("expected array");List<String> values=new ArrayList<String>();for(JsonNode node:nodes){if(!node.isTextual())throw new IllegalArgumentException("expected string");values.add(node.asText());}return values;}
}
