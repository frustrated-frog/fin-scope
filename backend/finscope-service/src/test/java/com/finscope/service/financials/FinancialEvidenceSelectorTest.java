package com.finscope.service.financials;

import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialReportType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialEvidenceSelectorTest {
    private final FinancialEvidenceSelector selector = new FinancialEvidenceSelector();

    @Test
    void keepsHighValueEvidenceAndDropsTechnicalOrDuplicateQ1Lines() {
        List<FinancialEvidence> input = Arrays.asList(
                evidence("M_REVENUE_YOY", "METRIC", "11.12"),
                evidence("F_PROFIT_CASH_DIVERGENCE", "FINDING", "HIGH"),
                evidence("G_MISSING_CAPEX", "DATA_GAP", null),
                evidence("T_REVENUE_QUARTER", "TREND", "2025-03-31=100;2026-03-31=120"),
                evidence("T_UNIMPORTANT_FIELD_QUARTER", "TREND", "2025-03-31=1;2026-03-31=2"),
                evidence("L_INCOME_REVENUE_2026_Q1_CURRENT_YTD", "LINE_ITEM", "120"),
                evidence("L_INCOME_REVENUE_2026_Q1_CURRENT_QUARTER", "LINE_ITEM", "120"),
                evidence("L_BALANCE_SHEET_TOTAL_ASSETS_2026_Q1_CURRENT_PERIOD_END", "LINE_ITEM", "300"),
                evidence("L_BALANCE_SHEET_ASSET_BALANCE_2026_Q1_CURRENT_PERIOD_END", "LINE_ITEM", "0"),
                evidence("L_INCOME_UNIMPORTANT_FIELD_2026_Q1_CURRENT_YTD", "LINE_ITEM", "9"));

        List<String> ids = selector.select(input, FinancialReportType.Q1).stream()
                .map(FinancialEvidence::getId).collect(Collectors.toList());

        assertTrue(ids.contains("M_REVENUE_YOY"));
        assertTrue(ids.contains("F_PROFIT_CASH_DIVERGENCE"));
        assertTrue(ids.contains("G_MISSING_CAPEX"));
        assertTrue(ids.contains("T_REVENUE_QUARTER"));
        assertTrue(ids.contains("L_INCOME_REVENUE_2026_Q1_CURRENT_QUARTER"));
        assertTrue(ids.contains("L_BALANCE_SHEET_TOTAL_ASSETS_2026_Q1_CURRENT_PERIOD_END"));
        assertFalse(ids.contains("T_UNIMPORTANT_FIELD_QUARTER"));
        assertFalse(ids.contains("L_INCOME_REVENUE_2026_Q1_CURRENT_YTD"));
        assertFalse(ids.contains("L_BALANCE_SHEET_ASSET_BALANCE_2026_Q1_CURRENT_PERIOD_END"));
        assertFalse(ids.contains("L_INCOME_UNIMPORTANT_FIELD_2026_Q1_CURRENT_YTD"));
    }

    @Test
    void appliesAHardStableEvidenceLimit() {
        List<FinancialEvidence> input = new ArrayList<FinancialEvidence>();
        for (int index = 120; index >= 0; index--) {
            input.add(evidence("M_METRIC_" + index, "METRIC", String.valueOf(index)));
        }

        List<FinancialEvidence> selected = selector.select(input, FinancialReportType.ANNUAL);

        assertEquals(FinancialEvidenceSelector.MAX_EVIDENCE, selected.size());
        List<String> ids = selected.stream().map(FinancialEvidence::getId).collect(Collectors.toList());
        List<String> sorted = new ArrayList<String>(ids);
        sorted.sort(String::compareTo);
        assertEquals(sorted, ids);
    }

    @Test
    void reservesCompactPacketCapacityForAllThreeStatements() {
        List<FinancialEvidence> input = new ArrayList<FinancialEvidence>();
        for (int index = 0; index < 50; index++) {
            input.add(evidence("M_METRIC_" + index, "METRIC", String.valueOf(index)));
        }
        for (int index = 0; index < 20; index++) {
            input.add(evidence("L_INCOME_REVENUE_2026_Q1_ROLE_" + index,
                    "LINE_ITEM", String.valueOf(index)));
            input.add(evidence("L_BALANCE_SHEET_TOTAL_ASSETS_2026_Q1_ROLE_" + index,
                    "LINE_ITEM", String.valueOf(index)));
            input.add(evidence("L_CASH_FLOW_OPERATING_CASH_FLOW_2026_Q1_ROLE_" + index,
                    "LINE_ITEM", String.valueOf(index)));
        }

        List<String> ids = selector.select(input, FinancialReportType.Q1).stream()
                .map(FinancialEvidence::getId).collect(Collectors.toList());

        assertTrue(ids.stream().anyMatch(id -> id.startsWith("L_INCOME_")));
        assertTrue(ids.stream().anyMatch(id -> id.startsWith("L_BALANCE_SHEET_")));
        assertTrue(ids.stream().anyMatch(id -> id.startsWith("L_CASH_FLOW_")));
    }

    private FinancialEvidence evidence(String id, String type, String value) {
        FinancialEvidence evidence = new FinancialEvidence();
        evidence.setId(id);
        evidence.setType(type);
        evidence.setLabel(id);
        evidence.setValue(value);
        evidence.setDetail(value);
        evidence.setPeriod("2026-03-31");
        return evidence;
    }
}
