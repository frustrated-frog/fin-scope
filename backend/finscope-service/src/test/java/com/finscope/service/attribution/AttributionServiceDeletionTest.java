package com.finscope.service.attribution;

import com.finscope.dao.attribution.AttributionRepository;
import com.finscope.dao.attribution.AttributionResearchRunRepository;
import com.finscope.domain.attribution.AttributionReport;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttributionServiceDeletionTest {
    @Test
    void deletesCompletedReportAfterRemovingItsResearchRun() {
        AttributionRepository reportRepository = mock(AttributionRepository.class);
        AttributionResearchRunRepository runRepository = mock(AttributionResearchRunRepository.class);
        AttributionReport report = new AttributionReport();
        report.setId(42L);
        report.setStatus("COMPLETED");
        when(reportRepository.findById(42L)).thenReturn(Optional.of(report));

        AttributionService service = new AttributionService();
        ReflectionTestUtils.setField(service, "attributionRepository", reportRepository);
        ReflectionTestUtils.setField(service, "researchRunRepository", runRepository);

        service.deleteReport(42L);

        org.mockito.InOrder calls = inOrder(runRepository, reportRepository);
        calls.verify(runRepository).deleteByReportId(42L);
        calls.verify(reportRepository).deleteById(42L);
    }
}
