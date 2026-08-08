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
import com.finscope.common.exception.BizErrorCode;

@Service
public class StrategyHoldingService {
    private static final Set<String> FUND_ROLES = new HashSet<>(
            Arrays.asList("CORE", "SATELLITE", "DEFENSIVE", "OBSERVE"));
    private static final Set<String> STOCK_ROLES = new HashSet<>(
            Arrays.asList("OBSERVE", "SIMULATED", "LIVE_VALIDATION"));

    @Resource
    private StrategyHoldingRepository holdingRepository;
    @Resource
    private StrategyInstrumentResolver instrumentResolver;

    public List<StrategyHolding> list() {
        return holdingRepository.findAllWithInstrument();
    }

    @Transactional
    public StrategyHolding add(String code, String type, String role, double targetWeight,
                               double currentWeight, String note) {
        return add(code, type, role, targetWeight, currentWeight, null, null, note);
    }

    @Transactional
    public StrategyHolding add(String code, String type, String role, double targetWeight,
                               double currentWeight, Double quantity, Double averageCost, String note) {
        validateWeights(targetWeight, currentWeight);
        validatePosition(quantity, averageCost);
        validateRole(type, role);
        Instrument instrument = instrumentResolver.resolve(code, type);
        if (holdingRepository.existsByInstrumentId(instrument.getId())) {
            throw new BusinessException(BizErrorCode.INSTRUMENT_ALREADY_IN_PORTFOLIO);
        }
        validateTotal(null, targetWeight);

        StrategyHolding value = new StrategyHolding();
        value.setInstrumentId(instrument.getId());
        value.setRole(role);
        value.setTargetWeight(targetWeight);
        value.setCurrentWeight(currentWeight);
        value.setQuantity(quantity);
        value.setAverageCost(averageCost);
        value.setNote(note);
        return holdingRepository.save(value);
    }

    @Transactional
    public StrategyHolding update(Long id, String role, double targetWeight,
                                  double currentWeight, String note, long revision) {
        StrategyHolding current = holdingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BizErrorCode.PORTFOLIO_ENTRY_NOT_FOUND));
        return update(id, role, targetWeight, currentWeight, current.getQuantity(),
                current.getAverageCost(), note, revision);
    }

    @Transactional
    public StrategyHolding update(Long id, String role, double targetWeight,
                                  double currentWeight, Double quantity, Double averageCost,
                                  String note, long revision) {
        StrategyHolding current = holdingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BizErrorCode.PORTFOLIO_ENTRY_NOT_FOUND));
        validateWeights(targetWeight, currentWeight);
        validatePosition(quantity, averageCost);
        validateRole(current.getType(), role);
        validateTotal(id, targetWeight);
        if (!holdingRepository.update(id, role, targetWeight, currentWeight,
                quantity, averageCost, note, revision)) {
            throw new BusinessException(BizErrorCode.RECORD_UPDATED_AGAIN);
        }
        return holdingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BizErrorCode.PORTFOLIO_ENTRY_NOT_FOUND));
    }

    public void delete(Long id, long revision) {
        if (!holdingRepository.deleteByIdAndRevision(id, revision)) {
            throw new BusinessException(BizErrorCode.RECORD_UPDATED_AGAIN);
        }
    }

    private void validateTotal(Long id, double value) {
        if (holdingRepository.sumTargetWeightExcluding(id) + value > 100.000001d) {
            throw new BusinessException(BizErrorCode.WEIGHT_TOTAL_EXCEEDS_100);
        }
    }

    private void validateWeights(double targetWeight, double currentWeight) {
        if (targetWeight < 0 || targetWeight > 100 || currentWeight < 0 || currentWeight > 100) {
            throw new BusinessException(BizErrorCode.WEIGHT_OUT_OF_RANGE);
        }
    }

    private void validatePosition(Double quantity, Double averageCost) {
        if ((quantity == null) != (averageCost == null)
                || quantity != null && (quantity < 0 || averageCost < 0)) {
            throw new IllegalArgumentException("持仓数量与平均成本必须同时填写且不能为负数");
        }
    }

    private void validateRole(String type, String role) {
        if ("FUND".equals(type) && !FUND_ROLES.contains(role)) {
            throw new BusinessException(BizErrorCode.FUND_ROLE_INVALID);
        }
        if ("STOCK".equals(type) && !STOCK_ROLES.contains(role)) {
            throw new BusinessException(BizErrorCode.STOCK_ROLE_INVALID);
        }
    }
}
