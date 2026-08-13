package com.finscope.rpc.financials;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.common.enums.financials.FinancialStatementType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.search.WebSearchProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class DartFinancialDataClient implements StructuredFinancialDataGateway {
    private static final int MAX_CANDIDATES_PER_REFRESH = 5;
    private static final String BASE = "https://opendart.fss.or.kr";
    private static final String ENGLISH_DART = "https://englishdart.fss.or.kr";
    private static final Pattern RCP_NO = Pattern.compile("(?:rcpNo=|/)(\\d{14})(?:$|[^0-9])");
    private static final Pattern VIEW_DOC = Pattern.compile(
            "viewDoc\\('([^']+)',\\s*'([^']+)',\\s*'[^']*',\\s*'([^']+)'\\)");
    private static final Pattern DATE = Pattern.compile("(20\\d{2})-(\\d{2})-(\\d{2})");
    private static final Map<String, Definition> DEFINITIONS = definitions();

    private final FinanceHttpClient http;
    private final List<WebSearchProvider> searches;

    public DartFinancialDataClient(FinanceHttpClient http, List<WebSearchProvider> searches) {
        this.http = http;
        this.searches = searches == null
                ? Collections.<WebSearchProvider>emptyList() : searches;
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument != null && "STOCK".equals(instrument.getType())
                && "KR".equalsIgnoreCase(instrument.getMarket())
                && alias(instrument, "KRX_SYMBOL:") != null;
    }

    @Override
    public String providerCode() {
        return "DART_XBRL";
    }

    @Override
    public ExternalFinancialStatements fetch(Instrument instrument, LocalDate periodEnd,
                                             FinancialReportType reportType) {
        if (!supports(instrument)) {
            throw error("DART_INSTRUMENT_UNSUPPORTED", "缺少韩国股票代码，无法抓取 DART 财报");
        }
        ProviderContractException lastError = null;
        int attempted = 0;
        for (String rcpNo : discoverFilings(instrument, periodEnd, reportType)) {
            if (attempted++ >= MAX_CANDIDATES_PER_REFRESH) break;
            try {
                return fetchCandidate(instrument, rcpNo, periodEnd, reportType);
            } catch (ProviderContractException error) {
                propagateTimeout(error, "DART XBRL 抓取超时");
                lastError = error;
                log.warn("DART XBRL 候选不匹配 rcpNo={} message={}", rcpNo, error.getMessage());
            }
        }
        if (lastError != null) throw lastError;
        throw error("DART_FILING_NOT_FOUND", "全网搜索未找到该报告期的 DART XBRL 披露");
    }

    private ExternalFinancialStatements fetchCandidate(Instrument instrument, String rcpNo, LocalDate periodEnd,
                                                        FinancialReportType reportType) {
        Document main = getDocument(URI.create(BASE
                + "/xbrl/viewer/main.do?lang=en&rcpNo=" + rcpNo));
        validateIssuer(main, instrument);
        Map<FinancialStatementType, Role> roles = roles(main);
        if (roles.size() < 3) {
            throw error("DART_XBRL_STATEMENTS_MISSING", "DART 披露未包含完整的连接三张表");
        }

        ExternalFinancialStatements result = new ExternalFinancialStatements();
        result.setReportType(reportType);
        result.setScope("CONSOLIDATED");
        result.setCurrency("KRW");
        result.setPublishedAt(filingDate(rcpNo).atStartOfDay());
        result.setAudited(reportType == FinancialReportType.ANNUAL);
        result.setSourceCode(providerCode());
        result.setQualityStatus(FinancialQualityStatus.FRESH);
        result.getWarnings().add("数据来自韩国金融监督院 DART XBRL Viewer；实际报告期以披露表头为准");

        LocalDate actualEnd = null;
        for (Map.Entry<FinancialStatementType, Role> entry : roles.entrySet()) {
            ParsedStatement parsed = parseStatement(entry.getKey(), entry.getValue(),
                    periodEnd);
            result.getStatements().add(parsed.statement);
            if (actualEnd == null || parsed.periodEnd.isAfter(actualEnd)) actualEnd = parsed.periodEnd;
        }
        result.setPeriodEnd(actualEnd == null ? periodEnd : actualEnd);
        return result;
    }

    private void validateIssuer(Document main, Instrument instrument) {
        String title = main.title();
        String issuer = title.contains("/") ? title.substring(0, title.indexOf('/')) : title;
        String expected = normalizedIssuer(instrument.getName());
        if (expected.isEmpty() || !expected.equals(normalizedIssuer(issuer))) {
            throw error("DART_ISSUER_MISMATCH", "DART 披露主体与所选公司不一致");
        }
    }

    private String normalizedIssuer(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
        return normalized.replaceFirst("(incorporated|corporation|coltd|corp|inc|ltd)$", "");
    }

    private List<String> discoverFilings(Instrument instrument, LocalDate periodEnd,
                                         FinancialReportType reportType) {
        List<String> official = discoverOfficialFilings(instrument, periodEnd, reportType);
        ensureDeadline("DART 官方披露检索超时");
        if (!official.isEmpty()) return official;

        String symbol = alias(instrument, "KRX_SYMBOL:");
        String reportName = reportName(reportType);
        int filingYear = reportType == FinancialReportType.ANNUAL
                ? periodEnd.getYear() + 1 : periodEnd.getYear();
        List<String> candidates = new ArrayList<String>();
        String shortName = instrument.getName().replaceAll("(?i)\\s+(incorporated|inc\\.?|corp\\.?)$", "");
        String[] queries = {
                "site:opendart.fss.or.kr/xbrl/viewer/main.do \"" + shortName + "\" \""
                        + reportName + "\" " + symbol + " " + filingYear,
                "site:englishdart.fss.or.kr \"" + shortName + "\" \""
                        + reportName + "\" " + filingYear,
                symbol + " " + shortName + " " + filingYear + " " + reportName + " DART XBRL"
        };
        for (String query : queries) {
            for (WebSearchProvider search : searches) {
                if (!search.isConfigured()) continue;
                try {
                    List<SearchResult> results = search.search(new WebSearchRequest(query, 8, "", "en"));
                    ensureDeadline("DART 披露搜索超时");
                    for (SearchResult result : results) addRcpNo(candidates, result.getUrl());
                } catch (Exception error) {
                    propagateTimeout(error, "DART 披露搜索超时");
                    log.warn("DART 披露搜索失败 provider={} message={}",
                            search.providerCode(), error.getMessage());
                }
            }
            if (!candidates.isEmpty()) break;
        }
        if (candidates.isEmpty()) {
            throw error("DART_FILING_NOT_FOUND", "全网搜索未找到该报告期的 DART XBRL 披露");
        }
        return candidates;
    }

    private List<String> discoverOfficialFilings(Instrument instrument, LocalDate periodEnd,
                                                  FinancialReportType reportType) {
        List<String> candidates = new ArrayList<String>();
        try {
            String companyName = instrument.getName().replaceAll(
                    "(?i)\\s+(incorporated|inc\\.?|corp\\.?)$", "").trim();
            FinanceHttpResponse company = postForm("/corp/searchCorp.ax",
                    form("textCrpNm", companyName));
            Element code = selectCorpCode(Jsoup.parse(company.getBody()),
                    alias(instrument, "KRX_SYMBOL:"));
            if (code == null || !code.attr("value").matches("[0-9]{8}")) return candidates;

            int filingYear = reportType == FinancialReportType.ANNUAL
                    ? periodEnd.getYear() + 1 : periodEnd.getYear();
            Map<String, String> fields = new LinkedHashMap<String, String>();
            fields.put("currentPage", "1");
            fields.put("maxResults", "30");
            fields.put("maxLinks", "10");
            fields.put("sort", "");
            fields.put("series", "");
            fields.put("textCrpCik", code.attr("value"));
            fields.put("lateKeyword", "");
            fields.put("keyword", "");
            fields.put("reportNamePopYn", "");
            fields.put("textkeyword", "");
            fields.put("textCrpNm", companyName);
            fields.put("textCrpNm2", companyName);
            fields.put("businessCode", "all");
            fields.put("autoSearch", "N");
            fields.put("opendartUrl", "https://engopendart.fss.or.kr");
            fields.put("autoSearchCorp", "Y");
            fields.put("option", "corp");
            fields.put("reportName", "");
            fields.put("reportName2", "");
            fields.put("tocSrch", "");
            fields.put("tocSrch2", "");
            fields.put("startDate", filingYear + "0101");
            fields.put("endDate", filingYear + "1231");
            fields.put("decadeType", "");
            fields.put("finalReport", "recent");
            fields.put("publicType", publicType(reportType));
            FinanceHttpResponse disclosures = postForm("/dsbb007/detailSearch.ax", form(fields));
            Matcher filing = RCP_NO.matcher(disclosures.getBody());
            while (filing.find()) {
                if (!candidates.contains(filing.group(1))) candidates.add(filing.group(1));
            }
        } catch (Exception error) {
            propagateTimeout(error, "DART 官方披露检索超时");
            log.warn("DART 官方披露检索失败 company={} message={}",
                    instrument.getName(), error.getMessage());
        }
        return candidates;
    }

    private void propagateTimeout(Exception error, String message) {
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            throw new ProviderContractException("TIMEOUT", message, true, error);
        }
        if (error instanceof ProviderContractException
                && "TIMEOUT".equals(((ProviderContractException) error).getErrorType())) {
            throw (ProviderContractException) error;
        }
        if (ProviderCallDeadline.remainingMillis() <= 0L)
            throw new ProviderContractException("TIMEOUT", message, true, error);
    }

    private void ensureDeadline(String message) {
        if (ProviderCallDeadline.remainingMillis() <= 0L)
            throw new ProviderContractException("TIMEOUT", message, true);
    }

    private Element selectCorpCode(Document document, String symbol) {
        Elements codes = document.select("input[name=hiddenCikCD1]");
        for (Element code : codes) {
            Element row = code.closest("tr");
            if (row != null && symbol != null && row.text().contains(symbol)) return code;
        }
        return codes.size() == 1 ? codes.first() : null;
    }

    private FinanceHttpResponse postForm(String path, String body) throws Exception {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Accept", "text/html, */*; q=0.01");
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Referer", ENGLISH_DART + "/dsbb007/main.do?option=corp");
        FinanceHttpResponse response = http.postForm(providerCode(), URI.create(ENGLISH_DART + path),
                body, headers);
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw error("DART_SEARCH_HTTP_ERROR", "DART 官方披露检索失败：HTTP " + response.getStatus());
        }
        return response;
    }

    private String publicType(FinancialReportType type) {
        if (type == FinancialReportType.ANNUAL) return "A001";
        if (type == FinancialReportType.HALF_YEAR) return "A002";
        return "A003";
    }

    private String form(String key, String value) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put(key, value);
        return form(fields);
    }

    private String form(Map<String, String> fields) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (result.length() > 0) result.append('&');
            result.append(encode(field.getKey())).append('=').append(encode(field.getValue()));
        }
        return result.toString();
    }

    private void addRcpNo(List<String> values, String url) {
        if (url == null || (!url.contains("dart.fss.or.kr") && !url.contains("opendart.fss.or.kr"))) return;
        Matcher matcher = RCP_NO.matcher(url);
        if (matcher.find() && !values.contains(matcher.group(1))) values.add(matcher.group(1));
    }

    private Map<FinancialStatementType, Role> roles(Document main) {
        Map<FinancialStatementType, Role> result = new LinkedHashMap<FinancialStatementType, Role>();
        findRole(main, result, FinancialStatementType.BALANCE_SHEET,
                "D210000", "statement of financial position");
        findRole(main, result, FinancialStatementType.INCOME,
                "D431410", "statement of comprehensive income");
        findRole(main, result, FinancialStatementType.CASH_FLOW,
                "D520000", "statement of cash flows");
        return result;
    }

    private void findRole(Document main, Map<FinancialStatementType, Role> roles,
                          FinancialStatementType type, String preferredId, String label) {
        Element selected = main.selectFirst("#role_" + preferredId);
        if (selected == null) {
            for (Element link : main.select("a[onclick*=viewDoc]")) {
                String text = link.text().toLowerCase(Locale.ROOT);
                if (text.contains(label) && text.contains("consolidated")) {
                    selected = link;
                    break;
                }
            }
        }
        if (selected == null) return;
        Matcher matcher = VIEW_DOC.matcher(selected.attr("onclick"));
        if (matcher.find()) roles.put(type, new Role(matcher.group(1), matcher.group(2)));
    }

    private ParsedStatement parseStatement(FinancialStatementType type, Role role,
                                           LocalDate targetEnd) {
        String uri = BASE + "/xbrl/viewer/view.do?xbrlExtSeq=" + encode(role.xbrlExtSeq)
                + "&roleId=" + encode(role.roleId) + "&lang=en";
        Document document = getDocument(URI.create(uri));
        Element table = document.selectFirst("table.fact-table");
        if (table == null) throw error("DART_XBRL_TABLE_MISSING", "DART XBRL 财务表解析失败");
        Element period = selectPeriod(table, type, targetEnd);
        if (period == null) throw error("DART_PERIOD_NOT_FOUND", "DART 披露中没有找到目标报告期");
        int column = period.elementSiblingIndex();
        LocalDate periodEnd = lastDate(period.text());

        ExternalFinancialStatements.Statement statement = new ExternalFinancialStatements.Statement();
        statement.setStatementType(type);
        Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>();
        for (Element row : table.select("tr")) {
            Elements cells = row.children();
            Element concept = row.selectFirst("span.concept-label");
            if (concept == null || column >= cells.size()) continue;
            String sourceField = concept.attr("id").split("#", 2)[0];
            Definition definition = DEFINITIONS.get(sourceField);
            if (definition == null || definition.type != type || seen.containsKey(definition.code)) continue;
            Element fact = cells.get(column).selectFirst("span.fact-value");
            BigDecimal amount = number(fact == null ? "" : fact.text());
            if (amount == null) continue;
            if (definition.absolute) amount = amount.abs();
            ExternalFinancialStatements.Value value = new ExternalFinancialStatements.Value();
            value.setSourceLabel(concept.text());
            value.setConceptCode(definition.code);
            value.setPeriodRole(type == FinancialStatementType.BALANCE_SHEET
                    ? "CURRENT_PERIOD_END" : "CURRENT_YTD");
            value.setValue(amount);
            value.setUnitMultiplier(BigDecimal.ONE);
            value.setSourceField(sourceField);
            statement.getValues().add(value);
            seen.put(definition.code, Boolean.TRUE);
        }
        if (statement.getValues().isEmpty()) {
            throw error("DART_XBRL_VALUES_MISSING", "DART XBRL 财务表没有可识别的标准科目");
        }
        return new ParsedStatement(statement, periodEnd);
    }

    private Element selectPeriod(Element table, FinancialStatementType statementType,
                                 LocalDate targetEnd) {
        for (Element header : table.select("th.period")) {
            List<LocalDate> dates = dates(header.text());
            if (dates.isEmpty() || !targetEnd.equals(dates.get(dates.size() - 1))) continue;
            if (statementType == FinancialStatementType.BALANCE_SHEET) return header;
            if (dates.size() >= 2 && dates.get(0).equals(LocalDate.of(targetEnd.getYear(), 1, 1))) {
                return header;
            }
        }
        return null;
    }

    private List<LocalDate> dates(String value) {
        List<LocalDate> dates = new ArrayList<LocalDate>();
        Matcher matcher = DATE.matcher(value);
        while (matcher.find()) dates.add(LocalDate.parse(matcher.group()));
        return dates;
    }

    private Document getDocument(URI uri) {
        try {
            FinanceHttpResponse response = http.get(providerCode(), uri,
                    Collections.singletonMap("Accept", "text/html"));
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw error("DART_XBRL_HTTP_ERROR", "DART XBRL 请求失败：HTTP " + response.getStatus());
            }
            return Jsoup.parse(response.getBody(), BASE);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("DART_XBRL_REQUEST_FAILED",
                    "DART XBRL 请求失败", true, error);
        }
    }

    private LocalDate lastDate(String value) {
        Matcher matcher = DATE.matcher(value);
        LocalDate result = null;
        while (matcher.find()) result = LocalDate.parse(matcher.group());
        if (result == null) throw error("DART_PERIOD_INVALID", "DART 报告期格式无法识别");
        return result;
    }

    private BigDecimal number(String value) {
        String normalized = value == null ? "" : value.replace(",", "").replace("(", "-")
                .replace(")", "").replaceAll("[^0-9.\\-]", "");
        if (normalized.isEmpty() || "-".equals(normalized)) return null;
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDate filingDate(String rcpNo) {
        return LocalDate.of(Integer.parseInt(rcpNo.substring(0, 4)),
                Integer.parseInt(rcpNo.substring(4, 6)), Integer.parseInt(rcpNo.substring(6, 8)));
    }

    private String reportName(FinancialReportType type) {
        if (type == FinancialReportType.ANNUAL) return "Annual Report";
        if (type == FinancialReportType.HALF_YEAR) return "Semi-Annual Report";
        return "Quarterly Report";
    }

    private String alias(Instrument instrument, String prefix) {
        if (instrument.getAliases() == null) return null;
        for (String part : instrument.getAliases().split("[,;\\s]+")) {
            if (part.toUpperCase(Locale.ROOT).startsWith(prefix)) return part.substring(prefix.length());
        }
        return null;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException("JVM 不支持 UTF-8", impossible);
        }
    }

    private ProviderContractException error(String code, String message) {
        return new ProviderContractException(code, message, false);
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> values = new LinkedHashMap<String, Definition>();
        define(values, FinancialStatementType.INCOME, "REVENUE", false,
                "ifrs-full_Revenue");
        define(values, FinancialStatementType.INCOME, "OPERATING_COST", false,
                "ifrs-full_CostOfSales");
        define(values, FinancialStatementType.INCOME, "GROSS_PROFIT", false,
                "ifrs-full_GrossProfit");
        define(values, FinancialStatementType.INCOME, "OPERATING_PROFIT", false,
                "dart_OperatingIncomeLoss", "ifrs-full_ProfitLossFromOperatingActivities");
        define(values, FinancialStatementType.INCOME, "NET_PROFIT", false,
                "ifrs-full_ProfitLoss");
        define(values, FinancialStatementType.INCOME, "NET_PROFIT_PARENT", false,
                "ifrs-full_ProfitLossAttributableToOwnersOfParent");
        define(values, FinancialStatementType.INCOME, "RND_EXPENSE", false,
                "dart_ResearchAndDevelopmentExpenses");

        define(values, FinancialStatementType.BALANCE_SHEET, "TOTAL_ASSETS", false,
                "ifrs-full_Assets");
        define(values, FinancialStatementType.BALANCE_SHEET, "TOTAL_CURRENT_ASSETS", false,
                "ifrs-full_CurrentAssets");
        define(values, FinancialStatementType.BALANCE_SHEET, "CASH_AND_EQUIVALENTS", false,
                "ifrs-full_CashAndCashEquivalents");
        define(values, FinancialStatementType.BALANCE_SHEET, "ACCOUNTS_RECEIVABLE", false,
                "ifrs-full_TradeAndOtherCurrentReceivables");
        define(values, FinancialStatementType.BALANCE_SHEET, "INVENTORY", false,
                "ifrs-full_Inventories");
        define(values, FinancialStatementType.BALANCE_SHEET, "PROPERTY_PLANT_EQUIPMENT", false,
                "ifrs-full_PropertyPlantAndEquipment");
        define(values, FinancialStatementType.BALANCE_SHEET, "TOTAL_LIABILITIES", false,
                "ifrs-full_Liabilities");
        define(values, FinancialStatementType.BALANCE_SHEET, "TOTAL_CURRENT_LIABILITIES", false,
                "ifrs-full_CurrentLiabilities");
        define(values, FinancialStatementType.BALANCE_SHEET, "SHORT_TERM_BORROWINGS", false,
                "ifrs-full_ShorttermBorrowings", "ifrs-full_CurrentBorrowings");
        define(values, FinancialStatementType.BALANCE_SHEET, "LONG_TERM_BORROWINGS", false,
                "ifrs-full_LongtermBorrowings");
        define(values, FinancialStatementType.BALANCE_SHEET, "TOTAL_EQUITY", false,
                "ifrs-full_Equity");

        define(values, FinancialStatementType.CASH_FLOW, "OPERATING_CASH_FLOW", false,
                "ifrs-full_CashFlowsFromUsedInOperatingActivities");
        define(values, FinancialStatementType.CASH_FLOW, "INVESTING_CASH_FLOW", false,
                "ifrs-full_CashFlowsFromUsedInInvestingActivities");
        define(values, FinancialStatementType.CASH_FLOW, "FINANCING_CASH_FLOW", false,
                "ifrs-full_CashFlowsFromUsedInFinancingActivities");
        define(values, FinancialStatementType.CASH_FLOW, "CAPITAL_EXPENDITURE", true,
                "ifrs-full_PurchaseOfPropertyPlantAndEquipment");
        define(values, FinancialStatementType.CASH_FLOW, "DIVIDENDS_PAID", true,
                "ifrs-full_DividendsPaid");
        define(values, FinancialStatementType.CASH_FLOW, "SHARE_REPURCHASE", true,
                "ifrs-full_PaymentsToAcquireTreasuryShares");
        return values;
    }

    private static void define(Map<String, Definition> values, FinancialStatementType type,
                               String code, boolean absolute, String... sourceFields) {
        Definition definition = new Definition(type, code, absolute);
        for (String sourceField : sourceFields) values.put(sourceField, definition);
    }

    private static class Definition {
        private final FinancialStatementType type;
        private final String code;
        private final boolean absolute;
        private Definition(FinancialStatementType type, String code, boolean absolute) {
            this.type = type;
            this.code = code;
            this.absolute = absolute;
        }
    }

    private static class Role {
        private final String xbrlExtSeq;
        private final String roleId;
        private Role(String xbrlExtSeq, String roleId) {
            this.xbrlExtSeq = xbrlExtSeq;
            this.roleId = roleId;
        }
    }

    private static class ParsedStatement {
        private final ExternalFinancialStatements.Statement statement;
        private final LocalDate periodEnd;
        private ParsedStatement(ExternalFinancialStatements.Statement statement, LocalDate periodEnd) {
            this.statement = statement;
            this.periodEnd = periodEnd;
        }
    }
}
