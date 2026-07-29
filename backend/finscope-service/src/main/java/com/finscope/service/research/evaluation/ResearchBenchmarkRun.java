package com.finscope.service.research.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResearchBenchmarkRun {
    private final String version;
    private final List<ResearchBenchmarkCaseResult> cases;

    ResearchBenchmarkRun(String version, List<ResearchBenchmarkCaseResult> cases) {
        this.version = version;
        this.cases = Collections.unmodifiableList(new ArrayList<ResearchBenchmarkCaseResult>(cases));
    }

    public String getVersion() { return version; }
    public List<ResearchBenchmarkCaseResult> getCases() { return cases; }

    public String toCanonicalJson() {
        try {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("version", version);
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (ResearchBenchmarkCaseResult item : cases) items.add(item.canonical());
            value.put("cases", items);
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Benchmark 结果序列化失败", error);
        }
    }
}
