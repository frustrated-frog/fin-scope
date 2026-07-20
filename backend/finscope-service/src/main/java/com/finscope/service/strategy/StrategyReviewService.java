package com.finscope.service.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.strategy.StrategyReviewRepository;
import com.finscope.domain.strategy.StrategyReview;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@Service
public class StrategyReviewService {
    @Resource
    private StrategyReviewRepository repository;

    public List<StrategyReview> list() {
        return repository.findAll();
    }

    public StrategyReview create(LocalDate date, String facts, String reasoning, String action) {
        if (date == null || blank(facts) || blank(reasoning) || blank(action)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "复盘日期、事实、推理和行动不能为空");
        }
        StrategyReview value = new StrategyReview();
        value.setReviewDate(date);
        value.setFacts(facts.trim());
        value.setReasoning(reasoning.trim());
        value.setNextAction(action.trim());
        return repository.save(value);
    }

    public void delete(Long id) {
        repository.delete(id);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
