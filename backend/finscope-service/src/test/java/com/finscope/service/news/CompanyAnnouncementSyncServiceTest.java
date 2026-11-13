package com.finscope.service.news;

import com.finscope.dao.news.NewsReportRepository;
import com.finscope.domain.news.NewsReport;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.rpc.research.material.CompanyAnnouncementPage;
import com.finscope.rpc.research.material.EastmoneyAnnouncementClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

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
        when(client.page(any(), any(), eq(1))).thenReturn(new CompanyAnnouncementPage(List.of(first), true));
        when(client.page(any(), any(), eq(2))).thenReturn(new CompanyAnnouncementPage(List.of(second), false));
        when(repository.find(anyString())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("temporary upstream failure")).doNothing().when(client).enrich(first);
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
        verify(client, times(2)).enrich(first);
        verify(client, times(1)).enrich(second);
    }

    private ResearchMaterial material(String id, String title) {
        var value = new ResearchMaterial();
        value.setExternalId(id);
        value.setTitle(title);
        value.setContent(title);
        return value;
    }
}
