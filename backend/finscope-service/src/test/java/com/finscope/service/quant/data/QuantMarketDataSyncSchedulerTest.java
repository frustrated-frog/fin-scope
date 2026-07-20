package com.finscope.service.quant.data;

import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.domain.quant.data.QuantDataset;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class QuantMarketDataSyncSchedulerTest {

    @Test
    void continuesWithOtherEligibleDatasetsAfterOneSyncFails() {
        QuantDatasetRepository datasets = mock(QuantDatasetRepository.class);
        QuantMarketDataSyncService sync = mock(QuantMarketDataSyncService.class);
        QuantDataset first = dataset(7L);
        QuantDataset second = dataset(8L);
        when(datasets.findSyncEligible()).thenReturn(Arrays.asList(first, second));
        doThrow(new IllegalStateException("upstream down"))
                .when(sync).sync(7L, "SCHEDULED");

        new QuantMarketDataSyncScheduler(datasets, sync).refreshBuildingDatasets();

        verify(sync).sync(7L, "SCHEDULED");
        verify(sync).sync(8L, "SCHEDULED");
    }

    private static QuantDataset dataset(Long id) {
        QuantDataset value = new QuantDataset();
        value.setId(id);
        value.setDataKind("REAL");
        value.setDatasetLevel("RESEARCH");
        value.setFingerprintVersion("quant-dataset-v2");
        value.setStatus("BUILDING");
        return value;
    }
}
