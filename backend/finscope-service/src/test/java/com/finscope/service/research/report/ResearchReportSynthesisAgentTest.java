package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchReportSynthesisAgentTest {
    @Test
    void rejectsUnstructuredOrOversizedModelOutputAndUsesBoundedFallback() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString())).thenReturn("无结构结果" + String.join("", Collections.nCopies(13000, "字")));
        ResearchReportSynthesisAgent agent = new ResearchReportSynthesisAgent();
        ReflectionTestUtils.setField(agent, "llmChatClient", llm);
        GeneratedResearchReport fallback = fallback();

        GeneratedResearchReport result = agent.refine(new ResearchThesis(), Collections.emptyList(), fallback);

        assertEquals("DETERMINISTIC", result.getGenerationMode());
        assertEquals(fallback.getMarkdown(), result.getMarkdown());
    }

    private GeneratedResearchReport fallback() {
        return new GeneratedResearchReport("标题", "阶段性结论", "MIXED", "LOW", "摘要",
                "# 标题\n\n## 核心结论\n\n阶段性结论\n\n## 执行摘要\n\n摘要\n\n## 命题拆解\n\n- A\n\n"
                        + "## 关键证据\n\n- A\n\n## 反方证据与风险\n\n- A\n\n## 结论边界与后续验证\n\n- A\n\n## 来源\n\n- A",
                "DETERMINISTIC");
    }
}
