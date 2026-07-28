package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchEvidenceDossierBuilderTest {
    private final ResearchEvidenceDossierBuilder builder = new ResearchEvidenceDossierBuilder();

    @Test
    void buildsStableEvidenceReferencesWithRichBoundedExcerpts() {
        List<ResearchEvidenceCard> cards = new ArrayList<ResearchEvidenceCard>();
        for (int index = 0; index < 14; index++) {
            Article article = new Article();
            article.setId((long) index + 1);
            article.setSourceName(index % 3 == 0 ? "公司公告" : "财经媒体 " + index);
            article.setTitle("长鑫科技上市交易事实 " + index);
            article.setPublishedAt(LocalDateTime.of(2026, 7, 29, 9, index));
            article.setSummary("摘要记录首日成交额与换手率变化 " + index);
            article.setBody("正文进一步解释发行流通结构、市值口径和成交活跃度。" + repeat("有效事实", 100));
            article.setUrl("https://example.com/" + index);
            cards.add(new ResearchEvidenceCard(article, null, index == 13 ? "COUNTER" : "SUPPORT",
                    90 - index, "首日成交额事实 " + index));
        }

        List<ResearchEvidenceDossier> dossier = builder.build(cards);

        assertEquals(12, dossier.size());
        assertEquals("E1", dossier.get(0).getEvidenceRef());
        assertEquals("E12", dossier.get(11).getEvidenceRef());
        assertTrue(dossier.get(0).getFactExcerpt().contains("首日成交额事实"));
        assertTrue(dossier.get(0).getFactExcerpt().contains("发行流通结构"));
        assertTrue(dossier.stream().anyMatch(item -> "COUNTER".equals(item.getStance())));
        assertTrue(builder.promptCharacters(dossier) <= ResearchEvidenceDossierBuilder.MAX_PROMPT_CHARACTERS);
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
