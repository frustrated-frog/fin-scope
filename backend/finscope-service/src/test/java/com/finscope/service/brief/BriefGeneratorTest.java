package com.finscope.service.brief;

import com.finscope.domain.article.Article;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

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

        assertTrue(markdown.contains("# FinScope Daily Brief - 2026-06-23"));
        assertTrue(markdown.contains("## 宏观"));
        assertTrue(markdown.contains("美联储释放降息信号"));
        assertFalse(markdown.contains("重复内容"));
        assertTrue(markdown.contains("## 今日学习问题"));
    }
}
