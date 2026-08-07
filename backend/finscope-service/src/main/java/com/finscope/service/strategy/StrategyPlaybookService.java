package com.finscope.service.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.strategy.StrategyPlaybookRepository;
import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyPlaybookRule;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.finscope.common.exception.BizErrorCode;

@Service
public class StrategyPlaybookService {
    private static final Set<String> STATUSES = values("RESEARCHING", "ACTIVE", "PAUSED");
    private static final Set<String> VALIDATION_STATUSES = values(
            "UNVALIDATED", "IN_RESEARCH", "SUPPORTED", "REFUTED", "INCONCLUSIVE");
    private static final Set<String> SOURCE_TYPES = values("BOOK", "ARTICLE", "SYSTEM");
    private static final Set<String> RULE_TYPES = values(
            "PRINCIPLE", "FILTER", "ENTRY", "EXIT", "CAUTION");
    private static final Set<String> TESTABILITY_TYPES = values(
            "QUALITATIVE", "CANDIDATE_RULE", "DETERMINISTIC");

    @Resource
    private StrategyPlaybookRepository repository;

    public List<StrategyPlaybookView> list() {
        List<StrategyPlaybookView> result = new ArrayList<>();
        for (StrategyPlaybook stored : repository.findAll()) {
            result.add(StrategyPlaybookView.of(stored));
        }
        return result;
    }

    public StrategyPlaybookView get(String code) {
        StrategyPlaybook stored = repository.findByCode(code)
                .orElseThrow(() -> new BusinessException(BizErrorCode.STRATEGY_TEMPLATE_NOT_FOUND));
        return StrategyPlaybookView.of(stored, repository.findRules(stored.getId()));
    }

    @Transactional
    public StrategyPlaybookView create(StrategyPlaybook value, List<StrategyPlaybookRule> rules) {
        validate(value, rules);
        try {
            StrategyPlaybook saved = repository.save(value, rules);
            return StrategyPlaybookView.of(saved, repository.findRules(saved.getId()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(BizErrorCode.STRATEGY_CODE_EXISTS, exception);
        }
    }

    @Transactional
    public StrategyPlaybook update(String code, String status, String note, long revision) {
        if (!repository.findByCode(code).isPresent()) {
            throw new BusinessException(BizErrorCode.STRATEGY_TEMPLATE_NOT_FOUND);
        }
        if (!STATUSES.contains(status)) {
            throw new BusinessException(BizErrorCode.STRATEGY_STATE_INVALID);
        }
        if (!repository.updateStatus(code, status, note, revision)) {
            throw new BusinessException(BizErrorCode.RECORD_UPDATED_AGAIN);
        }
        return repository.findByCode(code).orElseThrow(IllegalStateException::new);
    }

    private void validate(StrategyPlaybook value, List<StrategyPlaybookRule> rules) {
        if (value == null || blank(value.getCode()) || blank(value.getTitle()) || blank(value.getScope())
                || blank(value.getSummary()) || blank(value.getCadence()) || blank(value.getRiskBoundary())
                || blank(value.getSourceTitle()) || blank(value.getSourceType())
                || blank(value.getValidationStatus()) || blank(value.getStatus())) {
            invalid("策略定义缺少必要字段");
        }
        if (!STATUSES.contains(value.getStatus()) || !VALIDATION_STATUSES.contains(value.getValidationStatus())
                || !SOURCE_TYPES.contains(value.getSourceType())) {
            invalid("策略状态或来源类型不合法");
        }
        if (rules == null || rules.isEmpty()) {
            invalid("策略至少需要一条规则");
        }
        for (StrategyPlaybookRule rule : rules) {
            if (rule == null || blank(rule.getSectionCode()) || blank(rule.getSectionTitle())
                    || blank(rule.getRuleType()) || blank(rule.getRuleText()) || blank(rule.getTestability())
                    || !RULE_TYPES.contains(rule.getRuleType())
                    || !TESTABILITY_TYPES.contains(rule.getTestability())) {
                invalid("策略规则字段或枚举值不合法");
            }
        }
    }

    private void invalid(String message) {
        throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, message);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Set<String> values(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
