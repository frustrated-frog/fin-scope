package com.finscope.service.factorresearch;

import com.finscope.dao.factorresearch.ResearchDraftRepository;
import com.finscope.domain.factorresearch.ResearchDraft;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchDraftServiceTest {
    private final ResearchDraftRepository repository = mock(ResearchDraftRepository.class);
    private final ResearchFactorCatalog catalog = new ResearchFactorCatalog();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T17:00:00Z"), ZoneOffset.UTC);
    private final ResearchDraftService service = new ResearchDraftService(repository, catalog, clock);

    @Test
    void createsAContextOnlyDraftWithServerControlledResearchBoundaries() {
        when(repository.save(any(ResearchDraft.class))).thenAnswer(invocation -> {
            ResearchDraft value = invocation.getArgument(0);
            value.setId(9L);
            return value;
        });

        ResearchDraft result = service.createFromCapitalSignal(new CapitalResearchDraftCommand(
                "600519.SH", "贵州茅台", LocalDateTime.of(2026, 7, 15, 15, 0),
                "PRICE_FLOW_DIVERGENCE", 42L, "snapshot-fingerprint",
                Arrays.asList("snapshot:42", "daily-flow:2026-07-15"),
                Collections.singletonList("PRICE_FLOW_DIVERGENCE")));

        assertEquals(9L, result.getId());
        assertEquals("capital:MAIN_FLOW_SHARE:1.0.0", result.getFactor().toString());
        assertEquals("CROSS_SECTIONAL_FACTOR_STUDY", result.getEvaluationMode());
        assertEquals("DRAFT", result.getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 15, 17, 0), result.getCreatedAt());
        assertEquals(3, result.getRequiredNextSteps().size());
        verify(repository).save(any(ResearchDraft.class));
    }

    @Test
    void rejectsARequestWithoutTraceableEvidence() {
        CapitalResearchDraftCommand command = new CapitalResearchDraftCommand(
                "600519.SH", "贵州茅台", LocalDateTime.of(2026, 7, 15, 15, 0),
                "MAIN_FLOW_OBSERVATION", 42L, "snapshot-fingerprint",
                Collections.emptyList(), Collections.emptyList());

        assertThrows(IllegalArgumentException.class, () -> service.createFromCapitalSignal(command));
    }
}
