package com.finscope.service.search.evidence;

import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchEvidenceContentServiceTest {

    @Test
    void enrichesEvidenceWithFullTextMetadata() {
        ResearchEvidenceAcquisitionService acquisition = mock(ResearchEvidenceAcquisitionService.class);
        when(acquisition.acquire("https://example.com/a", "query", "snippet", "subject"))
                .thenReturn(new ResearchEvidenceAcquisitionResult("full text", "snippet", "FULL_TEXT",
                        "html:readability", "SUCCESS", 9));
        SearchEvidenceContentService service = new SearchEvidenceContentService(acquisition);
        SearchEvidence evidence = evidence();

        ResearchEvidenceAcquisitionResult result = service.acquire(evidence, "query", "subject", true);

        assertEquals("full text", result.getContent());
        assertEquals("FULL_TEXT", result.getContentOrigin());
        verify(acquisition).acquire("https://example.com/a", "query", "snippet", "subject");
    }

    @Test
    void keepsSnippetWithoutCallingReaderWhenBudgetDisallowsFullText() {
        ResearchEvidenceAcquisitionService acquisition = mock(ResearchEvidenceAcquisitionService.class);
        SearchEvidenceContentService service = new SearchEvidenceContentService(acquisition);

        ResearchEvidenceAcquisitionResult result = service.acquire(evidence(), "query", "subject", false);

        assertEquals("snippet", result.getContent());
        assertEquals("SEARCH_SNIPPET", result.getContentOrigin());
        assertEquals("NOT_ATTEMPTED", result.getFetchStatus());
        verifyNoInteractions(acquisition);
    }

    private SearchEvidence evidence() {
        SearchEvidence evidence = new SearchEvidence();
        evidence.setUrl("https://example.com/a");
        evidence.setContent("snippet");
        return evidence;
    }
}
