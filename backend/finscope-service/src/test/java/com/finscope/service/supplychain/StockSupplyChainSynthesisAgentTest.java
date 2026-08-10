package com.finscope.service.supplychain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.supplychain.StockSupplyChainEvidence;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockSupplyChainSynthesisAgentTest {

    @Test
    void parsesThreeLayerMapAndKeepsOnlyKnownEvidenceReferences() throws Exception {
        StockSupplyChainSnapshot result = agent(validJson()).synthesize(
                "中微公司", "688012", Arrays.asList(evidence("E1"), evidence("E2")));

        assertEquals("半导体前道设备", result.getPosition());
        assertEquals(3, result.getNodes().size());
        assertEquals("UPSTREAM", result.getNodes().get(0).getLayer());
        assertEquals("COMPANY", result.getNodes().get(1).getLayer());
        assertEquals("DOWNSTREAM", result.getNodes().get(2).getLayer());
        assertEquals(Arrays.asList("E1"), result.getNodes().get(0).getEvidenceRefs());
    }

    @Test
    void rejectsAnUnknownEvidenceReference() {
        assertThrows(IllegalArgumentException.class, () -> agent(
                validJson().replace("[\"E1\"]", "[\"E99\"]"))
                .synthesize("中微公司", "688012", Arrays.asList(evidence("E1"))));
    }

    @Test
    void rejectsUnsupportedConfidence() {
        assertThrows(IllegalArgumentException.class, () -> agent(
                validJson().replace("\"HIGH\"", "\"CERTAIN\""))
                .synthesize("中微公司", "688012", Arrays.asList(evidence("E1"), evidence("E2"))));
    }

    @Test
    void rejectsInventedTopLevelFields() {
        assertThrows(IllegalArgumentException.class, () -> agent(
                validJson().replace("{\"summary\"", "{\"recommendation\":\"买入\",\"summary\""))
                .synthesize("中微公司", "688012", Arrays.asList(evidence("E1"), evidence("E2"))));
    }

    @Test
    void repairsOneInvalidContractWithACompactPromptAndARealisticTimeout() throws Exception {
        List<Integer> timeouts = new ArrayList<Integer>();
        List<Integer> promptLengths = new ArrayList<Integer>();
        int[] calls = {0};
        LlmChatClient llm = new LlmChatClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String modelName() { return "test-model"; }
            @Override public String complete(String systemPrompt, String userPrompt) {
                throw new AssertionError("must use explicit synthesis timeout");
            }
            @Override public String complete(String systemPrompt, String userPrompt,
                                             int timeoutMs, int maxOutputTokens) {
                calls[0]++;
                timeouts.add(timeoutMs);
                promptLengths.add(userPrompt.length());
                return calls[0] == 1
                        ? validJson().replace("{\"summary\"", "{\"unexpected\":\"field\",\"summary\"")
                        : validJson();
            }
        };
        List<StockSupplyChainEvidence> evidence = new ArrayList<StockSupplyChainEvidence>();
        for (int index = 1; index <= 8; index++) {
            StockSupplyChainEvidence item = evidence("E" + index);
            item.setExcerpt(repeat("公开证据内容", 1000));
            evidence.add(item);
        }

        StockSupplyChainSnapshot result = new StockSupplyChainSynthesisAgent(
                llm, new ObjectMapper()).synthesize("中微公司", "688012", evidence);

        assertEquals(2, calls[0]);
        assertEquals(Arrays.asList(60_000, 45_000), timeouts);
        assertTrue(promptLengths.get(0) < 30_000);
        assertEquals(3, result.getNodes().size());
    }

    private StockSupplyChainSynthesisAgent agent(String result) {
        LlmChatClient llm = new LlmChatClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String modelName() { return "test-model"; }
            @Override public String complete(String systemPrompt, String userPrompt) { return result; }
        };
        return new StockSupplyChainSynthesisAgent(llm, new ObjectMapper());
    }

    private StockSupplyChainEvidence evidence(String code) {
        StockSupplyChainEvidence value = new StockSupplyChainEvidence();
        value.setEvidenceCode(code);
        value.setTitle(code + " 年度报告");
        value.setUrl("https://example.com/" + code);
        value.setSource("example.com");
        value.setSourceTier("T1");
        value.setPublishedAt("2026-03-31");
        value.setExcerpt("公开披露内容");
        return value;
    }

    private String validJson() {
        return "{"
                + "\"summary\":\"公司位于半导体前道设备环节\","
                + "\"position\":\"半导体前道设备\","
                + "\"limitations\":\"部分客户未实名披露\","
                + "\"nodes\":["
                + node("UPSTREAM", "真空与射频零部件", "SUPPLY", "HIGH", "[\"E1\"]") + ","
                + node("COMPANY", "刻蚀设备", "CORE_BUSINESS", "HIGH", "[\"E1\",\"E2\"]") + ","
                + node("DOWNSTREAM", "晶圆制造", "CUSTOMER_INDUSTRY", "MEDIUM", "[\"E2\"]")
                + "]}";
    }

    private String node(String layer, String name, String relationType,
                        String confidence, String refs) {
        return "{\"layer\":\"" + layer + "\",\"name\":\"" + name
                + "\",\"relationType\":\"" + relationType
                + "\",\"description\":\"公开资料支持的关系\",\"confidence\":\""
                + confidence + "\",\"evidenceRefs\":" + refs + "}";
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
