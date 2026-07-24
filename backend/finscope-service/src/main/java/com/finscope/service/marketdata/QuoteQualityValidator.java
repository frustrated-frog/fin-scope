package com.finscope.service.marketdata;

import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** 拒绝代码错配、非正价格、OHLC 矛盾和未来时间等不可安全缓存的行情。 */
@Component
public class QuoteQualityValidator {
    private final Clock clock;
    private final MarketTradingSession tradingSession;

    public QuoteQualityValidator() {
        this(Clock.systemDefaultZone());
    }

    public QuoteQualityValidator(Clock clock) {
        this.clock = clock;
        this.tradingSession = new MarketTradingSession(clock, 120L);
    }

    public Optional<Quote> accept(String requestedCode, Quote quote) {
        return accept(null, requestedCode, quote);
    }

    public Optional<Quote> accept(MarketDataCapability capability,
                                  String requestedCode, Quote quote) {
        if (quote == null || requestedCode == null
                || !requestedCode.equalsIgnoreCase(quote.getInstrumentCode()) || !quote.isValid()) {
            return Optional.empty();
        }
        Double validationPrice = quote.getPrice() == null ? quote.getConfirmedNav() : quote.getPrice();
        if (validationPrice == null || !Double.isFinite(validationPrice) || validationPrice <= 0.0d) {
            return Optional.empty();
        }
        if (invalidNumber(quote.getHigh()) || invalidNumber(quote.getLow())
                || invalidNumber(quote.getOpen()) || invalidNumber(quote.getPreviousClose())) {
            return Optional.empty();
        }
        if (quote.getHigh() != null && quote.getLow() != null && quote.getHigh() < quote.getLow()) {
            return Optional.empty();
        }
        if (quote.getHigh() != null && quote.getPrice() > quote.getHigh()) {
            return Optional.empty();
        }
        if (quote.getLow() != null && quote.getPrice() < quote.getLow()) {
            return Optional.empty();
        }
        LocalDateTime asOf = quote.getAsOf() == null ? quote.getQuoteTime() : quote.getAsOf();
        if (asOf != null && asOf.isAfter(LocalDateTime.now(clock).plusMinutes(2))) {
            return Optional.empty();
        }
        if (capability != null && quote.getPrice() != null && asOf != null
                && !tradingSession.canServeFallback(asOf, LocalDateTime.now(clock))) {
            return Optional.empty();
        }
        quote.setAsOf(asOf);
        return Optional.of(quote);
    }

    private boolean invalidNumber(Double value) {
        return value != null && !Double.isFinite(value);
    }
}
