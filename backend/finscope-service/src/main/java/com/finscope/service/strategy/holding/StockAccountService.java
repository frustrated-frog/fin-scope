package com.finscope.service.strategy.holding;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.service.instrument.QuoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Uses unadjusted quote snapshots for actual-account valuation. */
@Service
@Slf4j
public class StockAccountService {
    @Resource
    private StockTransactionService transactions;
    @Resource
    private QuoteService quotes;

    public StockAccountSnapshot snapshot() {
        StockAccountSnapshot account = transactions.account();
        Map<String, Quote> quoteMap = fetchQuotes(account.getPositions());
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        for (StockPosition position : account.getPositions()) {
            Quote quote = quoteMap.get(bareCode(position.getInstrumentCode()));
            if (quote == null || !quote.isValid() || quote.getPrice() == null || quote.getPrice() <= 0) {
                position.setQuoteQuality("UNAVAILABLE");
                continue;
            }
            BigDecimal rawPrice = BigDecimal.valueOf(quote.getPrice());
            BigDecimal positionValue = rawPrice.multiply(position.getQuantity());
            BigDecimal positionProfit = positionValue.subtract(position.getTotalCost());
            position.setLastPrice(rawPrice);
            position.setMarketValue(positionValue);
            position.setUnrealizedProfit(positionProfit);
            position.setTotalProfit(position.getRealizedProfit()
                    .add(position.getDividendIncome()).add(positionProfit));
            position.setQuoteDate(quoteTime(quote).toLocalDate());
            position.setQuoteQuality("RAW_QUOTE");
            marketValue = marketValue.add(positionValue);
            unrealized = unrealized.add(positionProfit);
        }
        account.setMarketValue(marketValue);
        account.setUnrealizedProfit(unrealized);
        account.setTotalProfit(account.getRealizedProfit().add(account.getDividendIncome()).add(unrealized));
        account.setTotalEquity(account.getCash().add(marketValue));
        applyWeights(account, marketValue);
        account.setCalculatedAt(LocalDateTime.now());
        return account;
    }

    private Map<String, Quote> fetchQuotes(List<StockPosition> positions) {
        if (positions.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> codes = new ArrayList<String>();
        for (StockPosition position : positions) {
            codes.add(bareCode(position.getInstrumentCode()));
        }
        try {
            List<Quote> values = quotes.fetch("STOCK", codes, false);
            Map<String, Quote> result = new HashMap<String, Quote>();
            for (Quote quote : values) {
                result.put(bareCode(quote.getInstrumentCode()), quote);
            }
            return result;
        } catch (RuntimeException error) {
            log.warn("真实持仓估值行情暂不可用 codes={} error={}", codes, error.getMessage());
            return Collections.emptyMap();
        }
    }

    private void applyWeights(StockAccountSnapshot account, BigDecimal marketValue) {
        BigDecimal concentration = BigDecimal.ZERO;
        for (StockPosition position : account.getPositions()) {
            if (marketValue.signum() <= 0 || position.getMarketValue() == null) {
                position.setWeight(BigDecimal.ZERO);
                continue;
            }
            BigDecimal weight = position.getMarketValue().divide(marketValue, 8, RoundingMode.HALF_UP);
            position.setWeight(weight);
            if (weight.compareTo(concentration) > 0) {
                concentration = weight;
            }
        }
        account.setConcentration(concentration);
    }

    private LocalDateTime quoteTime(Quote quote) {
        if (quote.getAsOf() != null) {
            return quote.getAsOf();
        }
        if (quote.getQuoteTime() != null) {
            return quote.getQuoteTime();
        }
        return LocalDateTime.now();
    }

    private String bareCode(String code) {
        if (code == null) {
            return "";
        }
        int delimiter = code.indexOf('.');
        return delimiter < 0 ? code : code.substring(0, delimiter);
    }
}
