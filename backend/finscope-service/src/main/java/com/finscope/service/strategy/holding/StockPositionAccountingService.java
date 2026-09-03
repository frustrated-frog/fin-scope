package com.finscope.service.strategy.holding;

import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StockPositionAccountingService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public StockAccountSnapshot replay(List<StockTransaction> events) {
        List<StockTransaction> safeEvents = events == null
                ? java.util.Collections.<StockTransaction>emptyList() : events;
        Set<Long> reversedIds = reversedIds(safeEvents);
        Map<Long, StockPosition> positions = new LinkedHashMap<Long, StockPosition>();
        BigDecimal cash = ZERO;
        boolean cashTracked = false;
        for (StockTransaction event : safeEvents) {
            if (event.getType() == StockTransactionType.REVERSAL
                    || event.getId() != null && reversedIds.contains(event.getId())) {
                continue;
            }
            if (event.getType() == StockTransactionType.CASH_DEPOSIT) {
                cash = cash.add(zero(event.getCashAmount()));
                cashTracked = true;
                continue;
            }
            if (event.getType() == StockTransactionType.CASH_WITHDRAWAL) {
                cash = cash.subtract(zero(event.getCashAmount()));
                cashTracked = true;
                continue;
            }
            StockPosition position = position(positions, event);
            if (event.getType() == StockTransactionType.OPENING_BALANCE) {
                applyOpening(position, event);
            } else if (event.getType() == StockTransactionType.BUY) {
                applyBuy(position, event);
                cash = cash.subtract(turnover(event).add(event.totalFees()));
            } else if (event.getType() == StockTransactionType.SELL) {
                applySell(position, event);
                cash = cash.add(turnover(event).subtract(event.totalFees()));
            } else if (event.getType() == StockTransactionType.CASH_DIVIDEND) {
                BigDecimal dividend = zero(event.getCashAmount());
                position.setDividendIncome(position.getDividendIncome().add(dividend));
                cash = cash.add(dividend);
            } else if (event.getType() == StockTransactionType.BONUS_SHARE) {
                applyBonus(position, event);
            }
        }
        return snapshot(positions, cash, cashTracked);
    }

    private Set<Long> reversedIds(List<StockTransaction> events) {
        Set<Long> values = new HashSet<Long>();
        for (StockTransaction event : events) {
            if (event.getType() == StockTransactionType.REVERSAL && event.getReversalOfId() != null) {
                values.add(event.getReversalOfId());
            }
        }
        return values;
    }

    private StockPosition position(Map<Long, StockPosition> positions, StockTransaction event) {
        if (event.getInstrumentId() == null) {
            throw new IllegalArgumentException("证券事件必须关联股票");
        }
        StockPosition existing = positions.get(event.getInstrumentId());
        if (existing != null) {
            return existing;
        }
        StockPosition created = new StockPosition();
        created.setInstrumentId(event.getInstrumentId());
        created.setInstrumentCode(event.getInstrumentCode());
        created.setInstrumentName(event.getInstrumentName());
        positions.put(event.getInstrumentId(), created);
        return created;
    }

    private void applyOpening(StockPosition position, StockTransaction event) {
        if (position.getQuantity().signum() != 0) {
            throw new IllegalArgumentException("同一股票只能录入一次期初持仓");
        }
        position.setQuantity(event.getQuantity());
        position.setTotalCost(turnover(event).add(event.totalFees()));
        position.setOpenedOn(event.getTradeDate());
        position.setOpeningBalance(true);
        updateAverageCost(position);
    }

    private void applyBuy(StockPosition position, StockTransaction event) {
        if (position.getQuantity().signum() == 0) {
            position.setOpenedOn(event.getTradeDate());
            position.setOpeningBalance(false);
        }
        position.setQuantity(position.getQuantity().add(event.getQuantity()));
        position.setTotalCost(position.getTotalCost().add(turnover(event)).add(event.totalFees()));
        updateAverageCost(position);
    }

    private void applySell(StockPosition position, StockTransaction event) {
        if (position.getQuantity().compareTo(event.getQuantity()) < 0) {
            throw new IllegalArgumentException("卖出数量不能超过当前持仓");
        }
        BigDecimal removedCost = position.getAverageCost().multiply(event.getQuantity());
        BigDecimal realized = turnover(event).subtract(event.totalFees()).subtract(removedCost);
        position.setRealizedProfit(position.getRealizedProfit().add(realized));
        position.setQuantity(position.getQuantity().subtract(event.getQuantity()));
        position.setTotalCost(position.getTotalCost().subtract(removedCost));
        if (position.getQuantity().signum() == 0) {
            position.setTotalCost(ZERO);
            position.setAverageCost(ZERO);
        }
    }

    private void applyBonus(StockPosition position, StockTransaction event) {
        if (position.getQuantity().signum() <= 0) {
            throw new IllegalArgumentException("没有持仓时不能录入送股或转增");
        }
        position.setQuantity(position.getQuantity().add(event.getQuantity()));
        updateAverageCost(position);
    }

    private void updateAverageCost(StockPosition position) {
        position.setAverageCost(position.getTotalCost().divide(
                position.getQuantity(), 8, RoundingMode.HALF_UP));
    }

    private StockAccountSnapshot snapshot(Map<Long, StockPosition> positionMap,
                                          BigDecimal cash,
                                          boolean cashTracked) {
        StockAccountSnapshot snapshot = new StockAccountSnapshot();
        snapshot.setCash(cash);
        List<StockPosition> openPositions = new ArrayList<StockPosition>();
        BigDecimal realized = ZERO;
        BigDecimal dividends = ZERO;
        for (StockPosition position : positionMap.values()) {
            realized = realized.add(position.getRealizedProfit());
            dividends = dividends.add(position.getDividendIncome());
            position.setTotalProfit(position.getRealizedProfit().add(position.getDividendIncome()));
            if (position.getQuantity().signum() > 0) {
                openPositions.add(position);
            }
        }
        snapshot.setPositions(openPositions);
        snapshot.setRealizedProfit(realized);
        snapshot.setDividendIncome(dividends);
        snapshot.setCashTracked(cashTracked);
        snapshot.setTotalProfit(realized.add(dividends));
        snapshot.setTotalEquity(cash);
        snapshot.setCalculatedAt(LocalDateTime.now());
        return snapshot;
    }

    private BigDecimal turnover(StockTransaction event) {
        return zero(event.getQuantity()).multiply(zero(event.getPrice()));
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
