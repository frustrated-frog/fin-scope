package com.finscope.service.globalmarket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.globalmarket.GlobalMarketSeries;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import com.finscope.rpc.globalmarket.GlobalMarketSource;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.service.marketdata.MarketDataSingleFlight;
import com.finscope.service.marketdata.MarketDataSnapshotCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.DateTimeException;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class GlobalMarketService {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalMarketService.class);
    private static final Set<String> CORE = Set.of("SPX", "DJI", "IXIC", "XAU", "BTC", "ETH");
    private static final Pattern STOCK = Pattern.compile("US-[A-Z]{1,6}(?:-[A-Z])?");
    @Autowired
    private GlobalMarketSource source;
    @Autowired
    private MarketDataSnapshotRepository snapshots;
    @Autowired
    private MarketDataSnapshotCodec codec;
    @Autowired
    private MarketDataSingleFlight flights;
    @Autowired
    private ObjectMapper json;

    public record Result(GlobalMarketSeries series, boolean fallback, String retrievedAt) {}

    public Result market(String id) {
        if (!CORE.contains(id) && !STOCK.matcher(id).matches()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "无法识别的全球行情标的");
        }
        return flights.execute("global-market/" + id, () -> load(id));
    }

    private Result load(String id) {
        MarketDataSnapshot previous = snapshots.find(MarketDataCapability.GLOBAL_MARKET_SERIES, id).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        GlobalMarketSeries saved = null;
        if (previous != null) {
            try {
                saved = json.readValue(previous.getPayloadJson(), GlobalMarketSeries.class);
            } catch (JsonProcessingException error) {
                LOG.warn("全球行情快照不可读 id={}", id, error);
            }
            if (saved != null && previous.getRetrievedAt().isAfter(now.minusSeconds(90))) {
                return new Result(saved, false, previous.getRetrievedAt().toInstant(ZoneOffset.UTC).toString());
            }
        }
        GlobalMarketSeries fresh;
        try {
            fresh = source.fetch(id);
            if (saved != null && Instant.parse(fresh.asOf()).isBefore(Instant.parse(saved.asOf()))) {
                throw new IOException("报价时间早于本地档案");
            }
        } catch (IOException | ProviderContractException | IllegalArgumentException | DateTimeException error) {
            LOG.warn("全球行情刷新失败 id={} reason={}", id, error.toString());
            if (saved != null) {
                return new Result(saved, true, previous.getRetrievedAt().toInstant(ZoneOffset.UTC).toString());
            }
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE, "暂未取得该标的行情，请稍后重试", error);
        }
        TreeMap<String, Double> history = new TreeMap<>();
        if (saved != null) {
            history.putAll(saved.closes());
        }
        history.putAll(fresh.closes());
        // Keep a rolling year on disk; upstream snapshots can contain decades of daily bars.
        history.headMap(now.toLocalDate().minusYears(1).minusDays(7).toString()).clear();
        GlobalMarketSeries series = new GlobalMarketSeries(fresh.symbol(), fresh.source(), fresh.asOf(),
                fresh.price(), fresh.reference(), history);
        String payload;
        try {
            payload = json.writeValueAsString(series);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法编码全球行情", error);
        }
        snapshots.upsert(new MarketDataSnapshot(MarketDataCapability.GLOBAL_MARKET_SERIES, id,
                series.source(), series.source(), LocalDateTime.ofInstant(Instant.parse(series.asOf()), ZoneOffset.UTC),
                now, payload, codec.sha256(payload), 1, now));
        return new Result(series, false, now.toInstant(ZoneOffset.UTC).toString());
    }
}
