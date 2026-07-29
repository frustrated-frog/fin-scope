package com.finscope.service.research.report;

import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchReportRepairAgentTest {

    @Test
    void invokesModelAtMostOnceAndAcceptsOnlyWhitelistedCitations() {
        AtomicInteger calls = new AtomicInteger();
        LlmChatClient client = client(calls, "## 核心结论\n\n公司收入增长18%。[E99]");
        ResearchReportRepairAgent agent = new ResearchReportRepairAgent(client);
        String original = "## 核心结论\n\n公司收入增长25%。[E1]";
        ResearchClaimAudit audit = new ResearchClaimAuditor(new ResearchClaimExtractor()).audit(original,
                Collections.singletonList(evidence("E1", "公司收入增长18%。")));

        String repaired = agent.repair(original, audit, Collections.singletonList(
                evidence("E1", "公司收入增长18%。")));

        assertEquals(1, calls.get());
        assertFalse(repaired.contains("E99"));
        assertFalse(repaired.contains("25%"));
        assertTrue(repaired.contains("待验证"));
    }

    @Test
    void usesSingleValidModelRepairWithoutInventingEvidence() {
        AtomicInteger calls = new AtomicInteger();
        LlmChatClient client = client(calls, "## 核心结论\n\n公司收入增长18%。[E1]");
        ResearchReportRepairAgent agent = new ResearchReportRepairAgent(client);
        String original = "## 核心结论\n\n公司收入增长25%。[E1]";
        ResearchClaimAudit audit = new ResearchClaimAuditor(new ResearchClaimExtractor()).audit(original,
                Collections.singletonList(evidence("E1", "公司收入增长18%。")));

        String repaired = agent.repair(original, audit, Collections.singletonList(
                evidence("E1", "公司收入增长18%。")));

        assertEquals(1, calls.get());
        assertTrue(repaired.contains("18%"));
        assertTrue(repaired.contains("[E1]"));
    }

    private LlmChatClient client(AtomicInteger calls, String output) {
        return new LlmChatClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String modelName() { return "test"; }
            @Override public String complete(String systemPrompt, String userPrompt) {
                calls.incrementAndGet();
                return output;
            }
        };
    }

    private ResearchEvidenceDossier evidence(String ref, String excerpt) {
        return new ResearchEvidenceDossier(ref, null, null, "example.com", "示例来源", "T2",
                "示例材料", null, "https://example.com/" + ref, excerpt, "SUPPORT", 90);
    }
}
