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

    @Test
    void rejectsARepairThatDropsTheStructuredReportContract() {
        AtomicInteger calls = new AtomicInteger();
        LlmChatClient client = client(calls, "## 核心结论\n\n公司收入增长18%。");
        ResearchReportRepairAgent agent = new ResearchReportRepairAgent(client);
        String original = "# 公司深度研究报告\n\n"
                + "## 核心结论\n\n公司收入增长25%。[E1]\n\n"
                + "## 核心证据链\n\n事实、推理与判断。[E1]\n\n"
                + "## 反方解释与争议\n\n反方解释仍需验证。[E1]\n\n"
                + "## 证据附录\n\n### E1 · 示例材料\n\n公司收入增长18%。";
        ResearchClaimAudit audit = new ResearchClaimAuditor(new ResearchClaimExtractor()).audit(original,
                Collections.singletonList(evidence("E1", "公司收入增长18%。")));

        String repaired = agent.repair(original, audit, Collections.singletonList(
                evidence("E1", "公司收入增长18%。")));

        assertEquals(1, calls.get());
        assertTrue(repaired.contains("## 核心证据链"));
        assertTrue(repaired.contains("## 反方解释与争议"));
        assertTrue(repaired.contains("## 证据附录"));
        assertTrue(repaired.contains("[E1]"));
        assertFalse(repaired.contains("25%"));
    }

    @Test
    void deterministicRepairProducesAnAuditableCaveatInsteadOfAnotherUnsupportedClaim() {
        ResearchReportRepairAgent agent = new ResearchReportRepairAgent(null);
        String original = "## 核心结论\n\n公司收入增长25%。[E1]";
        java.util.List<ResearchEvidenceDossier> dossier = Collections.singletonList(
                evidence("E1", "公司收入增长18%。"));
        ResearchClaimAuditor auditor = new ResearchClaimAuditor(new ResearchClaimExtractor());
        ResearchClaimAudit audit = auditor.audit(original, dossier);

        String repaired = agent.repair(original, audit, dossier);
        ResearchClaimAudit repairedAudit = auditor.audit(repaired, dossier);

        assertFalse(repairedAudit.hasBlockingIssues());
        assertTrue(repaired.contains("**审计降级：**"));
        assertTrue(repaired.contains("[E1]"));
        assertFalse(repaired.contains("25%"));
    }

    @Test
    void sanitizesRemainingUnsupportedClaimsAfterAWellFormedModelRepair() {
        AtomicInteger calls = new AtomicInteger();
        LlmChatClient client = client(calls, "## 核心结论\n\n公司收入增长24%。[E1]");
        ResearchReportRepairAgent agent = new ResearchReportRepairAgent(client);
        String original = "## 核心结论\n\n公司收入增长25%。[E1]";
        java.util.List<ResearchEvidenceDossier> dossier = Collections.singletonList(
                evidence("E1", "公司收入增长18%。"));
        ResearchClaimAuditor auditor = new ResearchClaimAuditor(new ResearchClaimExtractor());

        String repaired = agent.repair(original, auditor.audit(original, dossier), dossier);

        assertEquals(1, calls.get());
        assertFalse(auditor.audit(repaired, dossier).hasBlockingIssues());
        assertTrue(repaired.contains("**审计降级：**"));
        assertFalse(repaired.contains("24%"));
        assertFalse(repaired.contains("25%"));
    }

    @Test
    void deterministicCaveatIsNotReauditedWhenItFollowsASupportedSentenceOnTheSameLine() {
        ResearchReportRepairAgent agent = new ResearchReportRepairAgent(null);
        String original = "## 核心结论\n\n公司收入增长18%。[E1] 公司利润增长25%。[E1]";
        java.util.List<ResearchEvidenceDossier> dossier = Collections.singletonList(
                evidence("E1", "公司收入增长18%。"));
        ResearchClaimAuditor auditor = new ResearchClaimAuditor(new ResearchClaimExtractor());

        String repaired = agent.repair(original, auditor.audit(original, dossier), dossier);

        assertFalse(auditor.audit(repaired, dossier).hasBlockingIssues());
        assertTrue(repaired.contains("公司收入增长18%"));
        assertTrue(repaired.contains("**审计降级：**"));
        assertFalse(repaired.contains("利润增长25%"));
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
