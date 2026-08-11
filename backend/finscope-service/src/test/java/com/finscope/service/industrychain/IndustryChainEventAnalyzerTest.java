package com.finscope.service.industrychain;

import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IndustryChainEventAnalyzerTest {

    private final IndustryChainEventAnalyzer analyzer = new IndustryChainEventAnalyzer();

    @Test
    void mapsAConcreteRadarEventToItsDirectNodeAndPropagationPath() {
        Optional<IndustryChainEventImpact> result = analyzer.analyze(graph(),
                event("HBM 供给紧张推动价格上涨", "SK 海力士上调高带宽内存报价，AI 服务器成本承压"));

        IndustryChainEventImpact impact = result.orElseThrow(AssertionError::new);
        assertEquals("product:hbm", impact.getDirectNodeKey());
        assertEquals("POSITIVE", impact.getDirection());
        assertEquals("PRICE", impact.getMechanism());
        assertEquals("SHORT", impact.getHorizon());
        assertEquals("HIGH", impact.getConfidence());
        assertEquals("事件直接作用于“高带宽内存(HBM)”，可能通过价格机制沿产业链传导。", impact.getImpactSummary());
        assertEquals(Arrays.asList("product:hbm", "product:server", "stage:application"), impact.getPathNodeKeys());
    }

    @Test
    void skipsAnEventWithoutAConcreteIndustryChainMatch() {
        Optional<IndustryChainEventImpact> result = analyzer.analyze(graph(),
                event("欧洲央行维持利率不变", "通胀数据符合预期"));

        assertFalse(result.isPresent());
    }

    private IndustryChainGraph graph() {
        IndustryChainGraph graph = new IndustryChainGraph();
        graph.setName("AI 算力");
        graph.setNodes(Arrays.asList(
                node("product:hbm", "高带宽内存(HBM)", "HBM 存储芯片", 1),
                node("company:sk-hynix", "SK海力士", "HBM 供应商", 1),
                node("product:server", "AI服务器", "算力服务器", 2),
                node("stage:application", "云服务与模型应用", "下游应用", 3)));
        graph.setEdges(Arrays.asList(
                edge("product:hbm", "product:server"),
                edge("product:server", "stage:application")));
        return graph;
    }

    private IndustryChainNode node(String key, String name, String description, int order) {
        IndustryChainNode node = new IndustryChainNode();
        node.setNodeKey(key);
        node.setName(name);
        node.setDescription(description);
        node.setStageOrder(order);
        return node;
    }

    private IndustryChainEdge edge(String source, String target) {
        IndustryChainEdge edge = new IndustryChainEdge();
        edge.setSourceKey(source);
        edge.setTargetKey(target);
        return edge;
    }

    private RadarEvent event(String title, String summary) {
        RadarEvent event = new RadarEvent();
        event.setId(9L);
        event.setCanonicalTitle(title);
        event.setSummary(summary);
        return event;
    }
}
