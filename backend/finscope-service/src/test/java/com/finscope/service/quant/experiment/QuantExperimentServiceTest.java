package com.finscope.service.quant.experiment;

import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.strategy.QuantStrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuantExperimentServiceTest {
    @Test
    void reusesActiveExperimentForSameVersionAndRunsOnlyOnce() {
        QuantExperimentRepository repository = mock(QuantExperimentRepository.class);
        QuantStrategyService strategies = mock(QuantStrategyService.class);
        QuantExperimentRunner runner = mock(QuantExperimentRunner.class);
        QuantStrategyVersion version = new QuantStrategyVersion(); version.setId(3L);
        version.setStrategyFingerprint("strategy-sha"); version.setDatasetFingerprint("dataset-sha");
        when(strategies.getVersion(3L)).thenReturn(version);
        when(repository.findActiveByRequestFingerprint(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            QuantExperiment value = invocation.getArgument(0); value.setId(8L); return value;
        });
        QuantExperimentService service = new QuantExperimentService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "strategies", strategies);
        ReflectionTestUtils.setField(service, "runner", runner);
        ReflectionTestUtils.setField(service, "executor", (Executor) Runnable::run);

        QuantExperiment created = service.create(3L);

        assertEquals(8L, created.getId());
        verify(runner, times(1)).run(8L);
    }
}
