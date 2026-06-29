package com.finscope.service.research;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.service.dedupe.FingerprintService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EventClassifierTest {
    private final EventClassifier classifier = new EventClassifier(new FingerprintService());

    @Test
    void classifiesFollowUpWhenSameEventAddsNewNumbers() {
        Article first = article(
                "美联储暗示降息 黄金ETF获得资金流入",
                "美联储释放偏鸽信号，黄金ETF继续获得资金流入。",
                "美联储官员暗示年内可能降息，黄金ETF继续吸引避险资金。");
        Article followUp = article(
                "美联储降息预期升温 黄金ETF单周流入12亿美元",
                "美联储降息预期继续升温，黄金ETF单周净流入12亿美元。",
                "市场继续交易美联储降息预期，黄金ETF单周净流入12亿美元，创下阶段新高。");

        EventCluster candidate = candidateFrom(first);
        EventClassifier.EventSignature signature = classifier.signature(followUp);

        EventClassifier.MatchDecision decision = classifier.decide(followUp, signature, Collections.singletonList(candidate));

        assertSame(candidate, decision.getEvent());
        assertEquals(ResearchEnums.NOVELTY_FOLLOW_UP, decision.getNoveltyType());
    }

    @Test
    void classifiesRecapWhenSameEventHasNoMeaningfulNewVariable() {
        Article first = article(
                "美联储暗示降息 黄金ETF获得资金流入",
                "美联储释放偏鸽信号，黄金ETF继续获得资金流入。",
                "美联储官员暗示年内可能降息，黄金ETF继续吸引避险资金。");
        Article recap = article(
                "市场继续关注美联储降息预期 黄金维持强势",
                "市场继续讨论美联储降息预期，黄金维持强势。",
                "媒体继续围绕美联储降息预期和黄金走强进行复盘，没有新增数据披露。");

        EventCluster candidate = candidateFrom(first);
        EventClassifier.EventSignature signature = classifier.signature(recap);

        EventClassifier.MatchDecision decision = classifier.decide(recap, signature, Collections.singletonList(candidate));

        assertSame(candidate, decision.getEvent());
        assertEquals(ResearchEnums.NOVELTY_RECAP, decision.getNoveltyType());
    }

    @Test
    void classifiesNewWhenEventIsDifferent() {
        Article first = article(
                "美联储暗示降息 黄金ETF获得资金流入",
                "美联储释放偏鸽信号，黄金ETF继续获得资金流入。",
                "美联储官员暗示年内可能降息，黄金ETF继续吸引避险资金。");
        Article different = article(
                "宁德时代提交港股上市申请",
                "宁德时代向港交所提交上市申请。",
                "宁德时代正式向港交所递交上市申请文件，募资用途聚焦海外产能扩张。");
        EventCluster candidate = candidateFrom(first);
        EventClassifier.EventSignature signature = classifier.signature(different);

        EventClassifier.MatchDecision decision = classifier.decide(different, signature, Collections.singletonList(candidate));

        assertNull(decision.getEvent());
        assertEquals(ResearchEnums.NOVELTY_NEW, decision.getNoveltyType());
    }

    private EventCluster candidateFrom(Article article) {
        EventClassifier.EventSignature signature = classifier.signature(article);
        EventCluster event = new EventCluster();
        event.setId(1L);
        event.setCanonicalTitle(article.getTitle());
        event.setCanonicalEventKey(signature.getCanonicalEventKey());
        event.setThemeCode(signature.getThemeCode());
        event.setSummary(article.getSummary());
        event.setStatus(ResearchEnums.EVENT_ACTIVE);
        event.setFirstSeenAt(LocalDateTime.of(2026, 6, 27, 9, 0));
        event.setLastSeenAt(LocalDateTime.of(2026, 6, 27, 9, 0));
        event.setLastMeaningfulUpdateAt(LocalDateTime.of(2026, 6, 27, 9, 0));
        event.setImportanceScore(signature.getImportanceScore());
        event.setNoveltyState(ResearchEnums.NOVELTY_NEW);
        event.setEvidenceCount(0);
        event.setArticleCount(1);
        return event;
    }

    private Article article(String title, String summary, String body) {
        Article article = Article.createFetched(1L, "Test Source", title, "https://example.com/" + title.hashCode(),
                LocalDateTime.of(2026, 6, 27, 9, 0), summary, body);
        article.setCategory("宏观");
        return article;
    }
}
