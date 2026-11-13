package com.finscope.service.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalBusinessResearchStatus;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.learningcard.StockLearningCardRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.*;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.learningcard.StockLearningCardService;
import com.finscope.service.learningcard.StockLearningFramework;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FundamentalBusinessResearchServiceTest {
    private FundamentalBusinessResearchService service;
    private StockLearningCardRepository cards;
    private StockLearningCardService learning;
    private LlmChatClient llm;

    @BeforeEach
    void setUp() {
        service = new FundamentalBusinessResearchService();
        var stocks = mock(FundamentalStockResolver.class);
        var stock = new Instrument();
        stock.setId(5L);
        when(stocks.require("603618")).thenReturn(stock);
        cards = mock(StockLearningCardRepository.class);
        learning = mock(StockLearningCardService.class);
        llm = mock(LlmChatClient.class);
        ReflectionTestUtils.setField(service, "stocks", stocks);
        ReflectionTestUtils.setField(service, "cards", cards);
        ReflectionTestUtils.setField(service, "learning", learning);
        ReflectionTestUtils.setField(service, "llm", llm);
        var card = new StockLearningCard();
        card.setId(1L);
        when(cards.findByInstrumentId(5L)).thenReturn(Optional.of(card));
    }

    @Test
    void localReadKeepsOlderUsefulBusinessMaterialWhileAnotherRunIsActive() {
        var running = run(11, "RUNNING");
        running.setStage("COLLECTING_EVIDENCE");
        when(cards.latest(1L)).thenReturn(Optional.of(running));
        when(cards.recentCompletedBusinessRuns(1L)).thenReturn(List.of(run(10, "READY")));
        var result = service.view("603618");
        assertEquals(FundamentalBusinessResearchStatus.RUNNING, result.getStatus());
        assertEquals(10L, result.getFindings().get(0).getRunId());
        assertTrue(result.isShowingPrevious());
        service.start("603618");
        verifyNoInteractions(learning, llm);
    }

    @Test
    void failedGenerationDoesNotEraseEarlierFindingsOrStartAnotherRunOnRead() {
        when(cards.latest(1L)).thenReturn(Optional.of(run(11, "FAILED")));
        when(cards.recentCompletedBusinessRuns(1L)).thenReturn(List.of(run(10, "READY")));
        var result = service.view("603618");
        assertEquals(FundamentalBusinessResearchStatus.FAILED, result.getStatus());
        assertFalse(result.getFindings().isEmpty());
        assertTrue(result.isShowingPrevious());
        verifyNoInteractions(learning, llm);
    }

    @Test
    void explicitStartUsesExistingResearchPipelineAndRefusesAnUnconfiguredModel() {
        assertThrows(BusinessException.class, () -> service.start("603618"));
        verifyNoInteractions(learning);
        when(llm.isConfigured()).thenReturn(true);
        service.start("603618");
        verify(learning).start("603618");
    }

    @Test
    void activeRunIsReusedEvenIfANewerTerminalVersionWasRecorded() {
        when(cards.latest(1L)).thenReturn(Optional.of(run(12, "FAILED")));
        when(cards.active(1L)).thenReturn(Optional.of(run(11, "RUNNING")));
        assertEquals(FundamentalBusinessResearchStatus.RUNNING, service.start("603618").getStatus());
        verifyNoInteractions(learning, llm);
    }

    @Test
    void expiredRunningVersionCanBeRetriedInsteadOfPollingForever() {
        var stale = run(11, "RUNNING");
        stale.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(cards.latest(1L)).thenReturn(Optional.of(stale));
        assertEquals(FundamentalBusinessResearchStatus.FAILED, service.view("603618").getStatus());
        when(llm.isConfigured()).thenReturn(true);
        service.start("603618");
        verify(learning).start("603618");
    }

    private StockLearningCardRun run(long id, String status) {
        var run = new StockLearningCardRun();
        run.setId(id);
        run.setStatus(status);
        run.setFrameworkCode(StockLearningFramework.CODE);
        run.setCreatedAt(LocalDateTime.now());
        if (!"READY".equals(status)) {
            return run;
        }
        var claim = new StockLearningCardClaim();
        claim.setStatus("READY");
        claim.setDimensionCode("PROFIT_MODEL");
        var section = new StockLearningCardSection();
        section.setKey("revenue_engine");
        section.setContent("有来源的收入结构认识");
        section.setVerificationStatus("SUPPORTED");
        section.setEvidenceRefs(List.of("E1"));
        claim.setSections(List.of(section));
        run.setClaims(List.of(claim));
        var evidence = new StockLearningCardEvidence("E1", "公司资料", "https://example.com/report", "example.com", null, "公开内容");
        evidence.setDimensionCode("PROFIT_MODEL");
        run.setEvidence(List.of(evidence));
        return run;
    }
}
