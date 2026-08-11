package com.finscope.service.industrychain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndustryChainSynthesisAgentTest {

    @Test
    void parsesAndValidatesAnEvidenceBoundGraph() throws Exception {
        IndustryChainGraph graph = agent(validJson()).synthesize("AI算力", evidence());

        assertEquals("AI算力", graph.getName());
        assertEquals(3, graph.getNodes().size());
        assertEquals(2, graph.getEdges().size());
        assertEquals(Arrays.asList("E1"), graph.getEdges().get(0).getEvidenceRefs());
        assertEquals("INDUSTRY_CHAIN_V1", graph.getSchemaVersion());
    }

    @Test
    void repairsOneInvalidOutputAndValidatesTheRepair() throws Exception {
        int[] calls = {0};
        LlmChatClient llm = new LlmChatClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String modelName() { return "test-model"; }
            @Override public String complete(String systemPrompt, String userPrompt) {
                throw new AssertionError("必须使用显式超时");
            }
            @Override public String complete(String systemPrompt, String userPrompt,
                                             int timeoutMs, int maxOutputTokens) {
                calls[0]++;
                return calls[0] == 1
                        ? validJson().replace("\"nature\":\"INDUSTRY_LOGIC\"", "\"nature\":\"UNKNOWN\"")
                        : validJson();
            }
        };

        IndustryChainGraph graph = new IndustryChainSynthesisAgent(
                llm, new ObjectMapper(), new IndustryChainGraphValidator()).synthesize("AI算力", evidence());

        assertEquals(2, calls[0]);
        assertEquals(2, graph.getEdges().size());
    }

    private IndustryChainSynthesisAgent agent(String response) {
        LlmChatClient llm = new LlmChatClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String modelName() { return "test-model"; }
            @Override public String complete(String systemPrompt, String userPrompt) { return response; }
        };
        return new IndustryChainSynthesisAgent(llm, new ObjectMapper(), new IndustryChainGraphValidator());
    }

    private java.util.List<IndustryChainEvidence> evidence() {
        IndustryChainEvidence value = new IndustryChainEvidence();
        value.setEvidenceCode("E1");
        value.setTitle("AI 算力产业报告");
        value.setUrl("https://example.com/report");
        value.setSource("example.com");
        value.setSourceTier("T2");
        value.setExcerpt("芯片经服务器集成后进入数据中心");
        return Collections.singletonList(value);
    }

    private String validJson() {
        return "{\"summary\":\"AI 算力由芯片、服务器和数据中心构成\","
                + "\"limitations\":\"企业供销关系需以公告为准\",\"nodes\":["
                + node("stage:chip", "芯片", 1) + ","
                + node("stage:server", "服务器", 2) + ","
                + node("stage:datacenter", "数据中心", 3) + "],\"edges\":["
                + edge("flow:1", "stage:chip", "stage:server") + ","
                + edge("flow:2", "stage:server", "stage:datacenter") + "]}";
    }

    private String node(String key, String name, int order) {
        return "{\"nodeKey\":\"" + key + "\",\"type\":\"STAGE\",\"name\":\"" + name
                + "\",\"description\":\"产业链环节\",\"stageOrder\":" + order
                + ",\"stockCode\":\"\",\"confidence\":\"HIGH\",\"evidenceRefs\":[\"E1\"]}";
    }

    private String edge(String key, String source, String target) {
        return "{\"edgeKey\":\"" + key + "\",\"sourceKey\":\"" + source
                + "\",\"targetKey\":\"" + target + "\",\"type\":\"FLOWS_TO\","
                + "\"nature\":\"INDUSTRY_LOGIC\",\"description\":\"价值流转\","
                + "\"confidence\":\"HIGH\",\"evidenceRefs\":[\"E1\"]}";
    }
}
