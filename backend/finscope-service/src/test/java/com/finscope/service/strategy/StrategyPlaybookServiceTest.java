package com.finscope.service.strategy;

import com.finscope.dao.strategy.StrategyPlaybookRepository;
import com.finscope.domain.strategy.StrategyPlaybook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StrategyPlaybookServiceTest {
    private StrategyPlaybookRepository repository;
    private StrategyPlaybookService service;

    @BeforeEach
    void setUp() {
        repository = mock(StrategyPlaybookRepository.class);
        service = new StrategyPlaybookService();
        ReflectionTestUtils.setField(service, "repository", repository);
    }

    @Test
    void listMergesStoredStateWithoutWritingDefaults() {
        StrategyPlaybook stored = new StrategyPlaybook();
        stored.setCode("FUND_DCA");
        stored.setStatus("ACTIVE");
        stored.setNote("每月 8 日执行");
        stored.setRevision(3);
        when(repository.findAll()).thenReturn(Collections.singletonList(stored));

        List<StrategyPlaybookView> views = service.list();

        assertEquals(5, views.size());
        assertEquals("ACTIVE", views.get(0).getStatus());
        assertEquals(3, views.get(0).getRevision());
        assertEquals("RESEARCHING", views.get(1).getStatus());
        verify(repository, never()).upsert(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
