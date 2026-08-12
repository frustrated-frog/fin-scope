package com.finscope.service.industrychain;

import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.industrychain.IndustryChainResearchContent;
import com.finscope.domain.industrychain.IndustryChainStructureAssessment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustryChainStructureAssessorTest {
    private final IndustryChainStructureAssessor assessor = new IndustryChainStructureAssessor();

    @Test
    void marksMissingGraphAsBuilding() {
        IndustryChainStructureAssessment result = assessor.assess(null);

        assertEquals("BUILDING", result.getStatus());
        assertEquals(0, result.getScore());
        assertEquals("等待首版图谱生成", result.getGaps().get(0));
    }

    @Test
    void recommendsV2UpgradeEvenWhenStagesExist() {
        IndustryChainGraph graph = graph("INDUSTRY_CHAIN_V2", 3, 0);

        IndustryChainStructureAssessment result = assessor.assess(graph);

        assertEquals("UPGRADE_AVAILABLE", result.getStatus());
        assertTrue(result.getGaps().contains("升级为可展开的 V3 语义图谱"));
    }

    @Test
    void recommendsEnrichmentForSparseV3() {
        IndustryChainGraph graph = graph("INDUSTRY_CHAIN_V3", 3, 3);

        IndustryChainStructureAssessment result = assessor.assess(graph);

        assertEquals("ENRICHMENT_RECOMMENDED", result.getStatus());
        assertTrue(result.getScore() < 80);
        assertTrue(result.getGaps().stream().anyMatch(value -> value.contains("语义节点")));
    }

    @Test
    void marksCoveredAndProfiledV3AsComplete() {
        IndustryChainGraph graph = graph("INDUSTRY_CHAIN_V3", 3, 9);

        IndustryChainStructureAssessment result = assessor.assess(graph);

        assertEquals("COMPLETE", result.getStatus());
        assertTrue(result.getScore() >= 80);
        assertEquals(3, result.getCoveredStageCount());
        assertEquals(3, result.getStageCount());
        assertEquals(9, result.getSemanticNodeCount());
    }

    private IndustryChainGraph graph(String schemaVersion, int stageCount, int semanticCount) {
        IndustryChainGraph graph = new IndustryChainGraph();
        graph.setName("机器人");
        graph.setSchemaVersion(schemaVersion);
        graph.setNodes(new ArrayList<IndustryChainNode>());
        graph.setEdges(new ArrayList<IndustryChainEdge>());
        IndustryChainResearchContent content = new IndustryChainResearchContent();
        graph.setResearchContent(content);
        for (int index = 0; index < stageCount; index++) {
            IndustryChainNode stage = node("stage:" + index, "STAGE");
            stage.setStageOrder(index + 1);
            graph.getNodes().add(stage);
        }
        String[] types = {"MATERIAL", "EQUIPMENT", "COMPONENT", "PRODUCT", "TECHNOLOGY", "APPLICATION"};
        for (int index = 0; index < semanticCount; index++) {
            IndustryChainNode semantic = node("semantic:" + index, types[index % types.length]);
            graph.getNodes().add(semantic);
            IndustryChainEdge membership = new IndustryChainEdge();
            membership.setEdgeKey("edge:" + index);
            membership.setSourceKey(semantic.getNodeKey());
            membership.setTargetKey("stage:" + (index % stageCount));
            membership.setType("BELONGS_TO_STAGE");
            graph.getEdges().add(membership);
            IndustryChainResearchContent.NodeProfile profile = new IndustryChainResearchContent.NodeProfile();
            profile.setNodeKey(semantic.getNodeKey());
            content.getNodeProfiles().add(profile);
        }
        return graph;
    }

    private IndustryChainNode node(String key, String type) {
        IndustryChainNode node = new IndustryChainNode();
        node.setNodeKey(key);
        node.setType(type);
        node.setName(key);
        node.setConfidence("HIGH");
        node.setEvidenceRefs(Arrays.asList("E1"));
        return node;
    }
}
