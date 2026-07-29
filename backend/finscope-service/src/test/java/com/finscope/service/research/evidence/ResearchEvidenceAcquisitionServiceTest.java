package com.finscope.service.research.evidence;

import com.finscope.domain.research.ResearchSourceDocument;
import com.finscope.rpc.research.ResearchSourceReader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchEvidenceAcquisitionServiceTest {

    @Test
    void keepsQuestionRelevantFullTextChunksInsteadOfSearchSnippet() {
        ResearchSourceReader reader = url -> new ResearchSourceDocument(url, "上市公告", paragraphs(),
                "text/html", "web:generic-score", "FETCHED");
        ResearchEvidenceAcquisitionService service = new ResearchEvidenceAcquisitionService(
                reader, new ResearchEvidenceChunker(180, 30), new ResearchEvidenceRanker());

        ResearchEvidenceAcquisitionResult result = service.acquire(
                "https://example.com/a", "长鑫科技 IPO 募集资金用途", "搜索摘要", "长鑫科技");

        assertEquals("FULL_TEXT", result.getContentOrigin());
        assertEquals("FETCHED", result.getFetchStatus());
        assertTrue(result.getContent().contains("募集资金将投入先进制程研发"));
        assertFalse(result.getContent().contains("天气晴朗，园区食堂更新菜单"));
        assertEquals("搜索摘要", result.getSearchSnippet());
        assertTrue(result.getContentCharCount() > result.getContent().length());
    }

    @Test
    void marksSnippetFallbackWhenSourceReadingFails() {
        ResearchSourceReader reader = url -> {
            throw new IllegalStateException("站点拒绝访问");
        };
        ResearchEvidenceAcquisitionService service = new ResearchEvidenceAcquisitionService(
                reader, new ResearchEvidenceChunker(), new ResearchEvidenceRanker());

        ResearchEvidenceAcquisitionResult result = service.acquire(
                "https://example.com/a", "公司公告", "搜索摘要仍可用于降级", "示例公司");

        assertEquals("SEARCH_SNIPPET", result.getContentOrigin());
        assertEquals("FAILED", result.getFetchStatus());
        assertEquals("搜索摘要仍可用于降级", result.getContent());
        assertTrue(result.getExtractionMethod().contains("fallback"));
    }

    @Test
    void chunkerProducesBoundedOrderedChunks() {
        ResearchEvidenceChunker chunker = new ResearchEvidenceChunker(90, 15);

        List<ResearchEvidenceChunk> chunks = chunker.chunk(paragraphs());

        assertTrue(chunks.size() >= 3);
        assertEquals(0, chunks.get(0).getIndex());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getText().length() <= 105));
    }

    private String paragraphs() {
        return "公司园区介绍。天气晴朗，园区食堂更新菜单，员工活动将在周五举行。\n\n"
                + "长鑫科技披露 IPO 募集资金将投入先进制程研发、存储芯片产能建设与技术平台升级，"
                + "其中研发项目强调工艺迭代和核心设备验证。\n\n"
                + "风险提示显示项目建设周期较长，市场价格波动可能影响新增产能的盈利水平。";
    }
}
