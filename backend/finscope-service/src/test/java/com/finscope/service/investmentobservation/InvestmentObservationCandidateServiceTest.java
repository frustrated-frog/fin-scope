package com.finscope.service.investmentobservation;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class InvestmentObservationCandidateServiceTest {

    @Test
    void keepsInvestmentLearningSignalsWithoutPromotingGeneralPoliticsOrPersonalCases() {
        RadarRepository repository = Mockito.mock(RadarRepository.class);
        InvestmentObservationCandidateService service = new InvestmentObservationCandidateService();
        ReflectionTestUtils.setField(service, "radarRepository", repository);
        RadarEvent industrialGrowth = event(1L, "1至7月四川省规上工业增加值同比增长6.6%", "FINANCE");
        RadarEvent personalCase = event(2L, "交通银行原副行长涉嫌受贿被提起公诉", "FINANCE");
        RadarEvent airportIncident = event(3L, "日本成田机场一客机冒黑烟", "POLITICS");
        when(repository.findObservationCandidates(50))
                .thenReturn(Arrays.asList(industrialGrowth, personalCase, airportIncident));

        List<RadarEvent> candidates = service.load();

        assertEquals(1, candidates.size());
        assertEquals(industrialGrowth.getId(), candidates.get(0).getId());
    }

    private RadarEvent event(Long id, String title, String dashboardCategory) {
        RadarEvent value = new RadarEvent();
        value.setId(id);
        value.setCanonicalTitle(title);
        value.setDashboardCategory(dashboardCategory);
        return value;
    }
}
