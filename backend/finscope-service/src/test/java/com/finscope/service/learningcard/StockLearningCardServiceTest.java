package com.finscope.service.learningcard;

import com.finscope.dao.learningcard.StockLearningCardRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCard;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.service.research.ResearchService;
import com.finscope.service.research.ResearchThesisService;
import com.finscope.service.research.report.ResearchReportService;
import com.finscope.service.strategy.StrategyInstrumentResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockLearningCardServiceTest {
    @Test
    void startsCompanyResearchWithARegisteredTheme() {
        StockLearningCardService service = new StockLearningCardService();
        StrategyInstrumentResolver instruments = mock(StrategyInstrumentResolver.class);
        StockLearningCardRepository cards = mock(StockLearningCardRepository.class);
        ResearchThesisService theses = mock(ResearchThesisService.class);
        ResearchService research = mock(ResearchService.class);
        ReflectionTestUtils.setField(service, "instrumentResolver", instruments);
        ReflectionTestUtils.setField(service, "cards", cards);
        ReflectionTestUtils.setField(service, "thesisService", theses);
        ReflectionTestUtils.setField(service, "researchService", research);
        ReflectionTestUtils.setField(service, "reportService", mock(ResearchReportService.class));

        Instrument instrument = new Instrument();
        instrument.setId(1L); instrument.setCode("603618"); instrument.setName("杭电股份");
        StockLearningCard card = new StockLearningCard();
        card.setId(2L); card.setInstrumentId(1L);
        ResearchThesis thesis = new ResearchThesis(); thesis.setId(3L);
        ResearchRun run = new ResearchRun(); run.setId(4L);
        ResearchRunPlan plan = new ResearchRunPlan(); plan.setRun(run);
        when(instruments.resolve("603618", "STOCK")).thenReturn(instrument);
        when(cards.findOrCreate(1L, StockLearningFramework.CODE)).thenReturn(card);
        when(cards.latest(2L)).thenReturn(Optional.empty());
        when(theses.create(any(ResearchThesis.class))).thenReturn(thesis);
        when(research.createRun(anyLong(), any(LocalDate.class), anyList(), eq(ResearchMode.DEEP))).thenReturn(plan);
        when(cards.appendRun(any(StockLearningCardRun.class), anyList(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.start("603618");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> themes = ArgumentCaptor.forClass(List.class);
        verify(research).createRun(eq(3L), any(LocalDate.class), themes.capture(), eq(ResearchMode.DEEP));
        assertEquals(Collections.singletonList(ResearchEnums.THEME_COMPANY_IPO), themes.getValue());
    }
}
