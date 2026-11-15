package com.finscope.service.learningcard;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialStatementType;
import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.dao.fundamentals.FundamentalResearchRepository;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.fundamentals.CompanyBusinessProfile;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCardEvidence;
import com.finscope.rpc.research.material.CompanyBusinessProfileClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 以已校验证券身份的公司资料和本地公开财报建立研究基线，不使用用户手工笔记。 */
@Service
@Slf4j
public class StockLearningBaselineService {
    private static final Set<FinancialQualityStatus> USABLE = Set.of(
            FinancialQualityStatus.FRESH, FinancialQualityStatus.PARTIAL, FinancialQualityStatus.STALE);
    private static final Set<String> CONCEPTS = Set.of("REVENUE", "TOTAL_REVENUE", "OPERATING_COST",
            "NET_PROFIT_PARENT", "OPERATING_CASH_FLOW", "ACCOUNTS_RECEIVABLE", "CAPITAL_EXPENDITURE",
            "CASH", "TOTAL_ASSETS", "TOTAL_LIABILITIES", "RND_EXPENSE");
    @Resource
    private FundamentalResearchRepository profiles;
    @Resource
    private CompanyBusinessProfileClient profileClient;
    @Resource
    private FinancialReportRepository reports;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public List<StockLearningCardEvidence> build(Instrument instrument) {
        List<StockLearningCardEvidence> result = new ArrayList<>();
        CompanyBusinessProfile profile = profile(instrument);
        if (profile != null) {
            String body = identity(instrument) + "公司全称：" + profile.getCompanyName() + "\n所属行业："
                    + text(profile.getIndustry()) + "\n公司简介：" + text(profile.getDescription())
                    + "\n经营范围：" + text(profile.getBusinessScope());
            result.add(evidence(instrument.getName() + "公司资料与经营范围", profile.getSourceUrl(),
                    "东方财富公司资料", "", body, "COMPANY_PROFILE"));
        }
        try {
            List<FinancialReport> available = reports.findReports(instrument.getId()).stream()
                    .filter(report -> usable(instrument, report)).sorted(Comparator.comparing(FinancialReport::getPeriodEnd).reversed())
                    .toList();
            if (!available.isEmpty()) {
                FinancialReport latest = available.get(0);
                appendFinancial(instrument, latest, result);
                available.stream().filter(previous -> previous.getPeriodEnd().equals(latest.getPeriodEnd().minusYears(1))
                        && previous.getReportType() == latest.getReportType()
                        && Objects.equals(previous.getCurrency(), latest.getCurrency())
                        && Objects.equals(previous.getSourceCode(), latest.getSourceCode())).findFirst()
                        .ifPresent(previous -> appendFinancial(instrument, previous, result));
            }
        } catch (RuntimeException error) {
            log.warn("股票学习卡财报基线读取失败 code={} errorType={}", instrument.getCode(), error.getClass().getSimpleName());
        }
        return result;
    }

    private CompanyBusinessProfile profile(Instrument instrument) {
        CompanyBusinessProfile cached = profiles.profile(instrument.getId()).orElse(null);
        if (cached != null && !Objects.equals(instrument.getId(), cached.getInstrumentId())) {
            cached = null;
        }
        if (cached != null && cached.getFetchedAt() != null
                && cached.getFetchedAt().isAfter(LocalDateTime.now(clock).minusDays(7))) {
            return cached;
        }
        try {
            CompanyBusinessProfile fetched = profileClient.fetch(instrument);
            if (!Objects.equals(instrument.getId(), fetched.getInstrumentId())) {
                throw new IllegalStateException("公司资料标的身份不匹配");
            }
            profiles.saveProfile(fetched);
            return fetched;
        } catch (RuntimeException error) {
            log.warn("股票学习卡公司资料读取失败 code={} errorType={}", instrument.getCode(), error.getClass().getSimpleName());
            return cached;
        }
    }

    private boolean usable(Instrument instrument, FinancialReport report) {
        return Objects.equals(instrument.getId(), report.getInstrumentId()) && "CONSOLIDATED".equals(report.getScope())
                && report.getPeriodEnd() != null && !report.getPeriodEnd().isAfter(LocalDate.now(clock))
                && (report.getPublishedAt() == null || !report.getPublishedAt().isAfter(LocalDateTime.now(clock)))
                && report.getQualityStatus() != null && USABLE.contains(report.getQualityStatus())
                && financialUrl(instrument, report.getSourceCode()) != null;
    }

    private void appendFinancial(Instrument instrument, FinancialReport report, List<StockLearningCardEvidence> result) {
        Map<String, List<FinancialLineItem>> sources = new LinkedHashMap<>();
        for (FinancialStatementType statement : FinancialStatementType.values()) {
            for (FinancialLineItem item : reports.findLineItems(report.getId(), statement)) {
                if (item.getNormalizedValue() != null && item.getConceptCode() != null && CONCEPTS.contains(item.getConceptCode())
                        && item.getQualityStatus() != null && USABLE.contains(item.getQualityStatus())
                        && Objects.equals(report.getCurrency(), item.getCurrency()) && item.getPeriodRole() != null
                        && Set.of("CURRENT_YTD", "CURRENT_PERIOD_END").contains(item.getPeriodRole())
                        && financialUrl(instrument, item.getSourceCode()) != null) {
                    sources.computeIfAbsent(item.getSourceCode(), ignored -> new ArrayList<>()).add(item);
                }
            }
        }
        // 不同供应商分别成证据，补充缺项时仍保留各自口径和可追溯链接。
        sources.entrySet().stream().sorted(Comparator.comparing(entry -> !entry.getKey().equals(report.getSourceCode())))
                .limit(2).forEach(entry -> appendFinancialSource(instrument, report, entry.getKey(), entry.getValue(), result));
    }

    private void appendFinancialSource(Instrument instrument, FinancialReport report, String source,
                                        List<FinancialLineItem> items, List<StockLearningCardEvidence> result) {
        StringBuilder body = new StringBuilder(identity(instrument)).append("报告期：").append(report.getPeriodEnd())
                .append("；报告类型：").append(report.getReportType()).append("；合并口径；币种：")
                .append(report.getCurrency()).append("；来源：").append(source).append("；报告质量：")
                .append(report.getQualityStatus()).append("\n以下为累计发生额或期末余额，不是单季度或实时行情。\n");
        for (FinancialLineItem item : items) {
            body.append(item.getSourceLabel()).append("（").append(item.getConceptCode()).append("，")
                    .append(item.getPeriodRole()).append("，质量 ").append(item.getQualityStatus()).append("）：")
                    .append(item.getNormalizedValue().stripTrailingZeros().toPlainString()).append(' ')
                    .append(item.getCurrency()).append('\n');
        }
        result.add(evidence(instrument.getName() + " " + report.getPeriodEnd() + "合并财报指标",
                financialUrl(instrument, source), source, report.getPublishedAt() == null ? "" : report.getPublishedAt().toString(),
                body.toString(), "FINANCIAL_STATEMENT"));
    }

    private String financialUrl(Instrument instrument, String source) {
        if ("FUYAO_TONGHUASHUN_API".equals(source)) {
            return "https://basic.10jqka.com.cn/" + instrument.getCode() + "/finance.html";
        }
        if ("SINA_FINANCIAL_STATEMENTS".equals(source)) {
            return "https://money.finance.sina.com.cn/corp/go.php/vFD_FinanceSummary/stockid/" + instrument.getCode() + ".phtml";
        }
        return null;
    }

    private StockLearningCardEvidence evidence(String title, String url, String source, String publishedAt,
                                               String body, String origin) {
        var result = new StockLearningCardEvidence("", title, url, source, publishedAt, body);
        result.setContentOrigin(origin);
        return result;
    }

    private String identity(Instrument instrument) {
        return "证券代码：" + instrument.getCode() + "；证券简称：" + instrument.getName() + "\n";
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
