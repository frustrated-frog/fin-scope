package com.finscope.service.strategy.holding;

import com.finscope.dao.strategy.StockTransactionRepository;
import com.finscope.dao.strategy.StrategyHoldingRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.strategy.StrategyHolding;
import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import com.finscope.service.strategy.StrategyInstrumentResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StockTransactionService {
    private static final BigDecimal BOARD_LOT = new BigDecimal("100");

    @Resource
    private StockTransactionRepository repository;
    @Resource
    private StrategyInstrumentResolver instrumentResolver;
    @Resource
    private StockPositionAccountingService accountingService;
    @Resource
    private StrategyHoldingRepository holdingRepository;

    public List<StockTransaction> list(int limit) {
        return repository.findAllDescending(limit);
    }

    @Transactional(rollbackFor = Exception.class)
    public StockTransaction create(String requestedCode, StockTransaction request) {
        validateCommon(request);
        Optional<StockTransaction> duplicate = repository.findByClientRequestId(request.getClientRequestId());
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        if (request.getType() == StockTransactionType.REVERSAL) {
            throw new IllegalArgumentException("冲正请使用专用接口");
        }
        if (isSecurityEvent(request.getType())) {
            Instrument instrument = instrumentResolver.resolve(requestedCode, "STOCK");
            request.setInstrumentId(instrument.getId());
            request.setInstrumentCode(instrument.getCode() + marketSuffix(instrument.getCode()));
            request.setInstrumentName(instrument.getName());
        } else if (requestedCode != null && !requestedCode.trim().isEmpty()) {
            throw new IllegalArgumentException("资金事件不应填写股票代码");
        }
        normalizeMoney(request);
        validateFields(request);
        validateReplay(request);
        StockTransaction saved = repository.save(request);
        syncProjection(requestedCode, request.getInstrumentId());
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public StockTransaction reverse(Long targetId, String clientRequestId, LocalDate tradeDate, String note) {
        Optional<StockTransaction> duplicate = repository.findByClientRequestId(clientRequestId);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        StockTransaction target = repository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("待冲正交易不存在"));
        if (target.getType() == StockTransactionType.REVERSAL) {
            throw new IllegalArgumentException("冲正事件不能再次冲正");
        }
        StockTransaction reversal = new StockTransaction();
        reversal.setClientRequestId(clientRequestId);
        reversal.setType(StockTransactionType.REVERSAL);
        reversal.setTradeDate(tradeDate == null ? LocalDate.now() : tradeDate);
        reversal.setReversalOfId(targetId);
        reversal.setNote(note);
        normalizeMoney(reversal);
        StockTransaction saved = repository.save(reversal);
        if (target.getInstrumentId() != null) {
            syncProjection(bareCode(target.getInstrumentCode()), target.getInstrumentId());
        }
        return saved;
    }

    public StockAccountSnapshot account() {
        return accountingService.replay(repository.findAll(1000));
    }

    private void validateCommon(StockTransaction request) {
        if (request == null || request.getType() == null) {
            throw new IllegalArgumentException("交易类型不能为空");
        }
        if (request.getClientRequestId() == null || request.getClientRequestId().trim().isEmpty()
                || request.getClientRequestId().length() > 80) {
            throw new IllegalArgumentException("幂等请求号不能为空且不能超过 80 个字符");
        }
        if (request.getTradeDate() == null || request.getTradeDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("交易日期不能为空且不能晚于今天");
        }
    }

    private void normalizeMoney(StockTransaction request) {
        request.setCommission(zero(request.getCommission()));
        request.setStampDuty(zero(request.getStampDuty()));
        request.setTransferFee(zero(request.getTransferFee()));
        request.setOtherFee(zero(request.getOtherFee()));
        request.setCashAmount(zero(request.getCashAmount()));
    }

    private void validateFields(StockTransaction request) {
        requireNonNegative(request.getCommission(), "佣金");
        requireNonNegative(request.getStampDuty(), "印花税");
        requireNonNegative(request.getTransferFee(), "过户费");
        requireNonNegative(request.getOtherFee(), "其他费用");
        if (request.getType() == StockTransactionType.BUY
                || request.getType() == StockTransactionType.SELL
                || request.getType() == StockTransactionType.OPENING_BALANCE) {
            requirePositive(request.getQuantity(), "数量");
            requirePositive(request.getPrice(), "成交价");
        }
        if (request.getType() == StockTransactionType.BUY
                && request.getQuantity().remainder(BOARD_LOT).signum() != 0) {
            throw new IllegalArgumentException("A 股买入数量必须是 100 股的整数倍");
        }
        if (request.getType() == StockTransactionType.BONUS_SHARE) {
            requirePositive(request.getQuantity(), "送转数量");
        }
        if (request.getType() == StockTransactionType.CASH_DIVIDEND
                || request.getType() == StockTransactionType.CASH_DEPOSIT
                || request.getType() == StockTransactionType.CASH_WITHDRAWAL) {
            requirePositive(request.getCashAmount(), "现金金额");
        }
    }

    private void validateReplay(StockTransaction request) {
        List<StockTransaction> events = new ArrayList<StockTransaction>(repository.findAll(1000));
        request.setId(Long.MAX_VALUE);
        events.add(request);
        accountingService.replay(events);
        request.setId(null);
    }

    private void syncProjection(String requestedCode, Long instrumentId) {
        if (instrumentId == null) {
            return;
        }
        StockAccountSnapshot account = account();
        StockPosition position = null;
        for (StockPosition candidate : account.getPositions()) {
            if (instrumentId.equals(candidate.getInstrumentId())) {
                position = candidate;
                break;
            }
        }
        Double quantity = position == null ? 0d : position.getQuantity().doubleValue();
        Double averageCost = position == null ? 0d : position.getAverageCost().doubleValue();
        Optional<StrategyHolding> existing = holdingRepository.findStockByCode(requestedCode);
        if (existing.isPresent()) {
            holdingRepository.updatePositionProjection(existing.get().getId(), quantity, averageCost);
            return;
        }
        StrategyHolding holding = new StrategyHolding();
        holding.setInstrumentId(instrumentId);
        holding.setRole("LIVE_VALIDATION");
        holding.setTargetWeight(0);
        holding.setCurrentWeight(0);
        holding.setQuantity(quantity);
        holding.setAverageCost(averageCost);
        holding.setNote("由真实交易账本自动建立");
        holdingRepository.save(holding);
    }

    private boolean isSecurityEvent(StockTransactionType type) {
        return type == StockTransactionType.OPENING_BALANCE
                || type == StockTransactionType.BUY
                || type == StockTransactionType.SELL
                || type == StockTransactionType.CASH_DIVIDEND
                || type == StockTransactionType.BONUS_SHARE;
    }

    private void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
    }

    private void requireNonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(label + "不能为负数");
        }
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String bareCode(String code) {
        return code == null || code.length() < 6 ? code : code.substring(0, 6);
    }

    private String marketSuffix(String code) {
        if (code.startsWith("6") || code.startsWith("9")) {
            return ".SH";
        }
        if (code.startsWith("4") || code.startsWith("8") || code.startsWith("92")) {
            return ".BJ";
        }
        return ".SZ";
    }
}
