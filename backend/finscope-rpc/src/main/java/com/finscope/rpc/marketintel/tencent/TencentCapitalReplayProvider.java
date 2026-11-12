package com.finscope.rpc.marketintel.tencent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import com.finscope.rpc.marketintel.CapitalReplayData;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 独立的五日分钟价量来源，不根据成交额推断资金净额。 */
@Component
public class TencentCapitalReplayProvider implements CapitalFlowProvider {
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    private static final LocalTime AM_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime PM_OPEN = LocalTime.of(13, 0);
    private static final LocalTime CLOSE = LocalTime.of(15, 0);

    @Autowired
    private FinanceHttpClient http;

    @Autowired
    private ObjectMapper json;

    @Override
    public String providerCode() { return "TENCENT_INTRADAY"; }

    @Override
    public String providerFamily() { return "TENCENT"; }

    @Override
    public Set<MarketDataCapability> capabilities() { return Set.of(MarketDataCapability.CAPITAL_REPLAY_1M); }

    @Override
    public int priority() { return 5; }

    @Override
    public int batchLimit() { return 1; }

    @Override
    public Duration minimumInterval() { return Duration.ofMillis(500); }

    @Override
    public Duration timeout() { return Duration.ofSeconds(5); }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument != null && "STOCK".equals(instrument.getType())
                && instrument.getCode() != null && instrument.getCode().matches("\\d{6}")
                && Set.of("SH", "SZ").contains(instrument.getMarket() == null ? "" : instrument.getMarket());
    }

    @Override
    public CapitalFlowData fetch(Instrument instrument, LocalDate asOfDate) {
        throw new ProviderContractException("UNSUPPORTED_CAPABILITY", "腾讯分时源不提供分钟资金净额", false);
    }

    @Override
    public CapitalReplayData fetchReplay(Instrument instrument) {
        if (!supports(instrument)) {
            throw new ProviderContractException("UNSUPPORTED_INSTRUMENT", "腾讯分时源需要沪深 A 股代码", false);
        }
        String symbol = instrument.getMarket().toLowerCase(java.util.Locale.ROOT) + instrument.getCode();
        URI uri = URI.create("https://web.ifzq.gtimg.cn/appstock/app/day/query?code=" + symbol);
        try {
            FinanceHttpResponse response = http.get(providerCode(), uri,
                    Map.of("Referer", "https://gu.qq.com/"), 2 * 1024 * 1024, 5000);
            JsonNode root = json.readTree(response.getBody());
            JsonNode days = root.path("data").path(symbol).path("data");
            if (root.path("code").asInt(-1) != 0 || !days.isArray()) {
                throw new ProviderContractException("SCHEMA_DRIFT", "腾讯分时响应格式异常", false);
            }
            List<CapitalFlowPoint> prices = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (JsonNode day : days) {
                prices.addAll(parseDay(instrument, day, response, warnings));
            }
            if (prices.isEmpty()) {
                throw new ProviderContractException("EMPTY_INTRADAY_MARKET", "腾讯分时返回为空", true);
            }
            return new CapitalReplayData(prices, List.of(), warnings);
        } catch (ProviderContractException error) {
            throw error;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | java.time.DateTimeException | IllegalArgumentException error) {
            throw new ProviderContractException("SCHEMA_DRIFT", "腾讯分时响应格式异常", false, error);
        } catch (Exception error) {
            throw new ProviderContractException("INTRADAY_RESPONSE_ERROR", "腾讯分时读取失败", true, error);
        }
    }

    private List<CapitalFlowPoint> parseDay(Instrument instrument, JsonNode day,
                                           FinanceHttpResponse response, List<String> warnings) {
        LocalDate date = LocalDate.parse(day.path("date").asText(), DateTimeFormatter.BASIC_ISO_DATE);
        if (!day.path("data").isArray()) {
            throw new ProviderContractException("SCHEMA_DRIFT", "腾讯分时缺少分钟序列", false);
        }
        List<CapitalFlowPoint> points = new ArrayList<>();
        LocalTime previousTime = null;
        BigDecimal previousAmount = null;
        BigDecimal previousVolume = null;
        for (JsonNode line : day.path("data")) {
            String[] fields = line.asText().trim().split("\\s+");
            if (fields.length != 4) {
                throw new ProviderContractException("SCHEMA_DRIFT", "腾讯分时分钟字段异常", false);
            }
            LocalTime time = LocalTime.parse(fields[0], DateTimeFormatter.ofPattern("HHmm"));
            if (time.isBefore(OPEN) || time.isAfter(CLOSE) || time.isAfter(AM_CLOSE) && time.isBefore(PM_OPEN)) {
                continue;
            }
            BigDecimal price = new BigDecimal(fields[1]);
            BigDecimal cumulativeAmount = new BigDecimal(fields[3]);
            BigDecimal cumulativeVolume = new BigDecimal(fields[2]);
            if (price.signum() <= 0 || cumulativeAmount.signum() < 0 || cumulativeVolume.signum() < 0
                    || previousTime != null && !time.isAfter(previousTime)) {
                throw new ProviderContractException("SCHEMA_DRIFT", "腾讯分时分钟值异常", false);
            }
            boolean consecutive = previousTime != null && (time.equals(previousTime.plusMinutes(1))
                    || previousTime.equals(AM_CLOSE) && (time.equals(PM_OPEN) || time.equals(PM_OPEN.plusMinutes(1))));
            BigDecimal amount = time.equals(OPEN) ? cumulativeAmount
                    : consecutive ? cumulativeAmount.subtract(previousAmount) : null;
            if (amount == null || amount.signum() < 0) {
                amount = null;
                warnings.add("INTRADAY_AMOUNT_GAP");
            }
            CapitalFlowPoint point = new CapitalFlowPoint();
            point.setInstrumentId(instrument.getId());
            point.setProviderCode(providerCode());
            point.setGranularity("MINUTE_1");
            point.setDataDate(date);
            point.setObservedAt(date.atTime(time));
            point.setPrice(price);
            point.setIntervalTradeAmount(amount);
            BigDecimal volume = time.equals(OPEN) ? cumulativeVolume
                    : consecutive ? cumulativeVolume.subtract(previousVolume) : null;
            if (volume != null && volume.signum() >= 0) {
                point.setTradeVolume(volume);
            }
            point.setCumulativeTradeAmount(cumulativeAmount);
            point.setRetrievedAt(LocalDateTime.ofInstant(response.getRetrievedAt(), ZoneId.of("Asia/Shanghai")));
            point.setPayloadHash(response.getPayloadHash());
            point.setCalculationVersion("tencent-replay-v1");
            point.setQualityStatus(amount == null ? "PARTIAL" : "COMPLETE");
            points.add(point);
            previousTime = time;
            previousAmount = cumulativeAmount;
            previousVolume = cumulativeVolume;
        }
        return points;
    }
}
