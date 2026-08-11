package com.finscope.service.industrychain;

import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.industrychain.IndustryChainResearchContent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndustryChainGraphValidatorTest {
    private final IndustryChainGraphValidator validator = new IndustryChainGraphValidator();

    @Test
    void acceptsEvidenceBackedThreeStagePath() {
        assertDoesNotThrow(() -> validator.validate(validGraph()));
    }

    @Test
    void rejectsSelfLoop() {
        IndustryChainGraph graph = validGraph();
        graph.getEdges().get(0).setTargetKey("stage:upstream");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(graph));
    }

    @Test
    void rejectsUnknownEndpointAndEvidence() {
        IndustryChainGraph invalidEndpoint = validGraph();
        invalidEndpoint.getEdges().get(0).setTargetKey("stage:missing");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidEndpoint));

        IndustryChainGraph invalidEvidence = validGraph();
        invalidEvidence.getEdges().get(0).setEvidenceRefs(Collections.singletonList("E404"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidEvidence));
    }

    @Test
    void rejectsCyclicStageFlow() {
        IndustryChainGraph graph = validGraph();
        graph.getEdges().add(edge("edge:cycle", "stage:terminal", "stage:upstream", "FLOWS_TO", "INDUSTRY_LOGIC"));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(graph));
    }

    @Test
    void rejectsInferredCompanySupplyRelationship() {
        IndustryChainGraph graph = validGraph();
        graph.getNodes().add(node("company:a", "COMPANY", "企业甲", null));
        graph.getNodes().add(node("company:b", "COMPANY", "企业乙", null));
        graph.getEdges().add(edge("edge:supply", "company:a", "company:b", "SUPPLIES_TO", "INFERRED"));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(graph));
    }

    @Test
    void identifiesTheInvalidNodeAndField() {
        IndustryChainGraph graph = validGraph();
        graph.getNodes().get(0).setType("SEGMENT");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> validator.validate(graph));

        assertEquals("产业链节点类型无效：nodeKey=stage:upstream, type=SEGMENT", error.getMessage());
    }

    @Test
    void validatesResearchProfilesAgainstGraphNodeTypes() {
        IndustryChainGraph valid = validGraph();
        valid.setResearchContent(researchContent("stage:upstream", null));
        assertDoesNotThrow(() -> validator.validate(valid));

        IndustryChainGraph unknownStage = validGraph();
        unknownStage.setResearchContent(researchContent("stage:missing", null));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(unknownStage));

        IndustryChainGraph wrongCompanyType = validGraph();
        wrongCompanyType.setResearchContent(researchContent("stage:upstream", "stage:terminal"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(wrongCompanyType));
    }

    private IndustryChainGraph validGraph() {
        IndustryChainGraph graph = new IndustryChainGraph();
        graph.setName("AI 算力");
        graph.setSummary("从上游材料到终端应用的算力产业链");
        graph.setLimitations("企业关系仅覆盖公开披露部分");
        graph.setSchemaVersion("INDUSTRY_CHAIN_V1");
        graph.setEvidence(Collections.singletonList(evidence()));
        graph.setNodes(Arrays.asList(
                node("stage:upstream", "STAGE", "上游资源", 1),
                node("stage:manufacturing", "STAGE", "核心制造", 2),
                node("stage:terminal", "STAGE", "终端应用", 3)));
        graph.setEdges(Arrays.asList(
                edge("edge:1", "stage:upstream", "stage:manufacturing", "FLOWS_TO", "INDUSTRY_LOGIC"),
                edge("edge:2", "stage:manufacturing", "stage:terminal", "FLOWS_TO", "INDUSTRY_LOGIC")));
        return graph;
    }

    private IndustryChainNode node(String key, String type, String name, Integer stageOrder) {
        IndustryChainNode node = new IndustryChainNode();
        node.setNodeKey(key);
        node.setType(type);
        node.setName(name);
        node.setDescription(name + "说明");
        node.setStageOrder(stageOrder);
        node.setConfidence("HIGH");
        node.setEvidenceRefs(Collections.singletonList("E1"));
        return node;
    }

    private IndustryChainEdge edge(String key, String source, String target, String type, String nature) {
        IndustryChainEdge edge = new IndustryChainEdge();
        edge.setEdgeKey(key);
        edge.setSourceKey(source);
        edge.setTargetKey(target);
        edge.setType(type);
        edge.setNature(nature);
        edge.setDescription("关系说明");
        edge.setConfidence("HIGH");
        edge.setEvidenceRefs(Collections.singletonList("E1"));
        return edge;
    }

    private IndustryChainEvidence evidence() {
        IndustryChainEvidence evidence = new IndustryChainEvidence();
        evidence.setEvidenceCode("E1");
        evidence.setTitle("产业资料");
        evidence.setUrl("https://example.com/report");
        evidence.setSource("example.com");
        evidence.setSourceTier("T2");
        evidence.setExcerpt("公开资料内容");
        return evidence;
    }

    private IndustryChainResearchContent researchContent(String stageKey, String companyKey) {
        IndustryChainResearchContent content = new IndustryChainResearchContent();
        IndustryChainResearchContent.Overview overview = new IndustryChainResearchContent.Overview();
        overview.setLifecycle("GROWTH");
        overview.setProsperity("RISING");
        overview.setSupplyDemand("STRUCTURAL");
        content.setOverview(overview);

        IndustryChainResearchContent.StageProfile stage = new IndustryChainResearchContent.StageProfile();
        stage.setNodeKey(stageKey);
        stage.setLifecycle("GROWTH");
        stage.setProsperity("RISING");
        stage.setSupplyDemand("TIGHT");
        content.setStageProfiles(Collections.singletonList(stage));
        if (companyKey != null) {
            IndustryChainResearchContent.CompanyProfile company = new IndustryChainResearchContent.CompanyProfile();
            company.setNodeKey(companyKey);
            content.setCompanyProfiles(Collections.singletonList(company));
        }
        return content;
    }
}
