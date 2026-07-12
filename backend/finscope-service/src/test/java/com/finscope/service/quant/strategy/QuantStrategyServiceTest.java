package com.finscope.service.quant.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.quant.QuantStrategyRepository;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.data.QuantDatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuantStrategyServiceTest {
    @Test
    void confirmsValidatedDraftAsFingerprintBoundVersion() throws Exception {
        QuantStrategyRepository repository = mock(QuantStrategyRepository.class);
        QuantDatasetService datasets = mock(QuantDatasetService.class);
        QuantStrategyDraft draft = new QuantStrategyDraft();
        QuantStrategySpec spec = QuantStrategySpecValidatorTest.validSpec();
        draft.setId(7L); draft.setDatasetId(1L); draft.setStatus("VALIDATED");
        draft.setNormalizedSpec(new ObjectMapper().writeValueAsString(spec));
        when(repository.findDraft(7L)).thenReturn(Optional.of(draft));
        when(repository.nextVersion(spec.getName())).thenReturn(1);
        when(repository.saveVersion(any())).thenAnswer(invocation -> {
            QuantStrategyVersion value = invocation.getArgument(0); value.setId(9L); return value;
        });
        QuantDataset dataset = new QuantDataset(); dataset.setId(1L); dataset.setFingerprint("dataset-sha"); dataset.setStatus("READY");
        when(datasets.get(1L)).thenReturn(dataset);
        QuantStrategyService service = new QuantStrategyService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "datasets", datasets);

        QuantStrategyVersion version = service.confirm(7L);

        assertEquals(9L, version.getId());
        assertEquals("dataset-sha", version.getDatasetFingerprint());
        assertEquals("quant-java-v1", version.getEngineVersion());
    }
}
