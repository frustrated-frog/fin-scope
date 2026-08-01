package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RadarEvidenceTrustService {
    private static final Pattern NUMBER_UNIT = Pattern.compile("(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*(亿美元|万美元|亿元|万元|个基点|万辆|万台|美元|bps|BP|%|元|吨|台|家|人|倍)");

    public RadarEventWorkspace.Trust assess(List<RadarSignal> signals, List<RadarEvidence> evidence,
                                             RadarEventInterpretation interpretation) {
        RadarEventWorkspace.Trust trust = new RadarEventWorkspace.Trust();
        Set<String> sources = new LinkedHashSet<String>(); Map<String,Integer> tiers = new LinkedHashMap<String,Integer>();
        Map<String,Map<String,Set<String>>> numbers = new LinkedHashMap<String,Map<String,Set<String>>>();
        Set<String> validRefs = new LinkedHashSet<String>();
        if (signals != null) for (RadarSignal item : signals) {
            String source = source(item.getUrl(), item.getSourceName()); sources.add(source); tier(tiers, item.getSourceTier());
            validRefs.add("signal:" + item.getId()); numbers(numbers, source, text(item.getTitle(), item.getContent()));
        }
        if (evidence != null) for (RadarEvidence item : evidence) {
            String source = source(item.getUrl(), item.getSourceName()); sources.add(source); tier(tiers, item.getSourceTier());
            validRefs.add("evidence:" + item.getId()); numbers(numbers, source, text(item.getTitle(), item.getSummary()));
        }
        trust.setIndependentSourceCount(sources.size()); trust.setSourceTierCounts(tiers);
        List<String> refs = interpretation == null || interpretation.getResult() == null
                ? new ArrayList<String>() : interpretation.getResult().getEvidenceRefs();
        int covered = 0; Set<String> counted = new LinkedHashSet<String>();
        for (String ref : refs) if (validRefs.contains(ref) && counted.add(ref)) covered++;
        trust.setCitationCoveredCount(covered); trust.setCitationTotalCount(refs.size());
        trust.setConcentration(sources.size() <= 1 ? "单一来源" : sourceConcentration(signals, evidence, sources.size()));
        trust.setConflicts(conflicts(numbers)); return trust;
    }

    private String sourceConcentration(List<RadarSignal> signals, List<RadarEvidence> evidence, int sourceCount) {
        Map<String,Integer> counts = new LinkedHashMap<String,Integer>(); int total = 0, max = 0;
        if (signals != null) for (RadarSignal item : signals) { String key=source(item.getUrl(),item.getSourceName()); int n=counts.getOrDefault(key,0)+1;counts.put(key,n);max=Math.max(max,n);total++; }
        if (evidence != null) for (RadarEvidence item : evidence) { String key=source(item.getUrl(),item.getSourceName()); int n=counts.getOrDefault(key,0)+1;counts.put(key,n);max=Math.max(max,n);total++; }
        return sourceCount > 1 && total > 0 && max * 10 >= total * 6 ? "来源较集中" : "来源较分散";
    }

    private void numbers(Map<String,Map<String,Set<String>>> result, String source, String content) {
        Matcher matcher = NUMBER_UNIT.matcher(content == null ? "" : content);
        while (matcher.find()) {
            String value = matcher.group(1) + matcher.group(2).toLowerCase(Locale.ROOT);
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            result.computeIfAbsent(unit, key -> new LinkedHashMap<String,Set<String>>())
                    .computeIfAbsent(value, key -> new LinkedHashSet<String>()).add(source);
        }
    }

    private List<String> conflicts(Map<String,Map<String,Set<String>>> values) {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String,Map<String,Set<String>>> unit : values.entrySet()) {
            if (unit.getValue().size() < 2) continue;
            Set<String> sources = new LinkedHashSet<String>(); for (Set<String> item : unit.getValue().values()) sources.addAll(item);
            if (sources.size() > 1) result.add("不同来源出现不一致数值：" + String.join("、", unit.getValue().keySet()));
        }
        return result;
    }

    private String source(String url, String name) {
        try { String host = url == null ? null : URI.create(url).getHost(); if (host != null && !host.trim().isEmpty()) return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", ""); }
        catch (IllegalArgumentException ignored) { }
        String value = name == null ? "未知来源" : name.trim().toLowerCase(Locale.ROOT); return value.isEmpty() ? "未知来源" : value;
    }
    private void tier(Map<String,Integer> values, String tier) { String key=tier==null||tier.trim().isEmpty()?"UNKNOWN":tier;values.put(key,values.getOrDefault(key,0)+1); }
    private String text(String first, String second) { return String.valueOf(first) + ' ' + String.valueOf(second); }
}
