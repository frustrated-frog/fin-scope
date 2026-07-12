package com.finscope.service.quant.experiment;

import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.strategy.QuantStrategyService;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.domain.quant.data.QuantDataset;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import com.finscope.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        QuantDatasetService datasets = mock(QuantDatasetService.class);
        QuantStrategyVersion version = new QuantStrategyVersion(); version.setId(3L);
        version.setDatasetId(1L);
        version.setStrategyFingerprint("strategy-sha"); version.setDatasetFingerprint("dataset-sha");
        when(strategies.getVersion(3L)).thenReturn(version);
        QuantDataset dataset = new QuantDataset(); dataset.setId(1L); dataset.setName("测试数据"); dataset.setDataKind("LEARNING_SAMPLE");
        when(datasets.get(1L)).thenReturn(dataset);
        when(repository.findActiveByRequestFingerprint(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            QuantExperiment value = invocation.getArgument(0); value.setId(8L); return value;
        });
        QuantExperimentService service = new QuantExperimentService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "strategies", strategies);
        ReflectionTestUtils.setField(service, "runner", runner);
        ReflectionTestUtils.setField(service, "datasets", datasets);
        ReflectionTestUtils.setField(service, "executor", (Executor) Runnable::run);

        QuantExperiment created = service.create(3L);

        assertEquals(8L, created.getId());
        verify(runner, times(1)).run(8L);
    }

    @Test
    void marksQueuedExperimentFailedWhenExecutorRejectsIt() {
        QuantExperimentRepository repository = mock(QuantExperimentRepository.class); QuantStrategyService strategies = mock(QuantStrategyService.class);
        QuantStrategyVersion version = new QuantStrategyVersion(); version.setId(3L); version.setStrategyFingerprint("s");
        version.setDatasetFingerprint("d"); version.setEngineVersion("e"); when(strategies.getVersion(3L)).thenReturn(version);
        when(repository.findActiveByRequestFingerprint(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> { QuantExperiment value = invocation.getArgument(0); value.setId(9L); return value; });
        QuantExperimentService service = new QuantExperimentService(); ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "strategies", strategies); ReflectionTestUtils.setField(service, "executor", (Executor) command -> { throw new RejectedExecutionException(); });
        BusinessException error = assertThrows(BusinessException.class, () -> service.create(3L));
        assertEquals("实验队列已满，请稍后重试", error.getMessage()); verify(repository).markFailed(9L, "实验队列已满，请稍后重试");
    }
}
