package com.finscope.dao.industrychain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.industrychain.IndustryChain;
import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.industrychain.IndustryChainRevision;
import com.finscope.domain.industrychain.IndustryChainResearchContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustryChainRepositoryTest {
    private IndustryChainRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-industry-chain-test");
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        repository = new IndustryChainRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void publishesARevisionAndListsTheCurrentGraph() {
        IndustryChain chain = repository.createChain("AI 算力", "ai 算力");
        IndustryChainRevision revision = repository.createRevision(chain.getId());

        repository.publish(revision, graph("第一版产业图谱"));

        IndustryChain restored = repository.findChain(chain.getId()).orElseThrow(AssertionError::new);
        IndustryChainGraph restoredGraph = repository.findPublishedGraph(chain.getId()).orElseThrow(AssertionError::new);
        assertEquals(revision.getId(), restored.getCurrentRevisionId());
        assertEquals("第一版产业图谱", restoredGraph.getSummary());
        assertEquals(3, restoredGraph.getNodes().size());
        assertEquals(2, restoredGraph.getEdges().size());
        assertEquals("产业资料", restoredGraph.getEvidence().get(0).getTitle());
        assertEquals("GROWTH", restoredGraph.getResearchContent().getOverview().getLifecycle());
        assertEquals("设备销售与服务", restoredGraph.getResearchContent().getStageProfiles().get(0).getBusinessModel());
        assertEquals("SCALING", restoredGraph.getResearchContent().getNodeProfiles().get(0).getMaturity());
        assertEquals("PRIMARY", restoredGraph.getEdges().get(0).getStrength());
        assertEquals("资源进入核心制造", restoredGraph.getEdges().get(0).getDirectionNote());
        assertEquals(1, repository.listChains().size());
        assertEquals("READY", repository.latestRevision(chain.getId()).orElseThrow(AssertionError::new).getStatus());
    }

    @Test
    void failedLaterRevisionKeepsThePublishedGraph() {
        IndustryChain chain = repository.createChain("AI 算力", "ai 算力");
        IndustryChainRevision first = repository.createRevision(chain.getId());
        repository.publish(first, graph("稳定版本"));

        IndustryChainRevision failed = repository.createRevision(chain.getId());
        repository.fail(failed, "SYNTHESIS_FAILED", "产业链生成失败");

        assertEquals("稳定版本", repository.findPublishedGraph(chain.getId())
                .orElseThrow(AssertionError::new).getSummary());
        assertEquals("FAILED", repository.latestRevision(chain.getId())
                .orElseThrow(AssertionError::new).getStatus());
        assertFalse(repository.activeRevision(chain.getId()).isPresent());
        assertEquals(2, repository.findRevisions(chain.getId()).size());
    }

    @Test
    void findsChainByNormalizedNameAndTracksAnActiveRevision() {
        IndustryChain chain = repository.createChain("人形机器人", "人形机器人");
        IndustryChainRevision revision = repository.createRevision(chain.getId());

        assertEquals(chain.getId(), repository.findByNormalizedName("人形机器人")
                .orElseThrow(AssertionError::new).getId());
        assertTrue(repository.activeRevision(chain.getId()).isPresent());
        assertEquals(revision.getId(), repository.activeRevision(chain.getId())
                .orElseThrow(AssertionError::new).getId());
    }

    private IndustryChainGraph graph(String summary) {
        IndustryChainGraph graph = new IndustryChainGraph();
        graph.setName("AI 算力");
        graph.setSummary(summary);
        graph.setLimitations("仅覆盖公开资料");
        graph.setSchemaVersion("INDUSTRY_CHAIN_V1");
        graph.setModel("test-model");
        graph.setGeneratedAt(LocalDateTime.of(2026, 8, 11, 12, 0));
        graph.setEvidence(Collections.singletonList(evidence()));
        graph.setResearchContent(researchContent());
        graph.setNodes(Arrays.asList(
                node("stage:upstream", "上游资源", 1),
                node("stage:manufacturing", "核心制造", 2),
                node("stage:terminal", "终端应用", 3)));
        graph.setEdges(Arrays.asList(
                edge("edge:1", "stage:upstream", "stage:manufacturing"),
                edge("edge:2", "stage:manufacturing", "stage:terminal")));
        return graph;
    }

    private IndustryChainResearchContent researchContent() {
        IndustryChainResearchContent content = new IndustryChainResearchContent();
        IndustryChainResearchContent.Overview overview = new IndustryChainResearchContent.Overview();
        overview.setLifecycle("GROWTH");
        overview.setProsperity("RISING");
        overview.setSupplyDemand("STRUCTURAL");
        overview.setCycleType("成长与资本开支周期共振");
        content.setOverview(overview);
        IndustryChainResearchContent.StageProfile stage = new IndustryChainResearchContent.StageProfile();
        stage.setNodeKey("stage:upstream");
        stage.setBusinessModel("设备销售与服务");
        stage.setLifecycle("GROWTH");
        stage.setProsperity("RISING");
        stage.setSupplyDemand("TIGHT");
        content.setStageProfiles(Collections.singletonList(stage));
        IndustryChainResearchContent.NodeProfile profile = new IndustryChainResearchContent.NodeProfile();
        profile.setNodeKey("stage:upstream");
        profile.setDefinition("产业链所需的上游资源环节");
        profile.setFunction("提供核心制造所需输入");
        profile.setMaturity("SCALING");
        profile.setValueLevel("HIGH");
        profile.setBottleneckLevel("MEDIUM");
        profile.setLocalizationLevel("HIGH");
        content.setNodeProfiles(Collections.singletonList(profile));
        return content;
    }

    private IndustryChainNode node(String key, String name, int order) {
        IndustryChainNode node = new IndustryChainNode();
        node.setNodeKey(key);
        node.setType("STAGE");
        node.setName(name);
        node.setDescription(name);
        node.setStageOrder(order);
        node.setConfidence("HIGH");
        node.setEvidenceRefs(Collections.singletonList("E1"));
        return node;
    }

    private IndustryChainEdge edge(String key, String source, String target) {
        IndustryChainEdge edge = new IndustryChainEdge();
        edge.setEdgeKey(key);
        edge.setSourceKey(source);
        edge.setTargetKey(target);
        edge.setType("FLOWS_TO");
        edge.setNature("INDUSTRY_LOGIC");
        edge.setDescription("产业流向");
        edge.setConfidence("HIGH");
        edge.setStrength("PRIMARY");
        edge.setDirectionNote("资源进入核心制造");
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
        evidence.setPublishedAt("2026-08-10");
        evidence.setExcerpt("公开资料");
        return evidence;
    }
}
