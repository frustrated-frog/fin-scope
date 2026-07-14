package com.finscope.service.marketintel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalHypothesis;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalInterpretationAgentTest {
    @Test
    void capsHiddenFlowAtLowAndDropsUnknownMetricReferences() {
        CapitalHypothesis hidden=hypothesis("HIDDEN_FLOW","HIGH","flow:101:mainNetInflow");
        CapitalHypothesis invented=hypothesis("DISTRIBUTION","MID","flow:999:mainNetInflow");
        assertEquals(1,new CapitalHypothesisGate().apply(snapshot(),Arrays.asList(hidden,invented)).size());
        CapitalHypothesis accepted=new CapitalHypothesisGate().apply(snapshot(),Arrays.asList(hidden,invented)).get(0);
        assertEquals("LOW",accepted.getConfidence());
        assertTrue(accepted.getCounterEvidence().stream().anyMatch(v->v.contains("Level-2")));
    }

    @Test
    void keepsServerFactsAndReturnsHonestFallbackWhenLlmIsNotConfigured() {
        LlmChatClient llm=new LlmChatClient(){public boolean isConfigured(){return false;}public String modelName(){return "";}public String complete(String a,String b){throw new AssertionError("must not call LLM");}};
        CapitalInterpretationAgent agent=new CapitalInterpretationAgent(llm,new ObjectMapper(),new CapitalHypothesisGate(),new CapitalFactAssembler());
        CapitalInterpretation result=agent.interpret(snapshot(),rules());
        assertEquals("FALLBACK",result.getStatus());assertEquals("LLM_NOT_CONFIGURED",result.getFallbackReason());
        assertEquals("capital-rules-v1",result.getRuleVersion());assertFalse(result.getFacts().isEmpty());
    }

    @Test
    void parsesStrictModelJsonButDoesNotAcceptModelSuppliedFacts() {
        LlmChatClient llm=new LlmChatClient(){public boolean isConfigured(){return true;}public String modelName(){return "test-model";}
            public String complete(String a,String b){return "{\"plainSummary\":\"存在拆单可能\",\"facts\":[\"伪造事实\"],\"hypotheses\":[{\"type\":\"ORDER_SPLITTING\",\"claim\":\"可能存在拆单\",\"confidence\":\"HIGH\",\"supportingMetricRefs\":[\"flow:101:mainNetInflow\"],\"counterEvidence\":[],\"dataGaps\":[]}],\"dataGaps\":[\"缺少逐笔\"],\"observationPoints\":[\"观察尾盘\"],\"disclaimer\":\"不构成投资建议\"}";}};
        CapitalInterpretation result=new CapitalInterpretationAgent(llm,new ObjectMapper(),new CapitalHypothesisGate(),new CapitalFactAssembler()).interpret(snapshot(),rules());
        assertEquals("SUCCEEDED",result.getStatus());assertEquals("LOW",result.getHypotheses().get(0).getConfidence());
        assertFalse(result.getFacts().contains("伪造事实"));assertTrue(result.getFacts().get(0).contains("主力净流入"));
    }

    private CapitalBehaviorSnapshot snapshot(){CapitalFlowPoint p=new CapitalFlowPoint();p.setId(101L);p.setInstrumentId(7L);p.setGranularity("MINUTE_1");p.setObservedAt(LocalDateTime.of(2026,7,14,10,30));p.setMainNetInflow(new BigDecimal("18000000"));p.setIntervalTradeAmount(new BigDecimal("120000000"));p.setPrice(new BigDecimal("1480.50"));return CapitalBehaviorSnapshot.of(7L,p.getObservedAt(),Collections.singletonList(p),Collections.emptyList(),"fingerprint");}
    private CapitalRuleExplanation rules(){CapitalRuleExplanation value=new CapitalRuleExplanation();value.setRuleVersion("capital-rules-v1");value.setSummary("规则摘要");value.setItems(Collections.emptyList());value.setDataGaps(Collections.singletonList("缺少 Level-2"));return value;}
    private CapitalHypothesis hypothesis(String type,String confidence,String ref){CapitalHypothesis value=new CapitalHypothesis();value.setType(type);value.setClaim(type);value.setConfidence(confidence);value.setSupportingMetricRefs(Collections.singletonList(ref));return value;}
}
