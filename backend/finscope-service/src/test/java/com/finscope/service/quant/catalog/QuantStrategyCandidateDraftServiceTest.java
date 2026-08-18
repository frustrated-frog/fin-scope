package com.finscope.service.quant.catalog;

import com.finscope.common.enums.quant.QuantStrategyDraftStatus;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.quant.QuantStrategyCatalogRepository;
import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.service.quant.strategy.QuantStrategyService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuantStrategyCandidateDraftServiceTest {
    @Test
    void createsASourceBoundedDraftAndPersistsItsOrigin() {
        QuantStrategyCatalogRepository repository = mock(QuantStrategyCatalogRepository.class);
        QuantStrategyService strategies = mock(QuantStrategyService.class);
        when(repository.findById(7L)).thenReturn(Optional.of(candidate("ADAPTABLE")));
        QuantStrategyDraft draft = new QuantStrategyDraft(); draft.setId(11L); draft.setStatus(QuantStrategyDraftStatus.VALIDATED);
        when(strategies.generateDraft(org.mockito.ArgumentMatchers.eq(3L), contains("来源标题：价值（账面价值）因素")))
                .thenReturn(draft);
        QuantStrategyCandidateDraftService service = new QuantStrategyCandidateDraftService(repository, strategies);

        QuantStrategyDraft value = service.generate(7L, 3L);

        assertEquals(Long.valueOf(11L), value.getId());
        verify(repository).saveOrigin(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq("source-sha"), org.mockito.ArgumentMatchers.any());
        verify(strategies).generateDraft(org.mockito.ArgumentMatchers.eq(3L), contains("来源指标仅作线索"));
        verify(strategies).generateDraft(org.mockito.ArgumentMatchers.eq(3L), contains("BP"));
    }

    @Test
    void rejectsUnsupportedCandidateBeforeCallingTheAgent() {
        QuantStrategyCatalogRepository repository = mock(QuantStrategyCatalogRepository.class);
        QuantStrategyService strategies = mock(QuantStrategyService.class);
        when(repository.findById(7L)).thenReturn(Optional.of(candidate("UNSUPPORTED")));
        QuantStrategyCandidateDraftService service = new QuantStrategyCandidateDraftService(repository, strategies);

        BusinessException error = assertThrows(BusinessException.class, () -> service.generate(7L, 3L));

        assertTrue(error.getMessage().contains("暂不支持"));
        verify(strategies, org.mockito.Mockito.never()).generateDraft(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private QuantStrategyCandidate candidate(String status) {
        QuantStrategyCandidate value = new QuantStrategyCandidate();
        value.setId(7L); value.setTitle("价值（账面价值）因素"); value.setCompatibilityStatus(status);
        value.setSourceCommitSha("source-sha");
        value.setAdaptationNote("使用披露时点 BP 形成 A 股版本"); value.setMappedFactors(Arrays.asList("BP"));
        value.setPaperUrl("https://example.com/paper"); value.setImplementationUrl("https://example.com/code");
        return value;
    }
}
