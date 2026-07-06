package com.finscope.service.brief;

import com.finscope.dao.brief.BriefRepository;
import com.finscope.domain.brief.Brief;
import com.finscope.service.vault.VaultWriter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BriefServiceTest {
    @Test
    void listReturnsCachedBriefsWhenVaultIndexingIsTemporarilyBusy() throws Exception {
        BriefRepository briefRepository = mock(BriefRepository.class);
        VaultWriter vaultWriter = mock(VaultWriter.class);
        Brief cached = brief(LocalDate.of(2026, 6, 29));

        when(vaultWriter.listDailyBriefs())
                .thenReturn(Collections.singletonList(Paths.get("2026-06-29.md")));
        when(vaultWriter.readDailyBrief(LocalDate.of(2026, 6, 29)))
                .thenReturn("# 每日金融、投资、创业学习简报 - 2026-06-29\n\n## 今日摘要\n");
        when(vaultWriter.dailyBriefPath(LocalDate.of(2026, 6, 29)))
                .thenReturn(Paths.get("vault/daily-briefs/2026-06-29.md"));
        when(briefRepository.upsert(any(Brief.class)))
                .thenThrow(new IllegalStateException("database is locked"));
        when(briefRepository.findAll()).thenReturn(Collections.singletonList(cached));

        List<Brief> briefs = service(briefRepository, vaultWriter).list();

        assertEquals(1, briefs.size());
        assertEquals(LocalDate.of(2026, 6, 29), briefs.get(0).getBriefDate());
    }

    private BriefService service(BriefRepository briefRepository, VaultWriter vaultWriter) {
        BriefService service = new BriefService();
        ReflectionTestUtils.setField(service, "briefRepository", briefRepository);
        ReflectionTestUtils.setField(service, "vaultWriter", vaultWriter);
        return service;
    }

    private Brief brief(LocalDate date) {
        Brief brief = new Brief();
        brief.setBriefDate(date);
        brief.setTitle("Cached brief");
        brief.setContent("cached");
        brief.setMarkdownPath("cached.md");
        return brief;
    }
}
