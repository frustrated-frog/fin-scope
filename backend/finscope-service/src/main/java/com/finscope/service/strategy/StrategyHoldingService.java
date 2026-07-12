package com.finscope.service.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.strategy.StrategyHoldingRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.strategy.StrategyHolding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StrategyHoldingService {
    private static final Set<String> FUND_ROLES = new HashSet<>(Arrays.asList("CORE","SATELLITE","DEFENSIVE","OBSERVE"));
    private static final Set<String> STOCK_ROLES = new HashSet<>(Arrays.asList("OBSERVE","SIMULATED","LIVE_VALIDATION"));
    @Resource private StrategyHoldingRepository holdingRepository;
    @Resource private StrategyInstrumentResolver instrumentResolver;

    public List<StrategyHolding> list() { return holdingRepository.findAllWithInstrument(); }

    @Transactional
    public StrategyHolding add(String code,String type,String role,double targetWeight,double currentWeight,String note) {
        validateWeights(targetWeight,currentWeight); validateRole(type,role); validateTotal(null,targetWeight);
        Instrument instrument=instrumentResolver.resolve(code,type);
        StrategyHolding value=new StrategyHolding(); value.setInstrumentId(instrument.getId()); value.setRole(role); value.setTargetWeight(targetWeight); value.setCurrentWeight(currentWeight); value.setNote(note); return holdingRepository.save(value);
    }

    @Transactional
    public StrategyHolding update(Long id,String role,double targetWeight,double currentWeight,String note,long revision) {
        StrategyHolding current=holdingRepository.findById(id).orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND,"组合条目不存在"));
        validateWeights(targetWeight,currentWeight); validateRole(current.getType(),role); validateTotal(id,targetWeight);
        if(!holdingRepository.update(id,role,targetWeight,currentWeight,note,revision)) throw new BusinessException(ErrorCode.CONFLICT,"记录已被更新，请刷新后再试");
        return holdingRepository.findById(id).orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND,"组合条目不存在"));
    }

    public void delete(Long id,long revision) { if(!holdingRepository.deleteByIdAndRevision(id,revision)) throw new BusinessException(ErrorCode.CONFLICT,"记录已被更新，请刷新后再试"); }
    private void validateTotal(Long id,double value){ if(holdingRepository.sumTargetWeightExcluding(id)+value>100.000001d) throw new BusinessException(ErrorCode.BAD_REQUEST,"目标权重合计不能超过 100%"); }
    private void validateWeights(double target,double current){ if(target<0||target>100||current<0||current>100) throw new BusinessException(ErrorCode.BAD_REQUEST,"权重必须在 0 到 100 之间"); }
    private void validateRole(String type,String role){ if("FUND".equals(type)&&!FUND_ROLES.contains(role)) throw new BusinessException(ErrorCode.BAD_REQUEST,"基金角色只能是核心、卫星、防守或观察"); if("STOCK".equals(type)&&!STOCK_ROLES.contains(role)) throw new BusinessException(ErrorCode.BAD_REQUEST,"股票角色只能是观察、模拟或真实验证"); }
}
