package com.finscope.service.attribution;

import com.finscope.common.util.StringUtils;
import com.finscope.domain.attribution.AttributionEvidence;

import java.net.URI;
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
                if (evidence == null) continue;
                String key = evidenceKey(evidence);
                evidence.setEventKey(key);
                AttributionEvidence existing = unique.get(key);
                if (existing == null || score(evidence) > score(existing)) unique.put(key, evidence);
            }
        }
        List<AttributionEvidence> result = new ArrayList<AttributionEvidence>(unique.values());
        result.sort(Comparator.comparingInt(this::score).reversed());
        return result;
    }

    public String capConfidence(String requested, List<AttributionEvidence> evidences) {
        boolean directAuthority = false;
        boolean independentT2 = false;
        if (evidences != null) {
            Map<String, Boolean> domains = new LinkedHashMap<String, Boolean>();
            for (AttributionEvidence evidence : evidences) {
                if (evidence.isHistoricalContext()) continue;
                if ("T1".equals(evidence.getSourceTier()) && "DIRECT".equals(evidence.getDirectness())) directAuthority = true;
                if ("T1".equals(evidence.getSourceTier()) || "T2".equals(evidence.getSourceTier())) domains.put(domain(evidence.getUrl()), Boolean.TRUE);
            }
            independentT2 = domains.size() >= 2;
        }
        if ("HIGH".equals(requested) && !(directAuthority && independentT2)) return directAuthority ? "MID" : "LOW";
        if ("MID".equals(requested) && !directAuthority && !independentT2) return "LOW";
        return requested == null ? "LOW" : requested;
    }

    private String evidenceKey(AttributionEvidence evidence) {
        String url = normalizeUrl(evidence.getUrl());
        return StringUtils.isBlank(url) ? "title:" + StringUtils.firstNonBlank(evidence.getTitle(), "").trim().toLowerCase(Locale.ROOT) : "url:" + url;
    }

    private String normalizeUrl(String url) {
        if (StringUtils.isBlank(url)) return "";
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
        try { return URI.create(url).getHost(); } catch (Exception ex) { return StringUtils.firstNonBlank(url, ""); }
    }
}
