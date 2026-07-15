package com.finscope.service.factorresearch;

import com.finscope.dao.factorresearch.ResearchDraftRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.domain.factorresearch.ResearchDraft;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchDraftServiceTest {
    private final ResearchDraftRepository repository = mock(ResearchDraftRepository.class);
    private final CapitalBehaviorSnapshotRepository snapshots = mock(CapitalBehaviorSnapshotRepository.class);
    private final InstrumentRepository instruments = mock(InstrumentRepository.class);
    private final ResearchFactorCatalog catalog = new ResearchFactorCatalog();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T17:00:00Z"), ZoneOffset.UTC);
    private final ResearchDraftService service = new ResearchDraftService(
            repository, catalog, snapshots, instruments, clock);

    @Test
    void createsAContextOnlyDraftWithServerControlledResearchBoundaries() {
        CapitalBehaviorSnapshot snapshot = snapshot();
        when(snapshots.findById(42L)).thenReturn(Optional.of(snapshot));
        when(instruments.findById(7L)).thenReturn(Optional.of(instrument()));
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
        assertEquals("snapshot:42", result.getEvidenceRefs().get(0));
        assertEquals(Collections.singletonList("PRICE_FLOW_DIVERGENCE"), result.getObjectiveTags());
        assertEquals(LocalDateTime.of(2026, 7, 15, 17, 0), result.getCreatedAt());
        assertEquals(3, result.getRequiredNextSteps().size());
        verify(repository).save(any(ResearchDraft.class));
    }

    @Test
    void rejectsARequestWhenTheClientSnapshotFingerprintDoesNotMatchAuthoritativeEvidence() {
        when(snapshots.findById(42L)).thenReturn(Optional.of(snapshot()));
        when(instruments.findById(7L)).thenReturn(Optional.of(instrument()));
        CapitalResearchDraftCommand command = new CapitalResearchDraftCommand(
                "600519.SH", "贵州茅台", LocalDateTime.of(2026, 7, 15, 15, 0),
                "PRICE_FLOW_DIVERGENCE", 42L, "forged-fingerprint",
                Collections.singletonList("forged:evidence"), Collections.emptyList());

        assertThrows(RuntimeException.class, () -> service.createFromCapitalSignal(command));
        verify(repository, never()).save(any());
    }

    private CapitalBehaviorSnapshot snapshot() {
        CapitalBehaviorSignal signal = CapitalBehaviorSignal.of(
                "PRICE_FLOW_DIVERGENCE", "capital-signal-v2", Collections.singletonList("flow:88:mainNetInflow"));
        signal.setFactorRefs(Collections.singletonList("factor:PRICE_FLOW_DIVERGENCE_5D:2026-07-15"));
        CapitalBehaviorSnapshot value = CapitalBehaviorSnapshot.of(7L,
                LocalDateTime.of(2026, 7, 15, 15, 0), Collections.emptyList(),
                Collections.singletonList(signal), "snapshot-fingerprint");
        value.setId(42L);
        return value;
    }

    private Instrument instrument() {
        Instrument value = new Instrument();
        value.setId(7L); value.setCode("600519"); value.setMarket("SH"); value.setName("贵州茅台");
        return value;
    }
}
