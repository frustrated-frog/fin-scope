package com.finscope.service.instrument;

import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.domain.instrument.FundHoldingDisclosure;
import com.finscope.domain.instrument.FundStockHolding;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.rpc.quote.FundHoldingProvider;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.QuoteGatewayResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** 组装基金披露持仓、最新股票行情和盘中估算贡献。 */
@Service
public class FundHoldingDetailService {
    private static final Pattern FUND_CODE = Pattern.compile("^\\d{6}$");
    private static final String GENERAL_NOTE =
            "按最近披露的直接股票持仓估算，不代表基金实时持仓或实际净值涨跌。";
    private static final String ETF_LINK_NOTE =
            "该基金为 ETF 联接基金；当前仅展示基金直接披露的股票持仓，"
                    + "未穿透目标 ETF，不能代表目标 ETF 的底层成分股。";

    private final WatchlistRepository watchlistRepository;
    private final FundHoldingProvider holdingProvider;
    private final MarketDataGateway marketDataGateway;

    public FundHoldingDetailService(WatchlistRepository watchlistRepository,
                                    FundHoldingProvider holdingProvider,
                                    MarketDataGateway marketDataGateway) {
        this.watchlistRepository = watchlistRepository;
        this.holdingProvider = holdingProvider;
        this.marketDataGateway = marketDataGateway;
    }

    public FundHoldingDetail load(String code, boolean forceRefresh) {
        String fundCode = normalizeCode(code);
        WatchlistItem watchlistItem = watchlistRepository
                .findByCodeAndType(fundCode, "FUND")
                .orElseThrow(() -> new BusinessException(BizErrorCode.WATCHLIST_ITEM_NOT_FOUND));
        FundHoldingDisclosure disclosure = holdingProvider.fetch(fundCode);
        List<String> stockCodes = distinctStockCodes(disclosure.getHoldings());
        QuoteGatewayResult quoteResult = stockCodes.isEmpty()
                ? null : marketDataGateway.fetchQuotes("STOCK", stockCodes, forceRefresh);
        Map<String, Quote> quotes = quoteByCode(quoteResult);

        List<FundHoldingPositionView> positions = new ArrayList<FundHoldingPositionView>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalContribution = BigDecimal.ZERO;
        int estimatedCount = 0;
        for (FundStockHolding holding : disclosure.getHoldings()) {
            totalWeight = totalWeight.add(BigDecimal.valueOf(holding.getWeightPct()));
            Quote quote = quotes.get(holding.getStockCode());
            boolean eligible = contributionEligible(quote, quoteResult);
            Double contribution = eligible
                    ? contribution(holding.getWeightPct(), quote.getChangePct()) : null;
            if (contribution != null) {
                totalContribution = totalContribution.add(BigDecimal.valueOf(contribution));
                estimatedCount++;
            }
            positions.add(position(holding, quote, eligible, contribution));
        }

        String displayName = nonBlank(disclosure.getFundName())
                ? disclosure.getFundName() : watchlistItem.getName();
        String note = isEtfLink(displayName) ? ETF_LINK_NOTE : GENERAL_NOTE;
        return new FundHoldingDetail(fundCode, displayName, disclosure.getDisclosureDate(),
                disclosure.getRetrievedAt(), quoteResult == null ? null : quoteResult.getAsOf(),
                quoteResult == null ? null : quoteResult.getRetrievedAt(),
                quoteResult == null ? null : quoteResult.getSourceCode(),
                quoteResult == null ? null : quoteResult.getQualityStatus(),
                quoteResult == null ? null : quoteResult.getWarning(),
                quoteResult == null ? null : quoteResult.getRefreshId(),
                totalWeight.doubleValue(),
                estimatedCount == 0 ? null : totalContribution.doubleValue(),
                estimatedCount, positions.size(), false, note, positions);
    }

    private FundHoldingPositionView position(FundStockHolding holding, Quote quote,
                                             boolean eligible, Double contribution) {
        String note = quote == null ? "未取得该股票实时行情" : quote.getNote();
        if (!eligible && quote != null && (quote.getQualityStatus()
                == MarketDataQualityStatus.STALE_FALLBACK
                || quote.getQualityStatus() == MarketDataQualityStatus.UNAVAILABLE)) {
            note = "旧行情不参与估算";
        }
        return new FundHoldingPositionView(holding.getRank(), holding.getStockCode(),
                holding.getStockName(), holding.getWeightPct(), holding.getSharesTenThousand(),
                holding.getMarketValueTenThousand(), eligible ? quote.getPrice() : null,
                eligible ? quote.getChangePct() : null, contribution, eligible,
                quote == null ? null : quote.getQuoteTime(),
                quote == null ? null : quote.getQualityStatus(), note);
    }

    private boolean contributionEligible(Quote quote, QuoteGatewayResult batch) {
        return quote != null
                && quote.isValid()
                && finite(quote.getPrice())
                && finite(quote.getChangePct())
                && fresh(quote.getQualityStatus())
                && batch != null
                && fresh(batch.getQualityStatus());
    }

    private boolean fresh(MarketDataQualityStatus status) {
        return status == MarketDataQualityStatus.FRESH_PRIMARY
                || status == MarketDataQualityStatus.FRESH_FALLBACK
                || status == MarketDataQualityStatus.PARTIAL_FRESH;
    }

    private Double contribution(double weightPct, double changePct) {
        return BigDecimal.valueOf(weightPct)
                .multiply(BigDecimal.valueOf(changePct))
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Map<String, Quote> quoteByCode(QuoteGatewayResult result) {
        Map<String, Quote> quotes = new LinkedHashMap<String, Quote>();
        if (result == null) {
            return quotes;
        }
        for (Quote quote : result.getQuotes()) {
            if (quote != null && nonBlank(quote.getInstrumentCode())) {
                quotes.put(quote.getInstrumentCode().trim().toUpperCase(Locale.ROOT), quote);
            }
        }
        return quotes;
    }

    private List<String> distinctStockCodes(List<FundStockHolding> holdings) {
        Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>();
        for (FundStockHolding holding : holdings) {
            seen.put(holding.getStockCode(), Boolean.TRUE);
        }
        return new ArrayList<String>(seen.keySet());
    }

    private String normalizeCode(String code) {
        String normalized = Optional.ofNullable(code).orElse("").trim();
        if (!FUND_CODE.matcher(normalized).matches()) {
            throw new BusinessException(BizErrorCode.INSTRUMENT_CODE_FORMAT_INVALID, "000001");
        }
        return normalized;
    }

    private boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isEtfLink(String fundName) {
        if (fundName == null) {
            return false;
        }
        String compact = fundName.replace(" ", "").toUpperCase(Locale.ROOT);
        return compact.contains("ETF联接");
    }
}
