package com.finscope.service.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.strategy.StrategyStockThesisRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.strategy.StockThesisStage;
import com.finscope.domain.strategy.StrategyStockThesis;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class StrategyStockThesisService {
    @Resource private StrategyStockThesisRepository repository; @Resource private StrategyInstrumentResolver resolver;
    public List<StrategyStockThesis> list(){return repository.findAllWithInstrument();}
    @Transactional public StrategyStockThesis create(String code,String thesis,String buy,String invalidation,String focus,String note){requireText(thesis,buy,invalidation,focus);Instrument i=resolver.resolve(code,"STOCK");StrategyStockThesis v=new StrategyStockThesis();v.setInstrumentId(i.getId());v.setStage("RESEARCH_POOL");v.setThesis(thesis);v.setBuyConditions(buy);v.setInvalidationConditions(invalidation);v.setWatchFocus(focus);v.setNote(note);return repository.save(v);}
    public StrategyStockThesis update(Long id,String stage,String thesis,String buy,String invalidation,String focus,String note,long revision){StrategyStockThesis current=get(id);requireTransition(current.getStage(),stage);requireText(thesis,buy,invalidation,focus);if(!repository.update(id,stage,thesis,buy,invalidation,focus,note,revision))conflict();return get(id);}
    public void delete(Long id,long revision){if(!repository.delete(id,revision))conflict();}
    private StrategyStockThesis get(Long id){return repository.findById(id).orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND,"股票研究卡不存在"));}
    private void requireTransition(String from,String to){try{int a=StockThesisStage.valueOf(from).ordinal(),b=StockThesisStage.valueOf(to).ordinal();if(Math.abs(a-b)>1)throw new BusinessException(ErrorCode.BAD_REQUEST,"不能跳过研究阶段");}catch(IllegalArgumentException e){throw new BusinessException(ErrorCode.BAD_REQUEST,"股票研究阶段不合法");}}
    private void requireText(String... values){for(String v:values)if(v==null||v.trim().isEmpty())throw new BusinessException(ErrorCode.BAD_REQUEST,"投资逻辑、买入条件、失效条件和观察重点不能为空");}
    private void conflict(){throw new BusinessException(ErrorCode.CONFLICT,"记录已被更新，请刷新后再试");}
}
