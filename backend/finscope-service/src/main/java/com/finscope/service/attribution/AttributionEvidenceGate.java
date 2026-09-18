package com.finscope.service.attribution;

import com.finscope.common.util.StringUtils;
import com.finscope.domain.attribution.AttributionEvidence;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 对归因证据做规范化、去重、排序，并限制模型声明的置信度。 */
@Component
public class AttributionEvidenceGate {
    public List<AttributionEvidence> normalizeAndRank(List<AttributionEvidence> evidences) {
        Map<String, AttributionEvidence> unique = new LinkedHashMap<String, AttributionEvidence>();
        if (evidences != null) {
            for (AttributionEvidence evidence : evidences) {
                if (evidence == null) {
                    continue;
                }
                String key = evidenceKey(evidence);
                evidence.setEventKey(key);
                AttributionEvidence existing = unique.get(key);
                if (existing == null || score(evidence) > score(existing)) {
                    if (existing != null && "COUNTER".equals(existing.getStance())) {
                        evidence.setStance("COUNTER");
                    }
                    unique.put(key, evidence);
                } else if ("COUNTER".equals(evidence.getStance())) {
                    existing.setStance("COUNTER");
                }
            }
        }
        List<AttributionEvidence> result = new ArrayList<AttributionEvidence>(unique.values());
        result.sort(Comparator.comparingInt(this::score).reversed());
        return result;
    }

    /** 交易日之后的信息不得参与回溯归因；日期未知的线索保留但不能提高置信度。 */
    public List<AttributionEvidence> eligibleAtDate(List<AttributionEvidence> evidences, LocalDate reportDate) {
        return eligibleAtDate(evidences, reportDate, reportDate == null ? null : reportDate.minusDays(3));
    }

    public List<AttributionEvidence> eligibleAtDate(List<AttributionEvidence> evidences, LocalDate reportDate,
                                                   LocalDate startDate) {
        List<AttributionEvidence> result = new ArrayList<>();
        for (AttributionEvidence evidence : normalizeAndRank(evidences)) {
            LocalDate published = publishedDate(evidence);
            if (reportDate != null && published != null && published.isAfter(reportDate)) {
                continue;
            }
            if (startDate != null && published != null && published.isBefore(startDate)) {
                evidence.setHistoricalContext(true);
            }
            result.add(evidence);
        }
        return result;
    }

    public boolean isRecentSupport(AttributionEvidence evidence, LocalDate reportDate, LocalDate startDate) {
        LocalDate published = publishedDate(evidence);
        return reportDate != null && startDate != null && published != null
                && !published.isBefore(startDate) && !published.isAfter(reportDate)
                && !evidence.isHistoricalContext() && "SUPPORT".equals(evidence.getStance());
    }

    public String capConfidence(String requested, List<AttributionEvidence> evidences, LocalDate reportDate) {
        return capConfidence(requested, evidences, reportDate, reportDate == null ? null : reportDate.minusDays(3));
    }

    public String capConfidence(String requested, List<AttributionEvidence> evidences, LocalDate reportDate,
                                LocalDate startDate) {
        boolean directAuthority = false;
        boolean hasCounter = false;
        Map<String, Boolean> domains = new LinkedHashMap<>();
        if (evidences != null) {
            for (AttributionEvidence evidence : evidences) {
                if ("COUNTER".equals(evidence.getStance())) {
                    hasCounter = true;
                }
                if (!isRecentSupport(evidence, reportDate, startDate)) {
                    continue;
                }
                String host = domain(evidence.getUrl());
                if (StringUtils.isBlank(host)) {
                    continue;
                }
                if ("T1".equals(evidence.getSourceTier()) && "DIRECT".equals(evidence.getDirectness())) {
                    directAuthority = true;
                }
                if ("T1".equals(evidence.getSourceTier()) || "T2".equals(evidence.getSourceTier())) {
                    domains.put(host, Boolean.TRUE);
                }
            }
        }
        if (!"HIGH".equals(requested) && !"MID".equals(requested)) {
            return "LOW";
        }
        if (hasCounter || (!directAuthority && domains.size() < 2)) {
            return "LOW";
        }
        return "HIGH".equals(requested) && directAuthority && domains.size() >= 2 ? "HIGH" : "MID";
    }

    private LocalDate publishedDate(AttributionEvidence evidence) {
        String value = evidence.getPublishedAt();
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String date = value.trim();
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}([T ].*)?")) {
            return null;
        }
        try {
            return LocalDate.parse(date.substring(0, 10));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String evidenceKey(AttributionEvidence evidence) {
        String url = normalizeUrl(evidence.getUrl());
        return StringUtils.isBlank(url) ? "title:" + StringUtils.firstNonBlank(evidence.getTitle(), "").trim().toLowerCase(Locale.ROOT) : "url:" + url;
    }

    private String normalizeUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String query = uri.getQuery();
            String retained = query == null ? "" : Arrays.stream(query.split("&"))
                    .filter(part -> !part.toLowerCase(Locale.ROOT).startsWith("utm_"))
                    .reduce((a, b) -> a + "&" + b).orElse("");
            return StringUtils.firstNonBlank(uri.getScheme(), "https").toLowerCase(Locale.ROOT) + "://"
                    + StringUtils.firstNonBlank(uri.getHost(), "").toLowerCase(Locale.ROOT)
                    + StringUtils.firstNonBlank(uri.getPath(), "") + (retained.isEmpty() ? "" : "?" + retained);
        } catch (Exception ex) { return url.trim().toLowerCase(Locale.ROOT); }
    }

    private int score(AttributionEvidence evidence) {
        int tier = "T1".equals(evidence.getSourceTier()) ? 300 : "T2".equals(evidence.getSourceTier()) ? 200 : 100;
        int direct = "DIRECT".equals(evidence.getDirectness()) ? 30 : "INDIRECT".equals(evidence.getDirectness()) ? 10 : 0;
        return tier + direct + (evidence.getRelevance() == null ? 0 : evidence.getRelevance());
    }

    private String domain(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return "";
            }
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException | NullPointerException ex) {
            return "";
        }
    }
}
