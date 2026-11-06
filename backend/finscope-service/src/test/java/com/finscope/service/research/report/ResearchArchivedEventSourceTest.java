package com.finscope.service.research.report;

import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ResearchArchivedEventSourceTest {
    @Test
    void reusesOnlyTheExactQuotedArchivedEventBeforeTheRunStarted() {
        MajorEventRepository repository = mock(MajorEventRepository.class);
        ResearchArchivedEventSource service = new ResearchArchivedEventSource();
        ReflectionTestUtils.setField(service, "repository", repository);
        ResearchRun run = new ResearchRun();
        run.setCreatedAt(LocalDateTime.of(2026, 8, 6, 18, 0));
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("围绕“长鑫科技大宗交易4.3亿元”，哪些事实已经确认，后续应重点观察什么？");
        MajorEvent event = new MajorEvent();
        event.setId(21L);
        event.setTitle("长鑫科技大宗交易4.3亿元");
        event.setSummary("8月6日，长鑫科技大宗交易成交828.08万股，成交额4.3亿元。");
        event.setSourceName("界面新闻");
        event.setOccurredDate(LocalDate.of(2026, 8, 6));
        when(repository.findUniqueTitleBefore(event.getTitle(), run.getCreatedAt())).thenReturn(Optional.of(event));
        RunScopedResearchContext context = new RunScopedResearchContext(run, thesis, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertEquals(1, service.articlesFor(context).size());
        assertTrue(service.articlesFor(context).get(0).getSummary().contains("发生日期：2026-08-06"));
        assertTrue(context.getArticles().isEmpty());
        verify(repository, times(2)).findUniqueTitleBefore(event.getTitle(), run.getCreatedAt());
    }

    @Test
    void doesNotSearchOtherArchivesForAGenericCompanyQuestion() {
        MajorEventRepository repository = mock(MajorEventRepository.class);
        ResearchArchivedEventSource service = new ResearchArchivedEventSource();
        ReflectionTestUtils.setField(service, "repository", repository);
        ResearchRun run = new ResearchRun();
        run.setCreatedAt(LocalDateTime.now());
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("长鑫科技盈利模式如何？");
        assertTrue(service.articlesFor(new RunScopedResearchContext(run, thesis, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList())).isEmpty());
        verifyNoInteractions(repository);
    }
}
