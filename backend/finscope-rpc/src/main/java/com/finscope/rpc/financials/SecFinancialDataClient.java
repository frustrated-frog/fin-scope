package com.finscope.rpc.financials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.common.enums.financials.FinancialStatementType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SecFinancialDataClient implements StructuredFinancialDataGateway {
    private static final String SOURCE = "SEC_COMPANY_FACTS";
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final URI BASE_URI = URI.create("https://data.sec.gov/api/xbrl/companyfacts/");
    private static final List<Concept> CONCEPTS = concepts();

    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public SecFinancialDataClient(FinanceHttpClient http) {
        this.http = http;
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument != null && "STOCK".equals(instrument.getType())
                && "US".equals(instrument.getMarket())
                && instrument.getAliases() != null
                && instrument.getAliases().toUpperCase(Locale.ROOT).contains("SEC_CIK:");
    }

    @Override
    public String providerCode() {
        return SOURCE;
    }

    @Override
    public ExternalFinancialStatements fetch(Instrument instrument, LocalDate periodEnd,
                                             FinancialReportType reportType) {
        String cik = cik(instrument);
        URI uri = BASE_URI.resolve("CIK" + cik + ".json");
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "FinScope/0.1 support@finscope.local");
        try {
            FinanceHttpResponse response = http.get(SOURCE, uri, headers, MAX_RESPONSE_BYTES);
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new ProviderContractException("SEC_COMPANY_FACTS_HTTP_ERROR",
                        "SEC Company Facts 请求失败：HTTP " + response.getStatus(), true);
            }
            return parse(response.getBody(), periodEnd.getYear(), reportType);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("SEC_COMPANY_FACTS_ERROR",
                    "SEC Company Facts 解析失败：" + message(error), true);
        }
    }

    private ExternalFinancialStatements parse(String body, int fiscalYear,
                                               FinancialReportType reportType) throws Exception {
        JsonNode root = json.readTree(body);
        JsonNode usGaap = root.path("facts").path("us-gaap");
        if (!usGaap.isObject()) {
            throw new ProviderContractException("SEC_COMPANY_FACTS_SCHEMA_DRIFT",
                    "SEC Company Facts 缺少 us-gaap 数据", false);
        }
        Fact target = findTarget(usGaap, fiscalYear, reportType);
        if (target == null) {
            throw new ProviderContractException("SEC_FINANCIAL_PERIOD_NOT_FOUND",
                    "SEC 未找到 " + fiscalYear + " 财年 " + reportType.name() + " 披露", false);
        }

        ExternalFinancialStatements result = new ExternalFinancialStatements();
        result.setPeriodEnd(target.end);
        result.setReportType(reportType);
        result.setScope("CONSOLIDATED");
        result.setCurrency("USD");
        result.setPublishedAt(target.filed == null ? null : target.filed.atStartOfDay());
        result.setAudited(reportType == FinancialReportType.ANNUAL);
        result.setSourceCode(SOURCE);

        Map<FinancialStatementType, ExternalFinancialStatements.Statement> statements =
                new LinkedHashMap<FinancialStatementType, ExternalFinancialStatements.Statement>();
        for (FinancialStatementType type : FinancialStatementType.values()) {
            ExternalFinancialStatements.Statement statement = new ExternalFinancialStatements.Statement();
            statement.setStatementType(type);
            statements.put(type, statement);
        }
        for (Concept concept : CONCEPTS) {
            MappedFact mapped = findConcept(usGaap, concept, target);
            if (mapped == null) continue;
            ExternalFinancialStatements.Value value = new ExternalFinancialStatements.Value();
            value.setSourceLabel(mapped.label);
            value.setConceptCode(concept.code);
            value.setPeriodRole(concept.duration ? "CURRENT_YTD" : "CURRENT_PERIOD_END");
            value.setValue(mapped.fact.value);
            value.setUnitMultiplier(BigDecimal.ONE);
            value.setSourceField("us-gaap:" + mapped.taxonomyName);
            statements.get(concept.statementType).getValues().add(value);
        }
        for (ExternalFinancialStatements.Statement statement : statements.values()) {
            if (!statement.getValues().isEmpty()) result.getStatements().add(statement);
        }
        for (FinancialStatementType type : FinancialStatementType.values()) {
            if (statements.get(type).getValues().isEmpty()) {
                result.getWarnings().add("SEC 披露缺少可映射的" + statementName(type));
            }
        }
        result.setQualityStatus(result.getStatements().size() == 3
                ? FinancialQualityStatus.FRESH : FinancialQualityStatus.PARTIAL);
        if (result.getStatements().isEmpty()) {
            throw new ProviderContractException("EMPTY_FINANCIAL_STATEMENTS",
                    "SEC 披露未包含可映射的三张表数据", false);
        }
        return result;
    }

    private Fact findTarget(JsonNode usGaap, int fiscalYear, FinancialReportType reportType) {
        String fiscalPeriod = fiscalPeriod(reportType);
        Fact best = null;
        for (Concept concept : CONCEPTS) {
            if (!concept.duration) continue;
            for (String name : concept.taxonomyNames) {
                JsonNode units = usGaap.path(name).path("units").path("USD");
                for (JsonNode node : units) {
                    Fact fact = fact(node);
                    if (!matchesPeriod(fact, fiscalYear, fiscalPeriod, reportType)) continue;
                    if (best == null || betterTarget(fact, best)) best = fact;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private boolean betterTarget(Fact candidate, Fact current) {
        if (candidate.end != null && current.end != null && !candidate.end.equals(current.end)) {
            return candidate.end.isAfter(current.end);
        }
        int candidateDays = durationDays(candidate);
        int currentDays = durationDays(current);
        if (candidateDays != currentDays) return candidateDays > currentDays;
        if (candidate.filed == null) return false;
        return current.filed == null || candidate.filed.isAfter(current.filed);
    }

    private MappedFact findConcept(JsonNode usGaap, Concept concept, Fact target) {
        for (String name : concept.taxonomyNames) {
            JsonNode conceptNode = usGaap.path(name);
            JsonNode units = conceptNode.path("units").path("USD");
            Fact selected = null;
            for (JsonNode node : units) {
                Fact candidate = fact(node);
                if (!target.accession.equals(candidate.accession) || !target.end.equals(candidate.end)) continue;
                if (concept.duration && candidate.start == null) continue;
                if (!concept.duration && candidate.start != null) continue;
                if (selected == null || (concept.duration && durationDays(candidate) > durationDays(selected))) {
                    selected = candidate;
                }
            }
            if (selected != null) {
                String label = text(conceptNode, "label");
                return new MappedFact(name, label == null ? concept.fallbackLabel : label, selected);
            }
        }
        return null;
    }

    private boolean matchesPeriod(Fact fact, int fiscalYear, String fiscalPeriod,
                                  FinancialReportType reportType) {
        if (fact.end == null || fact.fiscalYear != fiscalYear || !fiscalPeriod.equals(fact.fiscalPeriod)) {
            return false;
        }
        if (reportType == FinancialReportType.ANNUAL) {
            return "10-K".equals(fact.form) || "10-K/A".equals(fact.form);
        }
        return "10-Q".equals(fact.form) || "10-Q/A".equals(fact.form);
    }

    private Fact fact(JsonNode node) {
        Fact value = new Fact();
        value.start = date(text(node, "start"));
        value.end = date(text(node, "end"));
        value.filed = date(text(node, "filed"));
        value.accession = text(node, "accn");
        value.form = text(node, "form");
        value.fiscalPeriod = text(node, "fp");
        value.fiscalYear = node.path("fy").asInt(-1);
        value.value = decimal(node.get("val"));
        return value;
    }

    private int durationDays(Fact fact) {
        return fact.start == null || fact.end == null ? 0
                : (int) java.time.temporal.ChronoUnit.DAYS.between(fact.start, fact.end);
    }

    private String cik(Instrument instrument) {
        if (instrument == null || !"STOCK".equals(instrument.getType()) || !"US".equals(instrument.getMarket())) {
            throw new ProviderContractException("FINANCIAL_INSTRUMENT_UNSUPPORTED",
                    "SEC 财报仅支持已识别的美国上市公司", false);
        }
        String aliases = instrument.getAliases();
        if (aliases != null) {
            for (String alias : aliases.split("[,;|]")) {
                String value = alias.trim();
                if (value.toUpperCase(Locale.ROOT).startsWith("SEC_CIK:")) {
                    String digits = value.substring(value.indexOf(':') + 1).replaceAll("\\D", "");
                    if (!digits.isEmpty()) return String.format("%010d", Long.parseLong(digits));
                }
            }
        }
        throw new ProviderContractException("SEC_CIK_MISSING", "美国公司缺少 SEC CIK 标识", false);
    }

    private static String fiscalPeriod(FinancialReportType type) {
        if (type == FinancialReportType.Q1) return "Q1";
        if (type == FinancialReportType.HALF_YEAR) return "Q2";
        if (type == FinancialReportType.Q3) return "Q3";
        return "FY";
    }

    private static String statementName(FinancialStatementType type) {
        if (type == FinancialStatementType.INCOME) return "利润表";
        if (type == FinancialStatementType.BALANCE_SHEET) return "资产负债表";
        return "现金流量表";
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull() ? null : new BigDecimal(node.asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static LocalDate date(String value) {
        return value == null || value.trim().isEmpty() ? null : LocalDate.parse(value);
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static List<Concept> concepts() {
        List<Concept> values = new ArrayList<Concept>();
        values.add(concept(FinancialStatementType.INCOME, "REVENUE", "营业收入", true,
                "RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues", "SalesRevenueNet"));
        values.add(concept(FinancialStatementType.INCOME, "OPERATING_COST", "营业成本", true,
                "CostOfRevenue", "CostOfGoodsAndServicesSold"));
        values.add(concept(FinancialStatementType.INCOME, "GROSS_PROFIT", "毛利润", true, "GrossProfit"));
        values.add(concept(FinancialStatementType.INCOME, "OPERATING_PROFIT", "营业利润", true, "OperatingIncomeLoss"));
        values.add(concept(FinancialStatementType.INCOME, "NET_PROFIT", "净利润", true, "NetIncomeLoss", "ProfitLoss"));
        values.add(concept(FinancialStatementType.INCOME, "RND_EXPENSE", "研发费用", true, "ResearchAndDevelopmentExpense"));
        values.add(concept(FinancialStatementType.INCOME, "ADMIN_EXPENSE", "销售及管理费用", true,
                "SellingGeneralAndAdministrativeExpense"));

        values.add(concept(FinancialStatementType.BALANCE_SHEET, "TOTAL_ASSETS", "资产总计", false, "Assets"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "TOTAL_CURRENT_ASSETS", "流动资产合计", false, "AssetsCurrent"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "CASH", "现金及现金等价物", false,
                "CashAndCashEquivalentsAtCarryingValue", "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "ACCOUNTS_RECEIVABLE", "应收账款", false,
                "AccountsReceivableNetCurrent"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "INVENTORY", "存货", false, "InventoryNet"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "FIXED_ASSETS", "固定资产", false,
                "PropertyPlantAndEquipmentNet"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "TOTAL_LIABILITIES", "负债合计", false, "Liabilities"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "TOTAL_CURRENT_LIABILITIES", "流动负债合计", false,
                "LiabilitiesCurrent"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "CURRENT_PORTION_LONG_DEBT", "一年内到期长期债务", false,
                "LongTermDebtCurrent", "LongTermDebtAndFinanceLeaseObligationsCurrent"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "LONG_TERM_BORROWINGS", "长期债务", false,
                "LongTermDebtNoncurrent", "LongTermDebtAndFinanceLeaseObligationsNoncurrent"));
        values.add(concept(FinancialStatementType.BALANCE_SHEET, "TOTAL_EQUITY", "股东权益合计", false,
                "StockholdersEquity", "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest"));

        values.add(concept(FinancialStatementType.CASH_FLOW, "OPERATING_CASH_FLOW", "经营活动现金流量净额", true,
                "NetCashProvidedByUsedInOperatingActivities"));
        values.add(concept(FinancialStatementType.CASH_FLOW, "CAPITAL_EXPENDITURE", "资本开支", true,
                "PaymentsToAcquirePropertyPlantAndEquipment"));
        values.add(concept(FinancialStatementType.CASH_FLOW, "INVESTING_CASH_FLOW", "投资活动现金流量净额", true,
                "NetCashProvidedByUsedInInvestingActivities"));
        values.add(concept(FinancialStatementType.CASH_FLOW, "FINANCING_CASH_FLOW", "筹资活动现金流量净额", true,
                "NetCashProvidedByUsedInFinancingActivities"));
        values.add(concept(FinancialStatementType.CASH_FLOW, "DIVIDENDS_PAID", "支付股利", true,
                "PaymentsOfDividends", "PaymentsOfDividendsCommonStock"));
        values.add(concept(FinancialStatementType.CASH_FLOW, "SHARE_REPURCHASE", "股份回购支出", true,
                "PaymentsForRepurchaseOfCommonStock"));
        return values;
    }

    private static Concept concept(FinancialStatementType statementType, String code,
                                   String fallbackLabel, boolean duration, String... names) {
        return new Concept(statementType, code, fallbackLabel, duration, Arrays.asList(names));
    }

    private static class Concept {
        private final FinancialStatementType statementType;
        private final String code;
        private final String fallbackLabel;
        private final boolean duration;
        private final List<String> taxonomyNames;

        private Concept(FinancialStatementType statementType, String code, String fallbackLabel,
                        boolean duration, List<String> taxonomyNames) {
            this.statementType = statementType;
            this.code = code;
            this.fallbackLabel = fallbackLabel;
            this.duration = duration;
            this.taxonomyNames = taxonomyNames;
        }
    }

    private static class Fact {
        private LocalDate start;
        private LocalDate end;
        private LocalDate filed;
        private String accession;
        private String form;
        private String fiscalPeriod;
        private int fiscalYear;
        private BigDecimal value;
    }

    private static class MappedFact {
        private final String taxonomyName;
        private final String label;
        private final Fact fact;

        private MappedFact(String taxonomyName, String label, Fact fact) {
            this.taxonomyName = taxonomyName;
            this.label = label;
            this.fact = fact;
        }
    }
}
