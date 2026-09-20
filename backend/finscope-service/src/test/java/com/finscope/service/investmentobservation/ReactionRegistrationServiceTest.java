package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.domain.investmentobservation.ReactionRegistration;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.domain.majorevent.MajorEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReactionRegistrationServiceTest {
    private final ReactionSampleRepository repository = mock(ReactionSampleRepository.class);
    private final MajorEventRepository majorEvents = mock(MajorEventRepository.class);
    private final ReactionRegistrationService service = new ReactionRegistrationService();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "majorEvents", majorEvents);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-09-20T08:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void preservesActualSourceIdentityAndLeavesIncompleteCandidatesAsDrafts() {
        MajorEvent event = new MajorEvent();
        event.setId(11L);
        event.setTitle("公司签订重大合同");
        event.setOriginType("NEWS_ITEM");
        event.setOriginKey("news-456");
        event.setCreatedAt(LocalDateTime.parse("2026-09-18T12:00:00"));
        when(majorEvents.findById(11L)).thenReturn(Optional.of(event));
        when(majorEvents.findRecent(100)).thenReturn(List.of(event));
        when(repository.create(any())).thenAnswer(call -> call.getArgument(0));
        ReactionSample sample = service.createDraft(11L);
        assertEquals(ReactionSampleState.DRAFT, sample.getState());
        assertEquals("NEWS_ITEM", sample.getSourceOriginType());
        assertEquals("news-456", sample.getSourceOriginKey());
        assertEquals(event.getCreatedAt(), sample.getFirstCapturedAt());
        assertNull(sample.getPublishedAt());
        assertEquals(ReactionEventType.CONTRACT, service.candidates().get(0).getSuggestedType());
    }

    @Test
    void confirmationRequiresFieldsAndRejectsFutureOrIndexSymbols() {
        ReactionRegistration command = command();
        command.setInstrumentCode("000300.SH");
        assertThrows(BusinessException.class, () -> service.confirm(1, command));
        command.setInstrumentCode("600519.SH");
        command.setPublishedAt(LocalDateTime.parse("2026-09-21T08:00:00"));
        assertThrows(BusinessException.class, () -> service.confirm(1, command));
        command.setPublishedAt(LocalDateTime.parse("2026-09-18T08:00:00"));
        command.setRelationNote(" ");
        assertThrows(BusinessException.class, () -> service.confirm(1, command));
        verifyNoInteractions(repository);
    }

    @Test
    void confirmsOnceFlagsHistoricalBackfillAndChecksRevision() {
        ReactionSample sample = new ReactionSample();
        sample.setId(1L);
        sample.setRegisteredAt(LocalDateTime.parse("2026-09-20T12:00:00"));
        when(repository.findById(1L)).thenReturn(Optional.of(sample));
        when(repository.confirm(any(), eq(2))).thenReturn(true);
        ReactionSample confirmed = service.confirm(1, command());
        assertEquals(ReactionSampleState.OBSERVING, confirmed.getState());
        assertTrue(confirmed.isHistoricalBackfill());
        assertThrows(BusinessException.class, () -> service.confirm(1, command()));
        when(repository.changeState(1, 2, ReactionSampleState.ARCHIVED)).thenReturn(false);
        assertEquals(ErrorCode.DATA_VERSION_CONFLICT,
                assertThrows(BusinessException.class, () -> service.archive(1, 2, true)).getErrorCode());
    }

    @Test
    void restoresIncompleteSamplesToDraftAndRejectsInvalidPagination() {
        ReactionSample sample = new ReactionSample();
        sample.setState(ReactionSampleState.ARCHIVED);
        when(repository.findById(1L)).thenReturn(Optional.of(sample));
        when(repository.changeState(1, 1, ReactionSampleState.DRAFT)).thenReturn(true);
        service.archive(1, 1, false);
        verify(repository).changeState(1, 1, ReactionSampleState.DRAFT);
        assertThrows(BusinessException.class, () -> service.list(null, 0, 101));
        when(majorEvents.findById(90L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, assertThrows(BusinessException.class, () -> service.createDraft(90L)).getErrorCode());
    }

    private ReactionRegistration command() {
        ReactionRegistration command = new ReactionRegistration();
        command.setInstrumentCode("600519.SH");
        command.setInstrumentName("示例公司");
        command.setEventType(ReactionEventType.EARNINGS);
        command.setPublishedAt(LocalDateTime.parse("2026-09-18T15:00:00"));
        command.setRelationNote("公司披露的正式财报");
        command.setRevision(2);
        return command;
    }
}
