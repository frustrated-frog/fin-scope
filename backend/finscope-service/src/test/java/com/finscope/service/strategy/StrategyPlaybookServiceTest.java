package com.finscope.service.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.strategy.StrategyPlaybookRepository;
import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyPlaybookRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StrategyPlaybookServiceTest {
    private StrategyPlaybookRepository repository;
    private StrategyPlaybookService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(StrategyPlaybookRepository.class);
        service = new StrategyPlaybookService();
        ReflectionTestUtils.setField(service, "repository", repository);
    }

    @Test
    void listReadsDefinitionsAndProvenanceOnlyFromDatabase() {
        StrategyPlaybook stored = playbook();
        stored.setStatus("ACTIVE");
        stored.setNote("进入验证阶段");
        stored.setRevision(3);
        when(repository.findAll()).thenReturn(Collections.singletonList(stored));

        List<StrategyPlaybookView> views = service.list();

        assertEquals(1, views.size());
        assertEquals("陈潇", views.get(0).getAuthor());
        assertEquals("《中长线股票策略基础》", views.get(0).getSourceTitle());
        assertEquals("ACTIVE", views.get(0).getStatus());
        assertEquals(3, views.get(0).getRevision());
        assertEquals(0, views.get(0).getRules().size());
        verify(repository, never()).save(any(), any());
    }

    @Test
    void getReturnsRulesInRepositoryOrder() {
        StrategyPlaybook stored = playbook();
        StrategyPlaybookRule first = rule("FUNDAMENTAL", "基本面筛选", "先看盈利质量", 1);
        StrategyPlaybookRule second = rule("TREND", "趋势过滤", "周线趋势向上", 2);
        when(repository.findByCode(stored.getCode())).thenReturn(Optional.of(stored));
        when(repository.findRules(stored.getId())).thenReturn(Arrays.asList(first, second));

        StrategyPlaybookView view = service.get(stored.getCode());

        assertEquals(2, view.getRules().size());
        assertEquals("FUNDAMENTAL", view.getRules().get(0).getSectionCode());
        assertEquals("TREND", view.getRules().get(1).getSectionCode());
    }

    @Test
    void createValidatesAndPersistsAggregate() {
        StrategyPlaybook value = playbook();
        List<StrategyPlaybookRule> rules = Collections.singletonList(
                rule("FUNDAMENTAL", "基本面筛选", "先看盈利质量", 1));
        when(repository.save(value, rules)).thenReturn(value);
        when(repository.findRules(value.getId())).thenReturn(rules);

        StrategyPlaybookView created = service.create(value, rules);

        assertEquals(value.getCode(), created.getCode());
        assertEquals(1, created.getRules().size());
        verify(repository).save(value, rules);
    }

    @Test
    void createRejectsMissingRulesAndDuplicateCodes() {
        StrategyPlaybook value = playbook();
        BusinessException missingRules = assertThrows(BusinessException.class,
                () -> service.create(value, Collections.emptyList()));
        assertEquals(ErrorCode.REQUEST_PARAMETER_INVALID, missingRules.getErrorCode());

        List<StrategyPlaybookRule> rules = Collections.singletonList(
                rule("FUNDAMENTAL", "基本面筛选", "先看盈利质量", 1));
        when(repository.save(value, rules)).thenThrow(new DuplicateKeyException("duplicate"));
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.create(value, rules));
        assertEquals(ErrorCode.DUPLICATE_OPERATION, duplicate.getErrorCode());
    }

    private StrategyPlaybook playbook() {
        StrategyPlaybook value = new StrategyPlaybook();
        value.setId(9L);
        value.setCode("STOCK_QUALITY_TREND_CHEN_XIAO_2020");
        value.setTitle("质量趋势中长线");
        value.setScope("股票");
        value.setSummary("以基本面质量筛选，再用中长期趋势确认并以量价配合买入");
        value.setCadence("周线观察，财报期复核");
        value.setRiskBoundary("不抄底、不逆势补仓，买入前写清退出条件");
        value.setAuthor("陈潇");
        value.setSourceTitle("《中长线股票策略基础》");
        value.setSourceType("BOOK");
        value.setSourceRef("local-pdf:中长线股票策略基础-陈潇");
        value.setSourcePublishedAt("2020");
        value.setValidationStatus("UNVALIDATED");
        value.setStatus("RESEARCHING");
        return value;
    }

    private StrategyPlaybookRule rule(String sectionCode, String sectionTitle,
                                      String ruleText, int sortOrder) {
        StrategyPlaybookRule value = new StrategyPlaybookRule();
        value.setSectionCode(sectionCode);
        value.setSectionTitle(sectionTitle);
        value.setRuleType("FILTER");
        value.setRuleText(ruleText);
        value.setTestability("CANDIDATE_RULE");
        value.setSourcePage(10);
        value.setSortOrder(sortOrder);
        return value;
    }
}
