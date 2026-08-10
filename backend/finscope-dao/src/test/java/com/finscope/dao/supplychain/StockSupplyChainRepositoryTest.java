package com.finscope.dao.supplychain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.supplychain.StockSupplyChainEvidence;
import com.finscope.domain.supplychain.StockSupplyChainNode;
import com.finscope.domain.supplychain.StockSupplyChainRefreshRun;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockSupplyChainRepositoryTest {
    private StockSupplyChainRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-supply-chain-test");
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        jdbc.update("INSERT INTO instrument(code,type,name,market,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                "688012", "STOCK", "中微公司", "SH", "2026-08-11T00:00:00", "2026-08-11T00:00:00");
        repository = new StockSupplyChainRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void replacesSnapshotOnlyWhenARefreshCompletesSuccessfully() {
        StockSupplyChainRefreshRun run = repository.createRun(1L);
        StockSupplyChainSnapshot first = snapshot("刻蚀设备连接上游零部件与下游晶圆厂");

        repository.replaceSnapshotAndComplete(first, run);

        StockSupplyChainSnapshot restored = repository.findSnapshot(1L).orElseThrow(AssertionError::new);
        assertNotNull(restored.getId());
        assertEquals("中微公司", restored.getCompanyName());
        assertEquals(3, restored.getNodes().size());
        assertEquals("UPSTREAM", restored.getNodes().get(0).getLayer());
        assertEquals(Arrays.asList("E1"), restored.getNodes().get(0).getEvidenceRefs());
        assertEquals("年度报告", restored.getEvidence().get(0).getTitle());
        assertEquals("READY", repository.latestRun(1L).orElseThrow(AssertionError::new).getStatus());

        StockSupplyChainRefreshRun failed = repository.createRun(1L);
        failed.setStatus("FAILED");
        failed.setStage("COMPLETED");
        failed.setErrorCode("SEARCH_FAILED");
        failed.setMessage("公开资料搜索失败，已保留原产业链快照");
        failed.setRetryable(true);
        repository.updateRun(failed);

        StockSupplyChainSnapshot afterFailure = repository.findSnapshot(1L).orElseThrow(AssertionError::new);
        assertEquals(first.getSummary(), afterFailure.getSummary());
        assertEquals("FAILED", repository.latestRun(1L).orElseThrow(AssertionError::new).getStatus());
        assertTrue(repository.activeRun(1L).isEmpty());
    }

    private StockSupplyChainSnapshot snapshot(String summary) {
        StockSupplyChainSnapshot value = new StockSupplyChainSnapshot();
        value.setInstrumentId(1L);
        value.setCompanyCode("688012");
        value.setCompanyName("中微公司");
        value.setSummary(summary);
        value.setPosition("半导体前道设备");
        value.setLimitations("客户名称以公开披露为准");
        value.setSchemaVersion("SUPPLY_CHAIN_V1");
        value.setModel("test-model");
        value.setEvidenceAsOf(LocalDate.of(2026, 8, 10));
        value.setGeneratedAt(LocalDateTime.of(2026, 8, 11, 9, 0));
        value.setNodes(Arrays.asList(
                node("UPSTREAM", "真空与射频零部件", "SUPPLY", "E1"),
                node("COMPANY", "刻蚀设备", "CORE_BUSINESS", "E1"),
                node("DOWNSTREAM", "晶圆制造", "CUSTOMER_INDUSTRY", "E1")));
        StockSupplyChainEvidence evidence = new StockSupplyChainEvidence();
        evidence.setEvidenceCode("E1");
        evidence.setTitle("年度报告");
        evidence.setUrl("https://example.com/report");
        evidence.setSource("example.com");
        evidence.setSourceTier("T1");
        evidence.setPublishedAt("2026-03-31");
        evidence.setExcerpt("公司披露主营刻蚀设备并服务晶圆制造客户");
        value.setEvidence(Arrays.asList(evidence));
        return value;
    }

    private StockSupplyChainNode node(String layer, String name, String relationType, String evidenceRef) {
        StockSupplyChainNode value = new StockSupplyChainNode();
        value.setLayer(layer);
        value.setName(name);
        value.setRelationType(relationType);
        value.setDescription(name);
        value.setConfidence("HIGH");
        value.setEvidenceRefs(Arrays.asList(evidenceRef));
        return value;
    }
}
