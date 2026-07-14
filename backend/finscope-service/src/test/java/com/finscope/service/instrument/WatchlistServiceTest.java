package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.attribution.AttributionRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {
    private WatchlistRepository repository;
    private WatchlistService service;

    @BeforeEach
    void setUp() {
        repository = mock(WatchlistRepository.class);
        QuoteService quoteService = mock(QuoteService.class);
        AttributionRepository attributionRepository = mock(AttributionRepository.class);
        when(quoteService.fetch(any(), any(), any(Boolean.class))).thenReturn(Collections.<Quote>emptyList());
        when(attributionRepository.findLatestCompletedSummaryViews()).thenReturn(Collections.emptyMap());

        service = new WatchlistService();
        ReflectionTestUtils.setField(service, "watchlistRepository", repository);
        ReflectionTestUtils.setField(service, "instrumentRepository", mock(InstrumentRepository.class));
        ReflectionTestUtils.setField(service, "quoteService", quoteService);
        ReflectionTestUtils.setField(service, "attributionRepository", attributionRepository);
    }

    @Test
    void investmentListExcludesExistingSectorRows() {
        when(repository.findByTypes(Arrays.asList("STOCK", "FUND")))
                .thenReturn(Arrays.asList(item(1L, "600519", "STOCK"), item(2L, "020608", "FUND")));

        List<WatchlistItemView> result = service.listInvestmentItemsWithQuotes(false);

        assertEquals(Arrays.asList("STOCK", "FUND"), result.stream()
                .map(value -> value.getItem().getType()).collect(Collectors.toList()));
    }

    @Test
    void followingExistingSectorIsIdempotent() {
        WatchlistItem existing = item(3L, "BK1036", "SECTOR");
        when(repository.findByCodeAndType("BK1036", "SECTOR")).thenReturn(Optional.of(existing));

        WatchlistItem result = service.followSector("bk1036");

        assertSame(existing, result);
        verify(repository, never()).save(any());
    }

    @Test
    void ordinaryAddRejectsSectorAndUnknownTypes() {
        BusinessException sector = assertThrows(BusinessException.class,
                () -> service.addInvestment("BK1036", "SECTOR", null));
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> service.addInvestment("600519", "CRYPTO", null));

        assertTrue(sector.getMessage().contains("板块关注接口"));
        assertTrue(unknown.getMessage().contains("股票或基金"));
    }

    @Test
    void ordinaryDeleteCannotRemoveSectorFollow() {
        WatchlistItem sector = item(3L, "BK1036", "SECTOR");
        when(repository.findById(3L)).thenReturn(Optional.of(sector));

        BusinessException error = assertThrows(BusinessException.class, () -> service.removeInvestment(3L));

        assertTrue(error.getMessage().contains("板块关注接口"));
        verify(repository, never()).delete(3L);
    }

    private WatchlistItem item(Long id, String code, String type) {
        WatchlistItem value = new WatchlistItem();
        value.setId(id);
        value.setInstrumentId(id);
        value.setCode(code);
        value.setName(code);
        value.setType(type);
        return value;
    }
}
