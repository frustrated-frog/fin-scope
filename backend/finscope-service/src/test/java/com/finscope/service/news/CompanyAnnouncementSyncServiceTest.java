package com.finscope.service.news;

import com.finscope.dao.news.NewsReportRepository;
import com.finscope.domain.news.NewsReport;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.rpc.research.material.CompanyAnnouncementPage;
import com.finscope.rpc.research.material.EastmoneyAnnouncementClient;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.common.enums.research.AnnouncementContentIssue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyAnnouncementSyncServiceTest {
    @Test
    void continuesAfterIndividualContentFailureAndRetriesItOnNextSync() {
        var client = mock(EastmoneyAnnouncementClient.class);
        var repository = mock(NewsReportRepository.class);
        var window = mock(NewsWindowService.class);
        var service = new CompanyAnnouncementSyncService();
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "reports", repository);
        ReflectionTestUtils.setField(service, "window", window);
        var first = material("AN123", "杭电股份：关于取得股票回购专项贷款承诺函的公告");
        var second = material("AN456", "杭电股份：关于回购股份事项前十名股东持股情况的公告");
        when(client.page(any(), any(), eq(1), any())).thenReturn(new CompanyAnnouncementPage(List.of(first), true));
        when(client.page(any(), any(), eq(2), any())).thenReturn(new CompanyAnnouncementPage(List.of(second), false));
        when(repository.find(anyString())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("temporary upstream failure")).doNothing().when(client).enrich(eq(first), any());
        assertFalse(service.sync());
        verify(window).ingestAnnouncements(List.of(first));
        verify(window).ingestAnnouncements(List.of(second));
        var incomplete = new NewsReport();
        incomplete.setTitle(first.getTitle());
        incomplete.setContent(first.getTitle());
        var complete = new NewsReport();
        complete.setTitle(second.getTitle());
        complete.setContent("公告原文：内容");
        when(repository.find("EASTMONEY_ANNOUNCEMENT:AN123")).thenReturn(Optional.of(incomplete));
        when(repository.find("EASTMONEY_ANNOUNCEMENT:AN456")).thenReturn(Optional.of(complete));
        assertTrue(service.sync());
        verify(client, times(2)).enrich(eq(first), any());
        verify(client, times(1)).enrich(eq(second), any());
    }

    @Test
    void repairsExistingTitleOnlyAnnouncementAndDoesNotDownloadItAgainAfterSuccess() {
        var client = mock(EastmoneyAnnouncementClient.class);
        var repository = mock(NewsReportRepository.class);
        var window = mock(NewsWindowService.class);
        var service = new CompanyAnnouncementSyncService();
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "reports", repository);
        ReflectionTestUtils.setField(service, "window", window);
        var material = material("AN202611091831557499", "仟源医药：回购注销核查意见");
        var stored = new NewsReport();
        stored.setTitle(material.getTitle());
        stored.setContent(null);
        when(repository.find(anyString())).thenReturn(Optional.of(stored));
        when(client.page(any(), any(), eq(1), any())).thenReturn(new CompanyAnnouncementPage(List.of(material), false));
        doThrow(new ProviderContractException(AnnouncementContentIssue.ISSUER_UNCONFIRMED.name(), "旧核验未通过", false))
                .doAnswer(invocation -> {
                    material.setContent(material.getContent() + "\n公告原文：\n山西仟源医药集团股份有限公司核查意见");
                    return null;
                }).when(client).enrich(eq(material), any());
        doAnswer(invocation -> {
            List<ResearchMaterial> batch = invocation.getArgument(0);
            for (ResearchMaterial item : batch) {
                stored.setContent(item.getContent());
            }
            return null;
        }).when(window).ingestAnnouncements(anyList());

        assertFalse(service.sync());
        assertTrue(service.sync());
        assertTrue(stored.getContent().contains("公告原文："));
        assertTrue(service.sync());
        verify(client, times(2)).enrich(eq(material), any());
    }

    @Test
    void deadlineExhaustionKeepsListMaterialWithoutRepeatedPdfFailures() {
        var client = mock(EastmoneyAnnouncementClient.class);
        var repository = mock(NewsReportRepository.class);
        var window = mock(NewsWindowService.class);
        var service = new CompanyAnnouncementSyncService();
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "reports", repository);
        ReflectionTestUtils.setField(service, "window", window);
        var material = material("AN123", "公司：回购公告");
        when(client.page(any(), any(), eq(1), any())).thenReturn(new CompanyAnnouncementPage(List.of(material), false));
        try (var ignored = ProviderCallDeadline.open(Duration.ofNanos(1))) {
            assertFalse(service.sync());
        }
        verify(client, never()).enrich(any(), any());
        verify(window).ingestAnnouncements(List.of(material));
    }

    private ResearchMaterial material(String id, String title) {
        var value = new ResearchMaterial();
        value.setExternalId(id);
        value.setTitle(title);
        value.setContent(title);
        return value;
    }
}
