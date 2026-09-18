package com.finscope.service.attribution;

import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import com.finscope.dao.attribution.AttributionResearchRunRepository;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionDriver;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.attribution.AttributionResearchRun;
import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeast;

class AttributionHarnessTest {
    @Test
    void persistsPlanWithoutPaddingDriversFromUnrelatedEvidence() {
        AttributionResearchRunRepository runRepository = mock(AttributionResearchRunRepository.class);
        AttributionAgent agent = mock(AttributionAgent.class);
        when(runRepository.createRun(any(AttributionResearchRun.class))).thenAnswer(invocation -> {
            AttributionResearchRun run = invocation.getArgument(0);
            run.setId(9L);
            return run;
        });
        when(runRepository.findByReportId(88L)).thenAnswer(invocation -> {
            AttributionResearchRun run = new AttributionResearchRun();
            run.setId(9L);
            run.setStatus("RUNNING");
            return Optional.of(run);
        });
        doAnswer(invocation -> {
            AttributionReport report = invocation.getArgument(0);
            report.setEvidences(Arrays.asList(
                    evidence("公司公告催化", "https://www.sse.com.cn/1", "T1", "DIRECT"),
                    evidence("行业景气改善", "https://www.yicai.com/2", "T2", "INDIRECT"),
                    evidence("政策预期升温", "https://www.gov.cn/3", "T1", "INDIRECT"),
                    evidence("板块资金回流", "https://www.eastmoney.com/4", "T2", "INDIRECT")));
            AttributionDriver modelDriver = new AttributionDriver();
            modelDriver.setClaim("公司公告催化");
            modelDriver.setConfidence("HIGH");
            modelDriver.setFacts(Arrays.asList("模型提取的公告事实"));
            modelDriver.setEvidenceUrls(Arrays.asList("https://www.sse.com.cn/1"));
            report.setDrivers(new ArrayList<>(Arrays.asList(modelDriver)));
            report.setUncertainties(Arrays.asList("模型识别的不确定性"));
            return null;
        }).when(agent).researchWithPlan(any(), any(), any(), any(), any(), any(), any());

        AttributionHarness harness = createHarness(runRepository, agent);
        AttributionReport report = new AttributionReport();
        report.setId(88L);
        report.setReportDate(LocalDate.parse("2026-09-18"));
        Instrument instrument = new Instrument();
        instrument.setCode("600519");
        instrument.setName("贵州茅台");
        instrument.setType("STOCK");

        AttributionProgressPublisher publisher = mock(AttributionProgressPublisher.class);
        harness.research(report, instrument, 3.1D, "task", publisher);
        harness.markPersisted(report);

        assertEquals(1, report.getDrivers().size());
        assertNotNull(report.getPrimaryDriver());
        assertTrue(report.getPrimaryDriver().getTransmissionPath().length() > 0);
        assertTrue(report.getUncertainties().size() > 0);
        assertTrue(report.getUncertainties().contains("模型识别的不确定性"));
        assertTrue(report.getPrimaryDriver().getFacts().contains("模型提取的公告事实"));
        assertEquals("MID", report.getPrimaryDriver().getConfidence());
        verify(runRepository, atLeast(5)).saveStep(any());
        verify(runRepository).updateRun(9L, "COMPLETED", null, "SUCCESS");
    }

    @Test
    void persistsNewResearchTracksAsPlannedBeforeTheAgentStartsWork() {
        AttributionResearchRunRepository runRepository = mock(AttributionResearchRunRepository.class);
        AttributionAgent agent = mock(AttributionAgent.class);
        when(runRepository.createRun(any(AttributionResearchRun.class))).thenAnswer(invocation -> {
            AttributionResearchRun run = invocation.getArgument(0);
            run.setId(10L);
            return run;
        });
        when(agent.researchWithPlan(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AttributionResearchExecution());

        AttributionHarness harness = createHarness(runRepository, agent);
        AttributionReport report = new AttributionReport();
        report.setId(89L);
        Instrument instrument = new Instrument();
        instrument.setCode("600519");
        instrument.setName("贵州茅台");
        instrument.setType("STOCK");

        harness.research(report, instrument, 1.2D, "task", mock(AttributionProgressPublisher.class));

        ArgumentCaptor<com.finscope.domain.attribution.AttributionResearchStep> steps =
                ArgumentCaptor.forClass(com.finscope.domain.attribution.AttributionResearchStep.class);
        verify(runRepository, atLeast(5)).saveStep(steps.capture());
        assertTrue(steps.getAllValues().subList(0, 5).stream()
                .allMatch(step -> "PLANNED".equals(step.getStatus())));
    }

    @Test
    void neverAttachesPositionalEvidenceToAnUnlinkedDriver() {
        AttributionResearchRunRepository repository = mock(AttributionResearchRunRepository.class);
        when(repository.createRun(any())).thenAnswer(invocation -> {
            AttributionResearchRun run = invocation.getArgument(0);
            run.setId(11L);
            return run;
        });
        AttributionReport report = new AttributionReport();
        report.setReportDate(LocalDate.parse("2026-09-18"));
        AttributionDriver driver = new AttributionDriver();
        driver.setClaim("模型未证明的解释");
        driver.setConfidence("HIGH");
        driver.setFacts(Arrays.asList("未经证实的事实"));
        driver.setEvidenceUrls(Arrays.asList("https://invented.com/notice"));
        report.setDrivers(Arrays.asList(driver));
        report.setEvidences(Arrays.asList(evidence("不相关公告", "https://real.com/notice", "T1", "DIRECT")));
        Instrument instrument = new Instrument();
        instrument.setCode("600519");
        instrument.setType("STOCK");

        createHarness(repository, mock(AttributionAgent.class)).research(report, instrument, 1D,
                "task", mock(AttributionProgressPublisher.class));

        assertEquals(1, report.getDrivers().size());
        assertTrue(driver.getFacts().isEmpty());
        assertTrue(driver.getEvidenceUrls().isEmpty());
        assertEquals("LOW", driver.getConfidence());
        assertEquals("LOW", driver.getExplanatoryPower());
    }

    private AttributionHarness createHarness(AttributionResearchRunRepository repository, AttributionAgent agent) {
        AttributionHarness harness = new AttributionHarness();
        ReflectionTestUtils.setField(harness, "planFactory", new AttributionResearchPlanFactory());
        ReflectionTestUtils.setField(harness, "planValidator", new AttributionPlanValidator());
        ReflectionTestUtils.setField(harness, "evidenceGate", new AttributionEvidenceGate());
        ReflectionTestUtils.setField(harness, "runRepository", repository);
        ReflectionTestUtils.setField(harness, "agent", agent);
        return harness;
    }

    private AttributionEvidence evidence(String title, String url, String tier, String directness) {
        AttributionEvidence evidence = new AttributionEvidence();
        evidence.setTitle(title);
        evidence.setSnippet(title + "，可能改变市场对盈利与风险的预期。");
        evidence.setUrl(url);
        evidence.setSourceTier(tier);
        evidence.setDirectness(directness);
        evidence.setRelevance(80);
        evidence.setPublishedAt("2026-09-18");
        evidence.setStance("SUPPORT");
        return evidence;
    }
}
