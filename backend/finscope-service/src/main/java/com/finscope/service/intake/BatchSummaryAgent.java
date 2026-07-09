package com.finscope.service.intake;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.intake.FetchBatch;
import com.finscope.domain.intake.IntakeCandidate;
import com.finscope.domain.intake.IntakeEnums;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class BatchSummaryAgent {
    @Resource
    private LlmChatClient llmChatClient;
    @Resource
    private AgentRunRepository agentRunRepository;

    public BatchSummary summarize(FetchBatch batch, List<IntakeCandidate> candidates) {
        long start = System.currentTimeMillis();
        BatchSummary fallback = fallback(candidates);
        if (llmChatClient == null || !llmChatClient.isConfigured()) {
            record("FALLBACK", batch, fallback, "LLM_UNCONFIGURED", start);
            return fallback;
        }
        record("FALLBACK", batch, fallback, "BATCH_LLM_DISABLED_IN_PHASE_1", start);
        return fallback;
    }

    private BatchSummary fallback(List<IntakeCandidate> candidates) {
        int total = candidates == null ? 0 : candidates.size();
        int high = 0;
        int low = 0;
        int duplicate = 0;
        if (candidates != null) {
            for (IntakeCandidate candidate : candidates) {
                if (candidate.getAgentScore() >= 70) {
                    high++;
                }
                if (IntakeEnums.AGENT_LOW_VALUE.equals(candidate.getAgentRecommendation())) {
                    low++;
                }
                if (IntakeEnums.AGENT_DUPLICATE.equals(candidate.getAgentRecommendation())) {
                    duplicate++;
                }
            }
        }
        int review = Math.max(0, total - high - low - duplicate);
        String text = "本批共 " + total + " 条候选，高分 " + high + " 条，需复核 " + review
                + " 条，低价值 " + low + " 条，重复 " + duplicate + " 条。";
        return new BatchSummary("{\"summaryText\":\"" + text + "\"}", text);
    }

    private void record(String status, FetchBatch batch, BatchSummary summary, String errorMessage, long start) {
        agentRunRepository.record("batch-summary", status,
                "batchId=" + (batch == null ? "" : batch.getId()),
                summary == null ? "" : summary.getSummaryText(),
                errorMessage,
                System.currentTimeMillis() - start);
    }

    public static class BatchSummary {
        private final String summaryJson;
        private final String summaryText;

        public BatchSummary(String summaryJson, String summaryText) {
            this.summaryJson = summaryJson;
            this.summaryText = summaryText;
        }

        public String getSummaryJson() {
            return summaryJson;
        }

        public String getSummaryText() {
            return summaryText;
        }
    }
}
