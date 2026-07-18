package com.finscope.service.financials;

import com.finscope.dao.financials.BrokerResearchReportRepository;
import com.finscope.domain.financials.BrokerResearchCandidate;
import com.finscope.domain.financials.BrokerResearchReport;
import com.finscope.domain.financials.BrokerResearchReportView;
import com.finscope.domain.financials.BrokerResearchSyncResult;
import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerResearchSyncServiceTest {

    @Test
    void syncsCatalogAndAutomaticallyImportsOnlyNewestMissingReport() {
        BrokerResearchSource source = mock(BrokerResearchSource.class);
        when(source.sourceCode()).thenReturn("EASTMONEY");
        BrokerResearchCandidate newest = candidate("NEW", LocalDate.of(2026, 7, 18));
        BrokerResearchCandidate older = candidate("OLD", LocalDate.of(2026, 6, 1));
        when(source.list(anyString(), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(Arrays.asList(older, newest));
        when(source.download(newest)).thenReturn("%PDF-new".getBytes());
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findBySourceUrl(anyString(), anyString())).thenReturn(Optional.empty());
        BrokerResearchService reports = mock(BrokerResearchService.class);
        BrokerResearchReport imported = new BrokerResearchReport();
        imported.setId(41L);
        imported.setSourceType("EASTMONEY");
        BrokerResearchReportView view = new BrokerResearchReportView();
        view.setReport(imported);
        when(reports.importRemote(7L, 9L, newest, "%PDF-new".getBytes())).thenReturn(view);
        BrokerResearchSyncService service = service(source, repository, reports);

        BrokerResearchSyncResult result = service.sync(7L, 9L);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1, result.getImportedCount());
        assertEquals(2, result.getCandidates().size());
        assertEquals("NEW", result.getCandidates().get(0).getExternalId());
        assertEquals(Long.valueOf(41L), result.getCandidates().get(0).getImportedReportId());
        assertEquals("AVAILABLE", result.getCandidates().get(1).getAvailability());
        verify(source).download(newest);
        verify(source, never()).download(older);
    }

    @Test
    void catalogOnlyMarksAlreadyImportedReportsWithoutDownloading() {
        BrokerResearchSource source = mock(BrokerResearchSource.class);
        when(source.sourceCode()).thenReturn("EASTMONEY");
        BrokerResearchCandidate candidate = candidate("KNOWN", LocalDate.of(2026, 7, 18));
        when(source.list(anyString(), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(Collections.singletonList(candidate));
        BrokerResearchReport existing = new BrokerResearchReport();
        existing.setId(33L);
        existing.setInstrumentId(7L);
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findBySourceUrl("EASTMONEY", candidate.getSourceUrl()))
                .thenReturn(Optional.of(existing));
        BrokerResearchService reports = mock(BrokerResearchService.class);
        BrokerResearchSyncService service = service(source, repository, reports);

        BrokerResearchSyncResult result = service.candidates(7L);

        assertEquals(Long.valueOf(33L), result.getCandidates().get(0).getImportedReportId());
        assertEquals("IMPORTED", result.getCandidates().get(0).getAvailability());
        assertEquals(1, result.getSkippedCount());
        verify(source, never()).download(any());
        verify(reports, never()).importRemote(any(), any(), any(), any());
    }

    @Test
    void importsSelectedCandidateByServerResolvedExternalIdentity() {
        BrokerResearchSource source = mock(BrokerResearchSource.class);
        when(source.sourceCode()).thenReturn("EASTMONEY");
        BrokerResearchCandidate selected = candidate("SELECTED", LocalDate.of(2026, 7, 18));
        when(source.list(anyString(), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(Collections.singletonList(selected));
        when(source.download(selected)).thenReturn("%PDF-selected".getBytes());
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findBySourceUrl(anyString(), anyString())).thenReturn(Optional.empty());
        BrokerResearchService reports = mock(BrokerResearchService.class);
        BrokerResearchReport imported = new BrokerResearchReport();
        imported.setId(51L);
        BrokerResearchReportView view = new BrokerResearchReportView();
        view.setReport(imported);
        when(reports.importRemote(7L, 9L, selected, "%PDF-selected".getBytes())).thenReturn(view);
        BrokerResearchSyncService service = service(source, repository, reports);

        BrokerResearchReportView result =
                service.importCandidate(7L, 9L, "EASTMONEY", "SELECTED");

        assertEquals(Long.valueOf(51L), result.getReport().getId());
        verify(source).download(selected);
    }

    @Test
    void preservesCatalogWhenOneAutomaticImportFails() {
        BrokerResearchSource source = mock(BrokerResearchSource.class);
        when(source.sourceCode()).thenReturn("EASTMONEY");
        BrokerResearchCandidate candidate = candidate("FAIL", LocalDate.of(2026, 7, 18));
        when(source.list(anyString(), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(Collections.singletonList(candidate));
        when(source.download(candidate)).thenThrow(new IllegalStateException("network"));
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findBySourceUrl(anyString(), anyString())).thenReturn(Optional.empty());
        BrokerResearchSyncService service =
                service(source, repository, mock(BrokerResearchService.class));

        BrokerResearchSyncResult result = service.sync(7L, null);

        assertEquals("PARTIAL", result.getStatus());
        assertEquals(1, result.getFailedCount());
        assertEquals(1, result.getCandidates().size());
        assertTrue(result.getErrors().get(0).contains("公司报告 FAIL"));
    }

    @Test
    void concurrentSyncRequestsForOneInstrumentShareTheSameRun() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BrokerResearchSource source = mock(BrokerResearchSource.class);
        when(source.sourceCode()).thenReturn("EASTMONEY");
        when(source.list(anyString(), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    release.await();
                    return Collections.emptyList();
                });
        BrokerResearchSyncService service = service(source,
                mock(BrokerResearchReportRepository.class), mock(BrokerResearchService.class));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<BrokerResearchSyncResult> first =
                    executor.submit(() -> service.sync(7L, 9L));
            entered.await();
            Future<BrokerResearchSyncResult> second =
                    executor.submit(() -> service.sync(7L, 9L));
            release.countDown();

            assertEquals("SUCCESS", first.get().getStatus());
            assertEquals("SUCCESS", second.get().getStatus());
            verify(source, times(1)).list(anyString(), any(LocalDate.class),
                    any(LocalDate.class), anyInt());
        } finally {
            executor.shutdownNow();
        }
    }

    private BrokerResearchSyncService service(BrokerResearchSource source,
                                               BrokerResearchReportRepository repository,
                                               BrokerResearchService reports) {
        FinancialQueryService financials = mock(FinancialQueryService.class);
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        instrument.setCode("SH.600519");
        instrument.setType("STOCK");
        instrument.setMarket("SH");
        when(financials.instrument(7L)).thenReturn(instrument);
        return new BrokerResearchSyncService(Collections.singletonList(source),
                repository, reports, financials);
    }

    private BrokerResearchCandidate candidate(String id, LocalDate date) {
        BrokerResearchCandidate value = new BrokerResearchCandidate();
        value.setSourceCode("EASTMONEY");
        value.setExternalId(id);
        value.setSourceUrl("https://pdf.dfcfw.com/pdf/H3_" + id + "_1.pdf");
        value.setStockCode("600519");
        value.setTitle("公司报告 " + id);
        value.setPublishedDate(date);
        value.setAvailability("AVAILABLE");
        return value;
    }
}
