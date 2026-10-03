package com.finscope.service.attribution;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionEventSource;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.attribution.AttributionResearchInsights;
import com.finscope.domain.instrument.Instrument;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 复用原材料，并补查业务/可比公司；不改变原报告的证据或筛选结果。 */
@Service
public class AttributionInsightMaterialsService {
    @Resource
    private SearchEvidenceGateway searchEvidenceGateway;
    @Resource
    private SearchEvidenceContentService searchEvidenceContentService;
    @Resource
    private AgentRunRepository agentRunRepository;

    public void collect(AttributionResearchInsights result, AttributionReport report,
                        Instrument instrument, List<AttributionEvidence> originals) {
        LocalDate cutoff = report.getReportDate();
        if (report.getAssessment() != null && report.getAssessment().getEventContext() != null) {
            for (AttributionEventSource source : report.getAssessment().getEventContext().getSources()) {
                add(result, source.getTitle(), source.getUrl(), source.getPublishedAt(), source.getContent(), false, cutoff);
            }
        }
        for (AttributionEvidence source : originals == null ? List.<AttributionEvidence>of() : originals) {
            if (source != null) {
                add(result, source.getTitle(), source.getUrl(), source.getPublishedAt(), source.getSnippet(), false, cutoff);
            }
        }
        if (!searchEvidenceGateway.isConfigured(SearchDepth.DEEP)) {
            result.getWarnings().add("业务资料补查未配置，本次使用报告已有材料。");
            return;
        }
        Set<String> reads = new HashSet<>();
        for (String purpose : List.of("主营业务 产品 收入 年报", "同行业 可比公司 年报")) {
            String query = instrument.getName() + " " + instrument.getCode() + " " + purpose + " before:" + cutoff.plusDays(1);
            long started = System.currentTimeMillis();
            try {
                var batch = searchEvidenceGateway.search(new SearchEvidenceRequest(query, SearchDepth.DEEP, 4, 4, "cn", "zh", 8000));
                if (batch.isAllProvidersFailed()) {
                    throw new IllegalStateException("业务资料搜索不可用");
                }
                for (var hit : batch.getEvidence().stream().limit(4).toList()) {
                    if (!eligible(hit.getUrl(), hit.getPublishedAt(), cutoff)) {
                        continue;
                    }
                    String content = hit.getContent();
                    if (reads.size() < 2 && reads.add(hit.getUrl())) {
                        try {
                            content = searchEvidenceContentService.acquire(hit, query, instrument.getName(), true).getContent();
                        } catch (RuntimeException ex) {
                            warn(result, "部分业务披露正文未读取成功，已保留搜索摘要。");
                        }
                    }
                    add(result, hit.getTitle(), hit.getUrl(), hit.getPublishedAt(), content, true, cutoff);
                }
                agentRunRepository.record("attribution:insight-search", "SUCCESS", query,
                        "sources=" + result.getSources().size(), null, System.currentTimeMillis() - started);
            } catch (RuntimeException ex) {
                warn(result, "部分业务资料补查未完成，已有资料仍参与分析。");
                agentRunRepository.record("attribution:insight-search", "FAILED", query, null,
                        ex.getClass().getSimpleName(), System.currentTimeMillis() - started);
            }
        }
    }

    private void add(AttributionResearchInsights result, String title, String url, String date,
                     String content, boolean supplemental, LocalDate cutoff) {
        if (!eligible(url, date, cutoff) || content == null || content.isBlank()) {
            return;
        }
        var existing = result.getSources().stream().filter(source -> source.getUrl().equals(url)).findFirst();
        if (existing.isPresent()) {
            if (content.length() > existing.get().getContent().length()) {
                existing.get().setContent(limit(content, 4000));
            }
            return;
        }
        if (result.getSources().size() >= 32) {
            return;
        }
        var source = new AttributionEventSource();
        source.setId("B" + (result.getSources().size() + 1));
        source.setTitle(limit(title, 240));
        source.setUrl(url);
        source.setPublishedAt(date);
        source.setContent(limit(content, 4000));
        source.setSupplemental(supplemental);
        result.getSources().add(source);
    }

    private boolean eligible(String url, String date, LocalDate cutoff) {
        try {
            URI uri = URI.create(url == null ? "" : url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                return false;
            }
        } catch (IllegalArgumentException ex) {
            return false;
        }
        LocalDate published = publicationDate(date);
        return published == null || !published.isAfter(cutoff);
    }

    private LocalDate publicationDate(String value) {
        String date = value == null ? "" : value.trim();
        try {
            if (date.contains("T") && date.matches(".*(?:Z|[+-]\\d{2}:\\d{2})$")) {
                return OffsetDateTime.parse(date).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate();
            }
            if (date.matches("\\d{4}-\\d{2}-\\d{2}([ T].*)?")) {
                return LocalDate.parse(date.substring(0, 10));
            }
            return ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String limit(String value, int max) {
        String clean = value == null ? "" : value.trim();
        return clean.substring(0, Math.min(clean.length(), max));
    }

    private void warn(AttributionResearchInsights result, String warning) {
        if (!result.getWarnings().contains(warning)) {
            result.getWarnings().add(warning);
        }
    }
}
