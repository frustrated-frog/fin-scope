package com.finscope.service.radar;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.agent.AgentRun;
import org.springframework.stereotype.Component;

@Component
public class RadarAgentTraceRecorder {
    private final AgentRunRepository runs;

    public RadarAgentTraceRecorder(AgentRunRepository runs) { this.runs = runs; }

    public void record(String nodeName, String subjectType, Long subjectId, String status,
                       String input, String output, String errorType, String fallbackReason,
                       long durationMs, String metadataJson) {
        AgentRun run = new AgentRun();
        run.setNodeName(nodeName);
        run.setSubjectType(subjectType);
        run.setSubjectId(subjectId);
        run.setStatus(status);
        run.setInput(limit(input, 800));
        run.setOutput(limit(output, 800));
        run.setErrorMessage(errorType == null ? "" : errorType);
        run.setErrorType(errorType);
        run.setFallbackUsed(fallbackReason != null && !fallbackReason.isEmpty());
        run.setFallbackReason(fallbackReason);
        run.setDurationMs(Math.max(0L, durationMs));
        run.setAttempt(1);
        run.setMetadataJson(metadataJson == null ? "{}" : metadataJson);
        try {
            runs.record(run);
        } catch (RuntimeException ignored) {
            // 轨迹是可观测性增强，持久化失败不能阻断雷达主流程。
        }
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max);
    }
}
