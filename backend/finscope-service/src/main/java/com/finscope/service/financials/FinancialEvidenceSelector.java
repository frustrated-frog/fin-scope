package com.finscope.service.financials;

import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.common.enums.financials.FinancialReportType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class FinancialEvidenceSelector {
    public static final int MAX_EVIDENCE = 72;
    public static final String SELECTOR_VERSION = "financial-evidence-selector-v2";

    private static final Set<String> CORE_CONCEPTS = new LinkedHashSet<String>(Arrays.asList(
            "REVENUE", "OPERATING_REVENUE", "OPERATING_COST", "TOTAL_OPERATING_COST",
            "NET_PROFIT", "NET_PROFIT_PARENT", "OPERATING_PROFIT", "TOTAL_PROFIT",
            "SELLING_EXPENSE", "ADMIN_EXPENSE", "RND_EXPENSE", "FINANCE_EXPENSE",
            "ASSET_IMPAIRMENT", "CREDIT_IMPAIRMENT", "OPERATING_CASH_FLOW",
            "OPERATING_CASH_INFLOW", "CAPITAL_EXPENDITURE", "INVESTING_CASH_FLOW",
            "FINANCING_CASH_FLOW", "NET_INCREASE_CASH", "ENDING_CASH_EQUIVALENTS",
            "CASH", "ACCOUNTS_RECEIVABLE", "INVENTORY", "CONTRACT_LIABILITIES",
            "CONTRACT_LIAB", "ACCOUNTS_PAYABLE", "TOTAL_CURRENT_ASSETS",
            "TOTAL_CURRENT_LIABILITIES", "TOTAL_CURRENT_LIAB", "TOTAL_ASSETS",
            "TOTAL_LIABILITIES", "TOTAL_EQUITY", "SHORT_TERM_BORROWINGS",
            "CURRENT_PORTION_LONG_DEBT", "LONG_TERM_BORROWINGS", "BONDS_PAYABLE",
            "FIXED_ASSETS", "INTANGIBLE_ASSETS", "CONSTRUCTION_IN_PROGRESS"));

    public List<FinancialEvidence> select(List<FinancialEvidence> values,
                                          FinancialReportType reportType) {
        List<FinancialEvidence> sorted = new ArrayList<FinancialEvidence>(values);
        sorted.sort(Comparator.comparing(FinancialEvidence::getId));
        LinkedHashMap<String, FinancialEvidence> selected =
                new LinkedHashMap<String, FinancialEvidence>();
        addTypes(selected, sorted, "FINDING", "METRIC", "DATA_GAP");
        addCoreTrends(selected, sorted);
        addCoreLines(selected, sorted, reportType);
        List<FinancialEvidence> result = new ArrayList<FinancialEvidence>(selected.values());
        result.sort(Comparator.comparing(FinancialEvidence::getId));
        return result;
    }

    private void addTypes(LinkedHashMap<String, FinancialEvidence> target,
                          List<FinancialEvidence> values, String... types) {
        for (String type : types) {
            for (FinancialEvidence value : values) {
                if (type.equals(value.getType())) add(target, value);
            }
        }
    }

    private void addCoreTrends(LinkedHashMap<String, FinancialEvidence> target,
                               List<FinancialEvidence> values) {
        for (FinancialEvidence value : values) {
            if ("TREND".equals(value.getType()) && isCore(value.getId())
                    && value.getDetail() != null && value.getDetail().contains(";")) {
                add(target, value);
            }
        }
    }

    private void addCoreLines(LinkedHashMap<String, FinancialEvidence> target,
                              List<FinancialEvidence> values,
                              FinancialReportType reportType) {
        Set<String> ids = new LinkedHashSet<String>();
        for (FinancialEvidence value : values) ids.add(value.getId());
        List<FinancialEvidence> eligible = new ArrayList<FinancialEvidence>();
        for (FinancialEvidence value : values) {
            if (!"LINE_ITEM".equals(value.getType()) || !isCore(value.getId())) continue;
            if (reportType == FinancialReportType.Q1
                    && value.getId().endsWith("_CURRENT_YTD")
                    && ids.contains(value.getId().replace("_CURRENT_YTD", "_CURRENT_QUARTER"))) {
                continue;
            }
            eligible.add(value);
        }
        int remaining = Math.max(0, MAX_EVIDENCE - target.size());
        int quota = Math.max(1, remaining / 3);
        addStatementLines(target, eligible, "L_INCOME_", quota);
        addStatementLines(target, eligible, "L_BALANCE_SHEET_", quota);
        addStatementLines(target, eligible, "L_CASH_FLOW_", quota);
        for (FinancialEvidence value : eligible) add(target, value);
    }

    private void addStatementLines(LinkedHashMap<String, FinancialEvidence> target,
                                   List<FinancialEvidence> values,
                                   String prefix, int limit) {
        int added = 0;
        for (FinancialEvidence value : values) {
            if (!value.getId().startsWith(prefix) || added >= limit) continue;
            int before = target.size();
            add(target, value);
            if (target.size() > before) added++;
        }
    }

    private boolean isCore(String id) {
        if (id == null) return false;
        for (String concept : CORE_CONCEPTS) {
            if (id.contains("_" + concept + "_")) return true;
        }
        return false;
    }

    private void add(LinkedHashMap<String, FinancialEvidence> target, FinancialEvidence value) {
        if (target.size() < MAX_EVIDENCE) target.putIfAbsent(value.getId(), value);
    }
}
