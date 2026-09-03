package com.finscope.service.investmentobservation;

import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class InvestmentObservationCandidateServiceTest {

    @Test
    void onlyUsesNewsThatTheUserHasPersistedAsMajorEvents() {
        MajorEventRepository repository = Mockito.mock(MajorEventRepository.class);
        InvestmentObservationCandidateService service = new InvestmentObservationCandidateService();
        ReflectionTestUtils.setField(service, "majorEvents", repository);
        MajorEvent industrialGrowth = event(1L, "RADAR_EVENT", "规上工业增加值同比增长");
        MajorEvent personalCase = event(2L, "RADAR_EVENT", "原副行长涉嫌受贿被提起公诉");
        MajorEvent article = event(3L, "ARTICLE", "长期研究文章");
        when(repository.find(null, null, null, null))
                .thenReturn(Arrays.asList(industrialGrowth, personalCase, article));

        List<RadarEvent> candidates = service.load();

        assertEquals(1, candidates.size());
        assertEquals(industrialGrowth.getId(), candidates.get(0).getId());
        assertEquals("规上工业增加值同比增长", candidates.get(0).getCanonicalTitle());
    }

    private MajorEvent event(Long id, String originType, String title) {
        MajorEvent value = new MajorEvent();
        value.setId(id);
        value.setOriginType(originType);
        value.setTitle(title);
        value.setSummary(title);
        value.setCategoryCode("FINANCE");
        value.setOccurredDate(LocalDate.of(2026, 9, 4));
        return value;
    }
}
