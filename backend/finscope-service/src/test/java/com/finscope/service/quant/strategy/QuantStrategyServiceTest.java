package com.finscope.service.quant.strategy;

import com.finscope.common.enums.quant.QuantStrategyDraftStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.quant.QuantStrategyRepository;
import com.finscope.dao.quant.QuantStrategyCatalogRepository;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.factor.FactorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.finscope.common.exception.BusinessException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class QuantStrategyServiceTest {
    @Test
    void marksConfirmedCatalogDraftWithItsCandidateOrigin() throws Exception {
        QuantStrategyRepository repository = mock(QuantStrategyRepository.class);
        QuantStrategyCatalogRepository catalog = mock(QuantStrategyCatalogRepository.class);
        QuantDatasetService datasets = mock(QuantDatasetService.class);
        QuantStrategyDraft draft = new QuantStrategyDraft();
        QuantStrategySpec spec = QuantStrategySpecValidatorTest.validSpec();
        draft.setId(7L); draft.setDatasetId(1L); draft.setStatus(QuantStrategyDraftStatus.VALIDATED); draft.setValidatedDatasetFingerprint("dataset-sha");
        spec.setStartDate(java.time.LocalDate.of(2024,1,1)); spec.setEndDate(java.time.LocalDate.of(2024,12,31));
        draft.setNormalizedSpec(new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).writeValueAsString(spec));
        when(repository.findDraft(7L)).thenReturn(Optional.of(draft)); when(repository.nextVersion(spec.getName())).thenReturn(1);
        when(repository.saveVersion(any())).thenAnswer(invocation -> { QuantStrategyVersion value = invocation.getArgument(0); value.setId(9L); return value; });
        when(catalog.findCandidateIdByDraft(7L)).thenReturn(Optional.of(3L));
        QuantDataset dataset = new QuantDataset(); dataset.setId(1L); dataset.setFingerprint("dataset-sha"); dataset.setStatus("READY");
        dataset.setStartDate(java.time.LocalDate.of(2024,1,1)); dataset.setEndDate(java.time.LocalDate.of(2024,12,31));
        when(datasets.get(1L)).thenReturn(dataset);
        when(datasets.availableFactorCodes(1L)).thenReturn(new java.util.HashSet<String>(java.util.Arrays.asList("ROE","MOMENTUM_20D")));
        QuantStrategyService service = new QuantStrategyService();
        ReflectionTestUtils.setField(service, "repository", repository); ReflectionTestUtils.setField(service, "catalogRepository", catalog);
        ReflectionTestUtils.setField(service, "datasets", datasets); ReflectionTestUtils.setField(service, "factors", new FactorRegistry());

        QuantStrategyVersion version = service.confirm(7L);

        assertEquals("CATALOG_AGENT", version.getSource());
        verify(catalog).linkVersionForDraft(7L, 9L);
    }

    @Test
    void confirmsValidatedDraftAsFingerprintBoundVersion() throws Exception {
        QuantStrategyRepository repository = mock(QuantStrategyRepository.class);
        QuantDatasetService datasets = mock(QuantDatasetService.class);
        QuantStrategyDraft draft = new QuantStrategyDraft();
        QuantStrategySpec spec = QuantStrategySpecValidatorTest.validSpec();
        draft.setId(7L); draft.setDatasetId(1L); draft.setStatus(QuantStrategyDraftStatus.VALIDATED);
        draft.setValidatedDatasetFingerprint("dataset-sha"); spec.setStartDate(java.time.LocalDate.of(2024,1,1)); spec.setEndDate(java.time.LocalDate.of(2024,12,31));
        draft.setNormalizedSpec(new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).writeValueAsString(spec));
        when(repository.findDraft(7L)).thenReturn(Optional.of(draft));
        when(repository.nextVersion(spec.getName())).thenReturn(1);
        when(repository.saveVersion(any())).thenAnswer(invocation -> {
            QuantStrategyVersion value = invocation.getArgument(0); value.setId(9L); return value;
        });
        QuantDataset dataset = new QuantDataset(); dataset.setId(1L); dataset.setFingerprint("dataset-sha"); dataset.setStatus("READY");
        dataset.setStartDate(java.time.LocalDate.of(2024,1,1)); dataset.setEndDate(java.time.LocalDate.of(2024,12,31));
        when(datasets.get(1L)).thenReturn(dataset);
        when(datasets.availableFactorCodes(1L)).thenReturn(new java.util.HashSet<String>(java.util.Arrays.asList("ROE","MOMENTUM_20D")));
        QuantStrategyService service = new QuantStrategyService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "datasets", datasets);
        ReflectionTestUtils.setField(service, "factors", new FactorRegistry());

        QuantStrategyVersion version = service.confirm(7L);

        assertEquals(9L, version.getId());
        assertEquals("dataset-sha", version.getDatasetFingerprint());
        assertEquals("quant-java-v1", version.getEngineVersion());

        draft.setValidatedDatasetFingerprint("stale-dataset-sha");
        BusinessException stale = assertThrows(BusinessException.class, () -> service.confirm(7L));
        assertEquals("数据集在草案生成后已变化，请重新生成策略草案", stale.getMessage());
    }
}
