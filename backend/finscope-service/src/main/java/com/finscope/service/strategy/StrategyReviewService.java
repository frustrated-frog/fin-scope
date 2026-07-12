package com.finscope.service.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.strategy.StrategyReviewRepository;
import com.finscope.domain.strategy.StrategyReview;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@Service public class StrategyReviewService {
    @Resource private StrategyReviewRepository repository;
    public List<StrategyReview> list(){return repository.findAll();}
    public StrategyReview create(LocalDate date,String facts,String reasoning,String action){if(date==null||blank(facts)||blank(reasoning)||blank(action))throw new BusinessException(ErrorCode.BAD_REQUEST,"复盘日期、事实、推理和行动不能为空");StrategyReview v=new StrategyReview();v.setReviewDate(date);v.setFacts(facts.trim());v.setReasoning(reasoning.trim());v.setNextAction(action.trim());return repository.save(v);}
    public void delete(Long id){repository.delete(id);} private boolean blank(String v){return v==null||v.trim().isEmpty();}
}
