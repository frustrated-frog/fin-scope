package com.finscope.service.quant.catalog;

import com.finscope.dao.quant.QuantStrategyCatalogRepository;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogEntry;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSnapshot;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSyncResult;
import com.finscope.rpc.quant.catalog.QuantStrategyCatalogProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuantStrategyCatalogServiceTest {
    @Test
    void evaluatesAndPersistsACompleteProviderSnapshot() {
        QuantStrategyCatalogProvider provider = mock(QuantStrategyCatalogProvider.class);
        QuantStrategyCatalogRepository repository = mock(QuantStrategyCatalogRepository.class);
        when(provider.fetch()).thenReturn(snapshot());
        when(repository.countActive()).thenReturn(2);
        QuantStrategyCatalogService service = new QuantStrategyCatalogService(
                provider, repository, new QuantStrategyCompatibilityService());

        QuantStrategyCatalogSyncResult result = service.sync();

        assertEquals(2, result.getImportedCount());
        assertEquals("abc123", result.getCommitSha());
        verify(repository).saveSource(any());
        verify(repository).upsertCandidates(anyString(), anyString(), anyList(), any(LocalDateTime.class));
    }

    @Test
    void providerFailureDoesNotMutateThePreviousSnapshot() {
        QuantStrategyCatalogProvider provider = mock(QuantStrategyCatalogProvider.class);
        QuantStrategyCatalogRepository repository = mock(QuantStrategyCatalogRepository.class);
        when(provider.fetch()).thenThrow(new IllegalStateException("network down"));
        QuantStrategyCatalogService service = new QuantStrategyCatalogService(
                provider, repository, new QuantStrategyCompatibilityService());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, service::sync);

        verify(repository, never()).saveSource(any());
        verify(repository, never()).upsertCandidates(anyString(), anyString(), anyList(), any(LocalDateTime.class));
    }

    private QuantStrategyCatalogSnapshot snapshot() {
        QuantStrategyCatalogSnapshot value = new QuantStrategyCatalogSnapshot();
        value.setSourceCode("AWESOME_SYSTEMATIC_TRADING");
        value.setRepositoryUrl("https://github.com/paperswithbacktest/awesome-systematic-trading");
        value.setBranch("main");
        value.setCommitSha("abc123");
        value.setFetchedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        value.setEntries(Arrays.asList(entry("value", "价值（账面价值）因素"), entry("roa", "股票内部的ROA效应")));
        return value;
    }

    private QuantStrategyCatalogEntry entry(String key, String title) {
        QuantStrategyCatalogEntry value = new QuantStrategyCatalogEntry();
        value.setExternalKey(key);
        value.setTitle(title);
        return value;
    }
}
