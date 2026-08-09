package com.finscope.service.learningcard;

import com.finscope.dao.learningcard.StockLearningCardRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCard;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.service.strategy.StrategyInstrumentResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockLearningCardServiceTest {
    @Test
    void startsAnIndependentAgentRunWithoutResearchThemesOrTheses() {
        StockLearningCardService service = new StockLearningCardService();
        StrategyInstrumentResolver instruments = mock(StrategyInstrumentResolver.class);
        StockLearningCardRepository cards = mock(StockLearningCardRepository.class);
        StockLearningCardAgentExecutor executor = mock(StockLearningCardAgentExecutor.class);
        ReflectionTestUtils.setField(service, "instrumentResolver", instruments);
        ReflectionTestUtils.setField(service, "cards", cards);
        ReflectionTestUtils.setField(service, "agentExecutor", executor);
        Instrument instrument = new Instrument();
        instrument.setId(1L); instrument.setCode("603618"); instrument.setName("杭电股份");
        StockLearningCard card = new StockLearningCard(); card.setId(2L); card.setInstrumentId(1L);
        when(instruments.resolve("603618", "STOCK")).thenReturn(instrument);
        when(cards.findOrCreate(1L, StockLearningFramework.CODE)).thenReturn(card);
        when(cards.latest(2L)).thenReturn(Optional.empty());
        when(cards.appendRun(any(StockLearningCardRun.class), anyList(), anyList()))
                .thenAnswer(invocation -> { StockLearningCardRun run = invocation.getArgument(0); run.setId(3L); return run; });

        StockLearningCardRun run = service.start("603618");

        assertEquals("RUNNING", run.getStatus());
        assertEquals("QUEUED", run.getStage());
        assertNull(run.getResearchRunId());
        verify(executor).schedule(instrument, run);
    }

    @Test
    void marksTheRunRetryableWhenTheIndependentQueueRejectsIt() {
        StockLearningCardService service = new StockLearningCardService();
        StrategyInstrumentResolver instruments = mock(StrategyInstrumentResolver.class);
        StockLearningCardRepository cards = mock(StockLearningCardRepository.class);
        StockLearningCardAgentExecutor executor = mock(StockLearningCardAgentExecutor.class);
        ReflectionTestUtils.setField(service, "instrumentResolver", instruments);
        ReflectionTestUtils.setField(service, "cards", cards);
        ReflectionTestUtils.setField(service, "agentExecutor", executor);
        Instrument instrument = new Instrument();
        instrument.setId(1L); instrument.setCode("603618"); instrument.setName("杭电股份");
        StockLearningCard card = new StockLearningCard(); card.setId(2L); card.setInstrumentId(1L);
        when(instruments.resolve("603618", "STOCK")).thenReturn(instrument);
        when(cards.findOrCreate(1L, StockLearningFramework.CODE)).thenReturn(card);
        when(cards.latest(2L)).thenReturn(Optional.empty());
        when(cards.appendRun(any(StockLearningCardRun.class), anyList(), anyList()))
                .thenAnswer(invocation -> { StockLearningCardRun run = invocation.getArgument(0); run.setId(3L); return run; });
        when(cards.updateRun(any(StockLearningCardRun.class), anyList(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new java.util.concurrent.RejectedExecutionException("queue full"))
                .when(executor).schedule(any(Instrument.class), any(StockLearningCardRun.class));

        StockLearningCardRun run = service.start("603618");

        assertEquals("FAILED", run.getStatus());
        assertEquals("QUEUE_REJECTED", run.getErrorCode());
        assertEquals(true, run.isRetryable());
        verify(cards).updateRun(any(StockLearningCardRun.class), anyList(), anyList());
    }

    @Test
    void readsAnEmptyCardWithoutWritingAPlaceholderRow() {
        StockLearningCardService service = new StockLearningCardService();
        StrategyInstrumentResolver instruments = mock(StrategyInstrumentResolver.class);
        StockLearningCardRepository cards = mock(StockLearningCardRepository.class);
        ReflectionTestUtils.setField(service, "instrumentResolver", instruments);
        ReflectionTestUtils.setField(service, "cards", cards);
        Instrument instrument = new Instrument();
        instrument.setId(1L); instrument.setCode("603618"); instrument.setName("杭电股份");
        when(instruments.resolve("603618", "STOCK")).thenReturn(instrument);
        when(cards.findByInstrumentId(1L)).thenReturn(Optional.empty());

        StockLearningCardService.StockLearningCardView view = service.get("603618");

        assertEquals("IDLE", view.getCard().getStatus());
        assertNull(view.getLatestRun());
        verify(cards, org.mockito.Mockito.never()).findOrCreate(any(), any());
    }

    @Test
    void expiresAStaleRunningLeaseBeforeStartingAgain() {
        StockLearningCardService service = new StockLearningCardService();
        StrategyInstrumentResolver instruments = mock(StrategyInstrumentResolver.class);
        StockLearningCardRepository cards = mock(StockLearningCardRepository.class);
        StockLearningCardAgentExecutor executor = mock(StockLearningCardAgentExecutor.class);
        ReflectionTestUtils.setField(service, "instrumentResolver", instruments);
        ReflectionTestUtils.setField(service, "cards", cards);
        ReflectionTestUtils.setField(service, "agentExecutor", executor);
        Instrument instrument = new Instrument(); instrument.setId(1L); instrument.setCode("603618"); instrument.setName("杭电股份");
        StockLearningCard card = new StockLearningCard(); card.setId(2L); card.setInstrumentId(1L);
        StockLearningCardRun stale = new StockLearningCardRun(); stale.setId(7L); stale.setCardId(2L);
        stale.setStatus("RUNNING"); stale.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(instruments.resolve("603618", "STOCK")).thenReturn(instrument);
        when(cards.findOrCreate(1L, StockLearningFramework.CODE)).thenReturn(card);
        when(cards.latest(2L)).thenReturn(Optional.of(stale));
        when(cards.updateRun(any(), anyList(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cards.appendRun(any(), anyList(), anyList())).thenAnswer(invocation -> {
            StockLearningCardRun run = invocation.getArgument(0); run.setId(8L); return run;
        });

        StockLearningCardRun next = service.start("603618");

        assertEquals("RUNNING", next.getStatus());
        assertEquals("STALE_RUN_EXPIRED", stale.getErrorCode());
        verify(executor).schedule(instrument, next);
    }
}
