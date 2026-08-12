package com.finscope.service.industrychain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustryChainSynthesisAgentTest {

    @Test
    void parsesAndValidatesAnEvidenceBoundGraph() throws Exception {
        IndustryChainGraph graph = agent(validJson()).synthesize("AI算力", evidence());

        assertEquals("AI算力", graph.getName());
        assertEquals(3, graph.getNodes().size());
        assertEquals(2, graph.getEdges().size());
        assertEquals(Arrays.asList("E1"), graph.getEdges().get(0).getEvidenceRefs());
        assertEquals("GROWTH", graph.getResearchContent().getOverview().getLifecycle());
        assertEquals("设备销售与服务", graph.getResearchContent().getStageProfiles().get(0).getBusinessModel());
        assertEquals("PRIMARY", graph.getEdges().get(0).getStrength());
        assertEquals("算力芯片决定整机性能上限", graph.getResearchContent().getNodeProfiles().get(0).getFunction());
        assertEquals("INDUSTRY_CHAIN_V3", graph.getSchemaVersion());
    }

    @Test
    void normalizesListLimitationsReturnedByTheModel() throws Exception {
        String response = validJson().replace(
                "\"limitations\":\"企业供销关系需以公告为准\"",
                "\"limitations\":[\"企业供销关系需以公告为准\",\"部分环节仍待观察\"]");

        IndustryChainGraph graph = agent(response).synthesize("AI算力", evidence());

        assertEquals("企业供销关系需以公告为准；部分环节仍待观察", graph.getLimitations());
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

    @Test
    void reservesEnoughTimeForLargeV3PrimaryAndRepairResponses() throws Exception {
        int[] calls = {0};
        int[] timeouts = {0, 0};
        LlmChatClient llm = new LlmChatClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String modelName() { return "test-model"; }
            @Override public String complete(String systemPrompt, String userPrompt) {
                throw new AssertionError("必须使用显式超时");
            }
            @Override public String complete(String systemPrompt, String userPrompt,
                                             int timeoutMs, int maxOutputTokens) {
                timeouts[calls[0]] = timeoutMs;
                calls[0]++;
                return calls[0] == 1
                        ? validJson().replace("\"nature\":\"INDUSTRY_LOGIC\"", "\"nature\":\"UNKNOWN\"")
                        : validJson();
            }
        };

        new IndustryChainSynthesisAgent(
                llm, new ObjectMapper(), new IndustryChainGraphValidator()).synthesize("AI算力", evidence());

        assertEquals(240_000, timeouts[0]);
        assertEquals(180_000, timeouts[1]);
    }

    @Test
    void removesUndisclosedSupplyRelationshipWithoutRemoteRepair() throws Exception {
        int[] calls = {0};
        LlmChatClient llm = new LlmChatClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String modelName() { return "test-model"; }
            @Override public String complete(String systemPrompt, String userPrompt) {
                throw new AssertionError("必须使用显式超时");
            }
            @Override public String complete(String systemPrompt, String userPrompt,
                                             int timeoutMs, int maxOutputTokens) throws Exception {
                calls[0]++;
                if (calls[0] > 1) {
                    throw new SocketTimeoutException("repair timed out");
                }
                return jsonWithUndisclosedSupplyRelationship();
            }
        };

        IndustryChainGraph graph = new IndustryChainSynthesisAgent(
                llm, new ObjectMapper(), new IndustryChainGraphValidator()).synthesize("AI算力", evidence());

        assertEquals(1, calls[0]);
        assertEquals(2, graph.getEdges().size());
        assertEquals("企业供销关系需以公告为准；未明确披露的企业供销关系已从图谱中移除。",
                graph.getLimitations());
    }

    @Test
    void sendsExplicitNodeAndEdgeEnumsToTheModel() throws Exception {
        String[] prompt = {""};
        LlmChatClient llm = new LlmChatClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String modelName() { return "test-model"; }
            @Override public String complete(String systemPrompt, String userPrompt) {
                prompt[0] = systemPrompt;
                return validJson();
            }
        };

        new IndustryChainSynthesisAgent(
                llm, new ObjectMapper(), new IndustryChainGraphValidator()).synthesize("AI算力", evidence());

        assertTrue(prompt[0].contains("node.type 只能是 INDUSTRY_CHAIN、STAGE、MATERIAL、EQUIPMENT、COMPONENT、"
                + "PRODUCT、TECHNOLOGY、APPLICATION、COMPANY"));
        assertTrue(prompt[0].contains("edge.type 只能是 CONTAINS_STAGE、FLOWS_TO、BELONGS_TO_STAGE、"
                + "INPUT_TO、PRODUCES、PARTICIPATES_IN、SUPPLIES_TO、DEPENDS_ON、ENABLES、USED_IN、"
                + "SUBSTITUTES、COMPETES_WITH"));
        assertTrue(prompt[0].contains("strength 只能是 PRIMARY、SECONDARY"));
        assertTrue(prompt[0].contains("nature 只能是 DISCLOSED、INDUSTRY_LOGIC、INFERRED"));
        assertTrue(prompt[0].contains("confidence 只能是 HIGH、MEDIUM、LOW"));
        assertTrue(prompt[0].contains("景气度、供需状态、核心指标、产业瓶颈"));
        assertTrue(prompt[0].contains("公司竞争格局"));
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
                + "\"limitations\":\"企业供销关系需以公告为准\","
                + "\"researchContent\":" + researchContent() + ",\"nodes\":["
                + node("stage:chip", "芯片", 1) + ","
                + node("stage:server", "服务器", 2) + ","
                + node("stage:datacenter", "数据中心", 3) + "],\"edges\":["
                + edge("flow:1", "stage:chip", "stage:server") + ","
                + edge("flow:2", "stage:server", "stage:datacenter") + "]}";
    }

    private String jsonWithUndisclosedSupplyRelationship() {
        return "{\"summary\":\"AI 算力由芯片、服务器和数据中心构成\","
                + "\"limitations\":\"企业供销关系需以公告为准\","
                + "\"researchContent\":" + researchContent().replace("\"companyProfiles\":[]",
                "\"companyProfiles\":[" + companyProfile("company:a") + ","
                        + companyProfile("company:b") + "]") + ",\"nodes\":["
                + node("stage:chip", "芯片", 1) + ","
                + node("stage:server", "服务器", 2) + ","
                + node("stage:datacenter", "数据中心", 3) + ","
                + company("company:a", "公司甲") + ","
                + company("company:b", "公司乙") + "],\"edges\":["
                + edge("flow:1", "stage:chip", "stage:server") + ","
                + edge("flow:2", "stage:server", "stage:datacenter") + ","
                + supplyEdge() + "]}";
    }

    private String node(String key, String name, int order) {
        return "{\"nodeKey\":\"" + key + "\",\"type\":\"STAGE\",\"name\":\"" + name
                + "\",\"description\":\"产业链环节\",\"stageOrder\":" + order
                + ",\"stockCode\":\"\",\"confidence\":\"HIGH\",\"evidenceRefs\":[\"E1\"]}";
    }

    private String company(String key, String name) {
        return "{\"nodeKey\":\"" + key + "\",\"type\":\"COMPANY\",\"name\":\"" + name
                + "\",\"description\":\"产业链参与企业\",\"stageOrder\":null,"
                + "\"stockCode\":\"\",\"confidence\":\"MEDIUM\",\"evidenceRefs\":[\"E1\"]}";
    }

    private String edge(String key, String source, String target) {
        return "{\"edgeKey\":\"" + key + "\",\"sourceKey\":\"" + source
                + "\",\"targetKey\":\"" + target + "\",\"type\":\"FLOWS_TO\","
                + "\"nature\":\"INDUSTRY_LOGIC\",\"description\":\"价值流转\","
                + "\"confidence\":\"HIGH\",\"strength\":\"PRIMARY\","
                + "\"directionNote\":\"算力芯片进入服务器集成\",\"evidenceRefs\":[\"E1\"]}";
    }

    private String supplyEdge() {
        return "{\"edgeKey\":\"supply:a-b\",\"sourceKey\":\"company:a\","
                + "\"targetKey\":\"company:b\",\"type\":\"SUPPLIES_TO\","
                + "\"nature\":\"INFERRED\",\"description\":\"可能存在供货关系\","
                + "\"confidence\":\"LOW\",\"strength\":\"SECONDARY\","
                + "\"directionNote\":\"待公开资料确认\",\"evidenceRefs\":[\"E1\"]}";
    }

    private String researchContent() {
        return "{\"overview\":{\"lifecycle\":\"GROWTH\",\"prosperity\":\"RISING\","
                + "\"supplyDemand\":\"STRUCTURAL\",\"cycleType\":\"资本开支驱动的成长周期\","
                + "\"demandDrivers\":[\"云厂商资本开支\"],\"supplyDrivers\":[\"先进制程产能\"],"
                + "\"keyVariables\":[\"GPU交付周期\"],\"bottlenecks\":[\"先进算力芯片\"],"
                + "\"overcapacityRisks\":[\"低端服务器组装\"],\"trendTags\":[\"算力升级\"]},"
                + "\"stageProfiles\":[" + stageProfile("stage:chip") + ","
                + stageProfile("stage:server") + "," + stageProfile("stage:datacenter") + "],"
                + "\"companyProfiles\":[],\"nodeProfiles\":[{\"nodeKey\":\"stage:chip\","
                + "\"definition\":\"提供通用与专用计算能力的芯片环节\","
                + "\"function\":\"算力芯片决定整机性能上限\",\"inputs\":[\"晶圆制造\"],"
                + "\"outputs\":[\"加速芯片\"],\"costDrivers\":[\"研发投入\"],"
                + "\"valueDrivers\":[\"性能与生态\"],\"barriers\":[\"软硬件协同\"],"
                + "\"coreMetrics\":[\"算力性能\"],\"risks\":[\"出口限制\"],"
                + "\"maturity\":\"SCALING\",\"valueLevel\":\"HIGH\","
                + "\"bottleneckLevel\":\"HIGH\",\"localizationLevel\":\"LOW\"}]}";
    }

    private String stageProfile(String nodeKey) {
        return "{\"nodeKey\":\"" + nodeKey + "\",\"roleSummary\":\"提供计算核心\","
                + "\"businessModel\":\"设备销售与服务\",\"costStructure\":\"研发和晶圆制造\","
                + "\"valueCapture\":\"性能溢价\",\"bottleneck\":\"先进制程与封装\","
                + "\"prosperity\":\"RISING\",\"supplyDemand\":\"TIGHT\",\"lifecycle\":\"GROWTH\","
                + "\"profitDrivers\":[\"产品升级\"],\"barriers\":[\"软硬件生态\"],"
                + "\"coreMetrics\":[\"出货量\"],\"risks\":[\"出口限制\"],"
                + "\"keyVariables\":[\"良率\"],\"trendTags\":[\"高性能计算\"]}";
    }

    private String companyProfile(String nodeKey) {
        return "{\"nodeKey\":\"" + nodeKey + "\",\"industryPosition\":\"代表企业\","
                + "\"coreProducts\":[\"算力产品\"],\"downstreamMarkets\":[\"数据中心\"],"
                + "\"competitiveAdvantages\":[\"技术积累\"],\"keyVariables\":[\"产品迭代\"]}";
    }
}
