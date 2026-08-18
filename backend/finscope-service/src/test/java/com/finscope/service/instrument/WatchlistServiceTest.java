package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.attribution.AttributionRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
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
    private InstrumentRepository instruments;
    private QuoteService quoteService;
    private SectorMarketService sectorMarketService;
    private WatchlistService service;

    @BeforeEach
    void setUp() {
        repository = mock(WatchlistRepository.class);
        instruments = mock(InstrumentRepository.class);
        quoteService = mock(QuoteService.class);
        sectorMarketService = mock(SectorMarketService.class);
        AttributionRepository attributionRepository = mock(AttributionRepository.class);
        when(quoteService.fetch(any(), any(), any(Boolean.class))).thenReturn(Collections.<Quote>emptyList());
        when(attributionRepository.findLatestCompletedSummaryViews()).thenReturn(Collections.emptyMap());

        service = new WatchlistService();
        ReflectionTestUtils.setField(service, "watchlistRepository", repository);
        ReflectionTestUtils.setField(service, "instrumentRepository", instruments);
        ReflectionTestUtils.setField(service, "quoteService", quoteService);
        ReflectionTestUtils.setField(service, "attributionRepository", attributionRepository);
        ReflectionTestUtils.setField(service, "sectorMarketService", sectorMarketService);
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
        WatchlistItem existing = item(3L, "881121", "SECTOR");
        when(repository.findByCodeAndType("881121", "SECTOR")).thenReturn(Optional.of(existing));

        WatchlistItem result = service.followSector("881121");

        assertSame(existing, result);
        verify(repository, never()).save(any());
    }

    @Test
    void ordinaryAddRejectsSectorAndUnknownTypes() {
        BusinessException sector = assertThrows(BusinessException.class,
                () -> service.addInvestment("881121", "SECTOR", null));
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> service.addInvestment("600519", "CRYPTO", null));

        assertTrue(sector.getMessage().contains("板块关注接口"));
        assertTrue(unknown.getMessage().contains("股票或基金"));
    }

    @Test
    void ordinaryDeleteCannotRemoveSectorFollow() {
        WatchlistItem sector = item(3L, "881121", "SECTOR");
        when(repository.findById(3L)).thenReturn(Optional.of(sector));

        BusinessException error = assertThrows(BusinessException.class, () -> service.removeInvestment(3L));

        assertTrue(error.getMessage().contains("板块关注接口"));
        verify(repository, never()).delete(3L);
    }

    @Test
    void followsNewTonghuashunSectorWithCatalogName() {
        SectorMarketEntry sector = sector("881121", "半导体", 2.4, 1_200_000_000D);
        when(sectorMarketService.findByCode("881121", false)).thenReturn(Optional.of(sector));
        when(repository.findByCodeAndType("881121", "SECTOR")).thenReturn(Optional.empty());
        Instrument saved = new Instrument();
        saved.setId(9L);
        saved.setCode("881121");
        saved.setType("SECTOR");
        saved.setName("半导体");
        when(instruments.findByCodeAndType("881121", "SECTOR")).thenReturn(Optional.empty());
        when(instruments.save(any(Instrument.class))).thenReturn(saved);
        when(repository.existsByInstrumentId(9L)).thenReturn(false);
        when(repository.save(any(WatchlistItem.class))).thenAnswer(invocation -> {
            WatchlistItem value = invocation.getArgument(0);
            value.setId(3L);
            return value;
        });

        WatchlistItem result = service.followSector("881121");

        assertEquals("半导体", result.getName());
        verify(instruments).save(org.mockito.ArgumentMatchers.argThat(
                value -> "881121".equals(value.getCode()) && "半导体".equals(value.getName())));
    }

    @Test
    void followedSectorCardsUseTonghuashunSnapshotInsteadOfQuoteAdapters() {
        WatchlistItem followed = item(3L, "881121", "SECTOR");
        followed.setName("半导体");
        when(repository.findByTypes(Collections.singletonList("SECTOR")))
                .thenReturn(Collections.singletonList(followed));
        when(sectorMarketService.findByCodes(Collections.singletonList("881121"), true))
                .thenReturn(Collections.singletonMap("881121",
                        sector("881121", "半导体", 2.4, 1_200_000_000D)));

        WatchlistItemView result = service.listFollowedSectorsWithQuotes(true).get(0);

        assertEquals(2.4, result.getQuote().getChangePct());
        assertEquals(1_200_000_000D, result.getQuote().getMainNetInflow());
        assertEquals("PYTHON_TONGHUASHUN_SECTOR", result.getQuote().getSourceCode());
        verify(quoteService, never()).fetch(org.mockito.ArgumentMatchers.eq("SECTOR"), any(), any(Boolean.class));
    }

    private SectorMarketEntry sector(String code, String name, double changePct, double mainNetInflow) {
        SectorMarketEntry value = new SectorMarketEntry();
        value.setCode(code);
        value.setName(name);
        value.setCategory(SectorCategory.INDUSTRY);
        value.setChangePct(changePct);
        value.setMainNetInflow(mainNetInflow);
        return value;
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
