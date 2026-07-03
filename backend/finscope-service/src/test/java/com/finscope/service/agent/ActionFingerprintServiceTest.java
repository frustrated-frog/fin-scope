package com.finscope.service.agent;

import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.article.Article;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ActionFingerprintServiceTest {
    private final ActionFingerprintService service = new ActionFingerprintService();

    @Test
    void fingerprintsSourceFetchBySourceId() {
        AgentActionFingerprint fingerprint = service.sourceFetch(12L);

        assertEquals("source-fetch", fingerprint.getNodeName());
        assertEquals("source", fingerprint.getTargetType());
        assertEquals("12", fingerprint.getTargetId());
        assertEquals("source-fetch:source:12", fingerprint.getFingerprint());
    }

    @Test
    void fingerprintsArticleInterpretByArticleIdAndBodyHash() {
        Article first = article(345L, "same body");
        Article sameBody = article(345L, "same body");
        Article changedBody = article(345L, "changed body");

        AgentActionFingerprint firstFingerprint = service.articleInterpret(first);
        AgentActionFingerprint sameFingerprint = service.articleInterpret(sameBody);
        AgentActionFingerprint changedFingerprint = service.articleInterpret(changedBody);

        assertEquals(firstFingerprint.getFingerprint(), sameFingerprint.getFingerprint());
        assertNotEquals(firstFingerprint.getFingerprint(), changedFingerprint.getFingerprint());
        assertEquals("article-interpret", firstFingerprint.getNodeName());
        assertEquals("article", firstFingerprint.getTargetType());
        assertEquals("345", firstFingerprint.getTargetId());
    }

    @Test
    void fingerprintsEvidenceExtractionByEventArticleAndBodyHash() {
        AgentActionFingerprint first = service.evidenceExtract(20L, article(345L, "market data"));
        AgentActionFingerprint same = service.evidenceExtract(20L, article(345L, "market data"));
        AgentActionFingerprint changed = service.evidenceExtract(20L, article(345L, "new market data"));

        assertEquals(first.getFingerprint(), same.getFingerprint());
        assertNotEquals(first.getFingerprint(), changed.getFingerprint());
        assertEquals("evidence-extract", first.getNodeName());
        assertEquals("event-article", first.getTargetType());
        assertEquals("20:345", first.getTargetId());
    }

    private Article article(Long id, String body) {
        Article article = Article.createFetched(null, "Manual", "Title", "https://example.com/" + id,
                LocalDateTime.of(2026, 7, 3, 9, 0), "Summary", body);
        article.setId(id);
        return article;
    }
}
