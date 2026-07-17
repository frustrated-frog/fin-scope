package com.finscope.service.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.financials.FinancialValueOrigin;
import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialEvidencePacketAssemblerTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final FinancialTrendEngine trends = new FinancialTrendEngine();
    private final FinancialEvidencePacketAssembler assembler =
            new FinancialEvidencePacketAssembler(json, trends, new FinancialEvidenceSelector());

    @Test
    void fingerprintAndEvidenceIdsIgnoreDatabaseIdsButTrackFinancialValues() {
        FinancialReportView first = view(9L, 101L, new BigDecimal("12.30"));
        FinancialReportView sameContent = view(99L, 999L, new BigDecimal("12.30"));
        FinancialReportView changed = view(9L, 101L, new BigDecimal("13.30"));

        FinancialEvidencePacket firstPacket = assembler.assemble(first, Collections.emptyList());
        FinancialEvidencePacket samePacket = assembler.assemble(sameContent, Collections.emptyList());
        FinancialEvidencePacket changedPacket = assembler.assemble(changed, Collections.emptyList());

        assertEquals(firstPacket.getInputHash(), samePacket.getInputHash());
        assertEquals(firstPacket.getEvidence().stream().map(FinancialEvidence::getId).collect(Collectors.toList()),
                samePacket.getEvidence().stream().map(FinancialEvidence::getId).collect(Collectors.toList()));
        assertNotEquals(firstPacket.getInputHash(), changedPacket.getInputHash());
        assertTrue(firstPacket.getEvidenceIndex().containsKey("M_REVENUE_YOY"));
        assertTrue(firstPacket.getEvidenceIndex().containsKey("L_INCOME_REVENUE_2025_ANNUAL_CURRENT_YTD"));
        assertTrue(firstPacket.getAllowedNumbers().contains("12.30"));
        assertFalse(firstPacket.getModelPayloadJson().isEmpty());
        assertTrue(firstPacket.getModelPayloadJson().length() < firstPacket.getPayloadJson().length());
        assertTrue(firstPacket.getModelEvidence().size() <= FinancialEvidenceSelector.MAX_EVIDENCE);
        try {
            assertEquals(firstPacket.getModelEvidence().size(),
                    json.readTree(firstPacket.getPayloadJson()).path("modelEvidenceCount").asInt());
            assertEquals(firstPacket.getModelPayloadJson().getBytes(StandardCharsets.UTF_8).length,
                    json.readTree(firstPacket.getPayloadJson()).path("modelInputBytes").asInt());
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    @Test
    void allowsSafeDisplayRoundingAndChineseCurrencyUnitsWithoutAllowingInventedNumbers() {
        FinancialReportView source = view(9L, 101L, new BigDecimal("11.119003"));
        source.getStatements().get(FinancialStatementType.INCOME).get(0)
                .setNormalizedValue(new BigDecimal("2202460295.29"));
        source.getStatements().get(FinancialStatementType.INCOME).add(
                line(102L, "OPERATING_CASH_FLOW", "CURRENT_YTD", "-277749501.36"));
        source.getStatements().get(FinancialStatementType.INCOME).add(
                line(103L, "SHORT_TERM_BORROWINGS", "CURRENT_YTD", "4049519274.67"));

        FinancialEvidencePacket packet = assembler.assemble(source, Collections.emptyList());

        assertTrue(packet.getAllowedNumbers().contains("11.12"));
        assertTrue(packet.getAllowedNumbers().contains("22.02"));
        assertTrue(packet.getAllowedNumbers().contains("2.78"));
        assertTrue(packet.getAllowedNumbers().contains("40.49"));
        assertFalse(packet.getAllowedNumbers().contains("9.99"));
    }

    @Test
    void trendEngineKeepsAnnualAndSingleQuarterSeriesSeparate() {
        FinancialReportView annual2025 = view(9L, 101L, new BigDecimal("12.30"));
        FinancialReportView annual2024 = view(8L, 100L, new BigDecimal("10.00"));
        annual2024.getReport().setPeriodEnd(LocalDate.of(2024, 12, 31));
        FinancialReportView q3 = view(7L, 90L, new BigDecimal("8.00"));
        q3.getReport().setPeriodEnd(LocalDate.of(2025, 9, 30));
        q3.getReport().setReportType(FinancialReportType.Q3);
        q3.getStatements().get(FinancialStatementType.INCOME).add(
                line(91L, "REVENUE", "CURRENT_QUARTER", "300"));

        List<FinancialEvidence> result = trends.build(
                Arrays.asList(annual2025, annual2024, q3));

        FinancialEvidence annualTrend = result.stream()
                .filter(value -> "T_REVENUE_YOY_ANNUAL".equals(value.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        FinancialEvidence quarterTrend = result.stream()
                .filter(value -> "T_REVENUE_QUARTER".equals(value.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(annualTrend.getDetail().contains("2024-12-31"));
        assertFalse(annualTrend.getDetail().contains("2025-09-30"));
        assertTrue(quarterTrend.getDetail().contains("2025-09-30"));
        assertFalse(quarterTrend.getDetail().contains("CURRENT_YTD"));
    }

    private FinancialReportView view(Long reportId, Long lineId, BigDecimal metricValue) {
        FinancialReport report = new FinancialReport();
        report.setId(reportId);
        report.setInstrumentId(7L);
        report.setPeriodEnd(LocalDate.of(2025, 12, 31));
        report.setReportType(FinancialReportType.ANNUAL);
        report.setScope("CONSOLIDATED");
        report.setCurrency("CNY");
        report.setQualityStatus(FinancialQualityStatus.FRESH);
        report.setSourceCode("TEST");
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        instrument.setCode("600519");
        instrument.setMarket("SH");
        instrument.setName("贵州茅台");
        FinancialMetric metric = new FinancialMetric();
        metric.setId(lineId);
        metric.setReportId(reportId);
        metric.setMetricCode("REVENUE_YOY");
        metric.setLabel("营业收入同比");
        metric.setValue(metricValue);
        metric.setUnit("%");
        metric.setFormulaVersion("financial-metrics-v2");
        metric.setQualityStatus(FinancialQualityStatus.FRESH);
        EnumMap<FinancialStatementType, List<FinancialLineItem>> statements =
                new EnumMap<FinancialStatementType, List<FinancialLineItem>>(FinancialStatementType.class);
        for (FinancialStatementType type : FinancialStatementType.values()) {
            statements.put(type, new java.util.ArrayList<FinancialLineItem>());
        }
        statements.get(FinancialStatementType.INCOME).add(
                line(lineId, "REVENUE", "CURRENT_YTD", "1200"));
        FinancialReportView view = new FinancialReportView();
        view.setInstrument(instrument);
        view.setReport(report);
        view.setStatements(statements);
        view.setMetrics(Collections.singletonList(metric));
        view.setDataGaps(Collections.singletonList("缺少经营现金流比较期"));
        return view;
    }

    private FinancialLineItem line(Long id, String code, String periodRole, String amount) {
        FinancialLineItem value = new FinancialLineItem();
        value.setId(id);
        value.setReportId(9L);
        value.setStatementType(FinancialStatementType.INCOME);
        value.setSourceLabel("营业收入");
        value.setConceptCode(code);
        value.setPeriodRole(periodRole);
        value.setNormalizedValue(new BigDecimal(amount));
        value.setCurrency("CNY");
        value.setValueOrigin(FinancialValueOrigin.REPORTED);
        value.setSourceCode("TEST");
        value.setDisplayOrder(1);
        value.setQualityStatus(FinancialQualityStatus.FRESH);
        return value;
    }
}
