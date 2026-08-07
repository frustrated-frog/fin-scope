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
import com.finscope.common.exception.BizErrorCode;

@Service
public class StrategyStockThesisService {
    @Resource
    private StrategyStockThesisRepository repository;
    @Resource
    private StrategyInstrumentResolver resolver;

    public List<StrategyStockThesis> list() {
        return repository.findAllWithInstrument();
    }

    @Transactional
    public StrategyStockThesis create(String code, String thesis, String buyConditions,
                                      String invalidationConditions, String watchFocus, String note) {
        requireText(thesis, buyConditions, invalidationConditions, watchFocus);
        Instrument instrument = resolver.resolve(code, "STOCK");
        if (repository.existsByInstrumentId(instrument.getId())) {
            throw new BusinessException(BizErrorCode.STOCK_RESEARCH_CARD_EXISTS);
        }
        StrategyStockThesis value = new StrategyStockThesis();
        value.setInstrumentId(instrument.getId());
        value.setStage(StockThesisStage.RESEARCH_POOL.name());
        value.setThesis(thesis);
        value.setBuyConditions(buyConditions);
        value.setInvalidationConditions(invalidationConditions);
        value.setWatchFocus(watchFocus);
        value.setNote(note);
        return repository.save(value);
    }

    public StrategyStockThesis update(Long id, String stage, String thesis, String buyConditions,
                                      String invalidationConditions, String watchFocus,
                                      String note, long revision) {
        StrategyStockThesis current = get(id);
        requireTransition(current.getStage(), stage);
        requireText(thesis, buyConditions, invalidationConditions, watchFocus);
        if (!repository.update(id, stage, thesis, buyConditions, invalidationConditions,
                watchFocus, note, revision)) {
            conflict();
        }
        return get(id);
    }

    public void delete(Long id, long revision) {
        if (!repository.delete(id, revision)) {
            conflict();
        }
    }

    private StrategyStockThesis get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(BizErrorCode.STOCK_RESEARCH_CARD_NOT_FOUND));
    }

    private void requireTransition(String from, String to) {
        try {
            int current = StockThesisStage.valueOf(from).ordinal();
            int next = StockThesisStage.valueOf(to).ordinal();
            if (Math.abs(current - next) > 1) {
                throw new BusinessException(BizErrorCode.RESEARCH_STAGE_SKIP_NOT_ALLOWED);
            }
        } catch (IllegalArgumentException error) {
            throw new BusinessException(BizErrorCode.STOCK_RESEARCH_STAGE_INVALID);
        }
    }

    private void requireText(String... values) {
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                throw new BusinessException(BizErrorCode.THESIS_CRITERIA_REQUIRED);
            }
        }
    }

    private void conflict() {
        throw new BusinessException(BizErrorCode.RECORD_UPDATED_AGAIN);
    }
}
