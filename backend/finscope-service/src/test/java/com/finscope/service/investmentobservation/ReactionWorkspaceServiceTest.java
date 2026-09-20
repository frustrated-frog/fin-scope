package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.*;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.domain.investmentobservation.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReactionWorkspaceServiceTest {
    @Test
    void searchesBeyondFirstPageCountsAllOutcomesAndNeverSelectsByReturn() {
        var repo = mock(ReactionSampleRepository.class);
        var registration = mock(ReactionRegistrationService.class);
        var service = new ReactionWorkspaceService();
        ReflectionTestUtils.setField(service, "repository", repo);
        ReflectionTestUtils.setField(service, "registration", registration);
        ReactionSample target = sample(999);
        target.setPublishedAt(LocalDateTime.parse("2026-12-01T16:00:00"));
        when(registration.require(999)).thenReturn(target);
        List<ReactionSample> first = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            first.add(sample(i));
        }
        ReactionSample missing = sample(101);
        missing.getCalculation().getWindows().get(0).setStatus(ReactionWindowStatus.MISSING_DATA);
        ReactionSample pending = sample(102);
        pending.getCalculation().getWindows().get(0).setStatus(ReactionWindowStatus.NOT_DUE);
        when(repo.list(null, 0, 100)).thenReturn(first);
        when(repo.list(null, 100, 100)).thenReturn(List.of(missing, pending));
        var result = service.compare(999, 5).getSameCompany();
        assertEquals(102, result.getSampleCount());
        assertEquals(102, result.getEventCount());
        assertEquals(100, result.getCompleteCount());
        assertEquals(1, result.getMissingCount());
        assertEquals(1, result.getNotDueCount());
        assertEquals(0, result.getMedian().compareTo(new BigDecimal("-50.5")));
        assertEquals(4, result.getCases().size());
        assertEquals(102L, result.getCases().get(0).getSampleId());
        assertFalse(result.isRelaxed());
    }

    @Test
    void includesMissingCalculationsWhenRelaxingAndSeparatesOtherCompanies() {
        var repo = mock(ReactionSampleRepository.class);
        var registration = mock(ReactionRegistrationService.class);
        var service = new ReactionWorkspaceService();
        ReflectionTestUtils.setField(service, "repository", repo);
        ReflectionTestUtils.setField(service, "registration", registration);
        var target = sample(999);
        when(registration.require(999)).thenReturn(target);
        var missing = sample(1);
        missing.setCalculation(null);
        var other = sample(2);
        other.setInstrumentCode("600519.SH");
        var wrongType = sample(3);
        wrongType.setEventSubtype(ReactionEventSubtype.CONTRACT_TERMINATED);
        when(repo.list(null, 0, 100)).thenReturn(List.of(missing, other, wrongType));
        var result = service.compare(999, 5);
        assertTrue(result.getSameCompany().isRelaxed());
        assertEquals(1, result.getSameCompany().getSampleCount());
        assertEquals(1, result.getSameCompany().getMissingCount());
        assertEquals(1, result.getOtherCompanies().getCompleteCount());
        assertThrows(com.finscope.common.exception.BusinessException.class, () -> service.compare(999, 2));
    }

    private ReactionSample sample(long id) {
        ReactionSample sample = new ReactionSample();
        sample.setId(id);
        sample.setSourceIdentity("EVENT:" + id);
        sample.setInstrumentCode("300476.SZ");
        sample.setEventType(ReactionEventType.CONTRACT);
        sample.setEventSubtype(ReactionEventSubtype.CONTRACT_SIGNED);
        sample.setPublishedAt(LocalDateTime.parse("2026-01-01T16:00:00").plusDays(id));
        sample.setState(ReactionSampleState.OBSERVING);
        ReactionCalculation calculation = new ReactionCalculation();
        ReactionProfile profile = new ReactionProfile();
        profile.setBeforeRelativePp(BigDecimal.ZERO);
        calculation.setProfile(profile);
        ReactionWindow window = new ReactionWindow();
        window.setSessions(5);
        window.setStatus(ReactionWindowStatus.READY);
        window.setRelativeReturnPp(BigDecimal.valueOf(-id));
        calculation.getWindows().add(window);
        sample.setCalculation(calculation);
        return sample;
    }
}
