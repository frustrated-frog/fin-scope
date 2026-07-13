package com.finscope.service.research.report;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.ResearchRunOutputRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunOutput;
import com.finscope.domain.research.ResearchThesis;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunScopedResearchContextServiceTest {
    @Test
    void loadsOnlyOutputsExplicitlyOwnedByTheRun() {
        ResearchRunRepository runRepository = mock(ResearchRunRepository.class);
        ResearchThesisRepository thesisRepository = mock(ResearchThesisRepository.class);
        ResearchRunOutputRepository outputRepository = mock(ResearchRunOutputRepository.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EventClusterRepository eventRepository = mock(EventClusterRepository.class);
        EvidenceItemRepository evidenceRepository = mock(EvidenceItemRepository.class);
        ResearchRun run = new ResearchRun();
        run.setId(14L);
        run.setThesisId(1L);
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(1L);
        Article article = new Article();
        article.setId(24L);
        EventCluster event = new EventCluster();
        event.setId(15L);
        EvidenceItem evidence = new EvidenceItem();
        evidence.setId(8L);
        evidence.setArticleId(24L);
        when(runRepository.findById(14L)).thenReturn(Optional.of(run));
        when(thesisRepository.findById(1L)).thenReturn(Optional.of(thesis));
        when(runRepository.findSourcesByRunId(14L)).thenReturn(Collections.emptyList());
        when(outputRepository.findByRunId(14L)).thenReturn(Arrays.asList(
                output("ARTICLE", 24L), output("EVENT", 15L), output("EVIDENCE", 8L)));
        when(articleRepository.findById(24L)).thenReturn(Optional.of(article));
        when(eventRepository.findById(15L)).thenReturn(Optional.of(event));
        when(evidenceRepository.findById(8L)).thenReturn(Optional.of(evidence));

        RunScopedResearchContext context = new RunScopedResearchContextService(runRepository, thesisRepository,
                outputRepository, articleRepository, eventRepository, evidenceRepository).load(14L);

        assertEquals(Collections.singletonList(24L), context.getArticleIds());
        assertEquals(Collections.singletonList(15L), context.getEventIds());
        assertEquals(Collections.singletonList(8L), context.getEvidenceIds());
        verify(articleRepository, never()).findById(99L);
    }

    private ResearchRunOutput output(String type, Long id) {
        ResearchRunOutput output = new ResearchRunOutput();
        output.setResearchRunId(14L);
        output.setOutputType(type);
        output.setOutputId(id);
        return output;
    }
}
