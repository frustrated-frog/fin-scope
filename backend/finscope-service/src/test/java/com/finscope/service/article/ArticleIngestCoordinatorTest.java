package com.finscope.service.article;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ArticleIngestCoordinatorTest {
    @Test
    void fetchResearchIngestDoesNotWrapExternalAgentWorkInTransaction() throws Exception {
        Method method = ArticleIngestCoordinator.class.getMethod("ingest", Source.class, RawItem.class);

        assertFalse(method.isAnnotationPresent(Transactional.class),
                "Research fetch calls this overload; wrapping it in one transaction holds SQLite write locks while LLM work runs.");
    }
}
