package com.finscope.service.brief;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.BriefResearchContext;
import com.finscope.domain.research.ContentIdea;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.research.ResearchEnums;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BriefGeneratorTest {
    @Test
    void generatesStructuredMarkdownAndSkipsDuplicates() {
        Article macro = Article.createFetched(1L, "Source A", "美联储释放降息信号", "https://a.test/1",
                LocalDateTime.of(2026, 6, 23, 9, 0), "宏观摘要", "宏观正文");
        macro.setNoveltyType("NEW");
        macro.setCategory("宏观");

        Article duplicate = Article.createFetched(1L, "Source A", "重复内容", "https://a.test/2",
                LocalDateTime.of(2026, 6, 23, 10, 0), "重复摘要", "重复正文");
        duplicate.setNoveltyType("DUPLICATE");
        duplicate.setNoveltyReason("标题与昨日内容相似");

        String markdown = new BriefGenerator().generate(LocalDate.of(2026, 6, 23), Arrays.asList(macro, duplicate));

        assertTrue(markdown.contains("# 每日金融、投资、创业学习简报 - 2026-06-23"));
        assertTrue(markdown.contains("## 宏观"));
        assertTrue(markdown.contains("美联储释放降息信号"));
        assertFalse(markdown.contains("重复内容"));
        assertTrue(markdown.contains("## 今日学习问题"));
    }

    @Test
    void generatesResearchBriefSectionsWhenStructuredContextExists() {
        BriefResearchContext context = new BriefResearchContext();
        context.setBriefDate(LocalDate.of(2026, 6, 23));

        EventCluster event = new EventCluster();
        event.setId(1L);
        event.setCanonicalTitle("美联储降息预期升温，黄金ETF获增量资金");
        event.setThemeCode(ResearchEnums.THEME_CHINA_MACRO);
        event.setSummary("黄金ETF单周流入12亿美元，实际利率预期下行。");
        event.setNoveltyState(ResearchEnums.NOVELTY_FOLLOW_UP);
        event.setImportanceScore(86);
        event.setEvidenceCount(2);
        context.setEvents(Collections.singletonList(event));

        EvidenceItem evidence = new EvidenceItem();
        evidence.setEventId(1L);
        evidence.setSourceTier(ResearchEnums.SOURCE_TIER_MEDIA);
        evidence.setEvidenceType(ResearchEnums.EVIDENCE_DATA);
        evidence.setClaim("黄金ETF单周流入12亿美元。");
        evidence.setConfidence(75);
        context.setEvidenceItems(Collections.singletonList(evidence));

        LearningTask task = new LearningTask();
        task.setEventId(1L);
        task.setThemeCode(ResearchEnums.THEME_CHINA_MACRO);
        task.setQuestion("为什么实际利率下行会推升黄金配置需求？");
        task.setStatus(ResearchEnums.LEARNING_STATUS_TODO);
        context.setLearningTasks(Collections.singletonList(task));

        ContentIdea idea = new ContentIdea();
        idea.setEventId(1L);
        idea.setTitle("黄金为什么会对降息预期提前反应");
        idea.setAngle("用实际利率框架解释黄金的领先反应。");
        idea.setFormat("THREAD");
        idea.setScore(82);
        idea.setOutline("1. 先看降息预期\n2. 再看实际利率\n3. 最后看资金流向");
        context.setContentIdeas(Collections.singletonList(idea));

        String markdown = new BriefGenerator().generate(LocalDate.of(2026, 6, 23), Collections.<Article>emptyList(), context);

        assertTrue(markdown.contains("# 每日金融、投资、创业学习简报 - 2026-06-23"));
        assertTrue(markdown.contains("定位：帮助建立长期判断力，不提供具体买卖建议。"));
        assertTrue(markdown.contains("## 今日新变量"));
        assertTrue(markdown.contains("## 事件追踪"));
        assertTrue(markdown.contains("## 中国宏观"));
        assertTrue(markdown.contains("中国宏观 / FOLLOW_UP / 重要性 86 / 证据 2"));
        assertTrue(markdown.contains("## 今日证据来源"));
        assertTrue(markdown.contains("## 今天要补的金融知识"));
        assertTrue(markdown.contains("## 可发展为自媒体选题"));
        assertTrue(markdown.contains("## 今日思考题"));
        assertTrue(markdown.contains("黄金ETF单周流入12亿美元"));
        assertTrue(markdown.contains("为什么实际利率下行会推升黄金配置需求"));
    }
}
