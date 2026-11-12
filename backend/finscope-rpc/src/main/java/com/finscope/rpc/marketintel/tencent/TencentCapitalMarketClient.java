package com.finscope.rpc.marketintel.tencent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 资金研究独立价量上下文，历史价格不复权，金额为真实成交额。 */
@Component
public class TencentCapitalMarketClient {
    @Autowired
    private FinanceHttpClient http;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private TencentCapitalReplayProvider intraday;

    public List<CapitalFlowPoint> fetchMinuteHistory(Instrument instrument) {
        return intraday.fetchReplay(instrument).getPrices();
    }

    public CapitalFlowPoint fetchQuote(Instrument instrument) {
        String symbol = symbol(instrument);
        try {
            FinanceHttpResponse response = request("https://qt.gtimg.cn/q=" + symbol);
            String body = response.getBody();
            int start = body.indexOf('"');
            int end = body.lastIndexOf('"');
            if (start < 0 || end <= start) {
                throw invalid();
            }
            String[] fields = body.substring(start + 1, end).split("~", -1);
            if (fields.length < 50 || !instrument.getCode().equals(fields[2])) {
                throw invalid();
            }
            LocalDateTime observed = LocalDateTime.parse(fields[30], DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            CapitalFlowPoint point = point(instrument, observed, response);
            point.setGranularity("QUOTE");
            point.setPrice(positive(fields[3]));
            // 资金研究页面统一使用手，与东财资金上下文保持一致。
            point.setTradeVolume(nonnegative(fields[36]));
            point.setCumulativeTradeAmount(nonnegative(fields[37]).multiply(new BigDecimal("10000")));
            point.setTurnoverRate(optional(fields[38]));
            point.setVolumeRatio(optional(fields[49]));
            return point;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("CAPITAL_QUOTE_UNAVAILABLE", "独立资金行情上下文读取失败", true, error);
        }
    }

    public List<CapitalFlowPoint> fetchDailyHistory(Instrument instrument) {
        String symbol = symbol(instrument);
        try {
            FinanceHttpResponse response = request("https://web.ifzq.gtimg.cn/appstock/app/newfqkline/get?param="
                    + symbol + ",day,,,320,");
            JsonNode root = json.readTree(response.getBody());
            JsonNode rows = root.path("data").path(symbol).path("day");
            if (root.path("code").asInt(-1) != 0 || !rows.isArray() || rows.isEmpty()) {
                throw invalid();
            }
            List<CapitalFlowPoint> result = new ArrayList<>();
            Set<LocalDate> seen = new HashSet<>();
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 9) {
                    throw invalid();
                }
                LocalDate day = LocalDate.parse(row.get(0).asText());
                if (day.isAfter(today) || !seen.add(day)) {
                    throw invalid();
                }
                CapitalFlowPoint point = point(instrument, day.atTime(15, 0), response);
                point.setGranularity("DAY_1");
                point.setPrice(positive(row.get(2).asText()));
                BigDecimal unit = instrument.getCode().startsWith("688") || instrument.getCode().startsWith("689")
                        ? new BigDecimal("0.01") : BigDecimal.ONE;
                point.setTradeVolume(nonnegative(row.get(5).asText()).multiply(unit));
                BigDecimal amount = optional(row.get(8).asText());
                point.setIntervalTradeAmount(amount == null ? null : amount.multiply(new BigDecimal("10000")));
                point.setTurnoverRate(optional(row.get(7).asText()));
                result.add(point);
            }
            result.sort(Comparator.comparing(CapitalFlowPoint::getObservedAt));
            return result;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("CAPITAL_HISTORY_UNAVAILABLE", "独立资金研究日线读取失败", true, error);
        }
    }

    private FinanceHttpResponse request(String uri) throws Exception {
        return http.get("TENCENT_CAPITAL_CONTEXT", URI.create(uri), Map.of("Referer", "https://gu.qq.com/"),
                2 * 1024 * 1024, 6000);
    }

    private CapitalFlowPoint point(Instrument instrument, LocalDateTime time, FinanceHttpResponse response) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setInstrumentId(instrument.getId());
        point.setProviderCode("TENCENT_CAPITAL_CONTEXT");
        point.setDataDate(time.toLocalDate());
        point.setObservedAt(time);
        point.setRetrievedAt(LocalDateTime.ofInstant(response.getRetrievedAt(), ZoneId.of("Asia/Shanghai")));
        point.setPayloadHash(response.getPayloadHash());
        point.setCalculationVersion("tencent-capital-context-v1");
        point.setQualityStatus("COMPLETE");
        return point;
    }

    private String symbol(Instrument instrument) {
        if (instrument == null || instrument.getCode() == null || !instrument.getCode().matches("\\d{6}")
                || !("SH".equals(instrument.getMarket()) || "SZ".equals(instrument.getMarket()))) {
            throw new ProviderContractException("UNSUPPORTED_INSTRUMENT", "独立资金行情目前覆盖沪深股票", false);
        }
        return instrument.getMarket().toLowerCase(java.util.Locale.ROOT) + instrument.getCode();
    }

    private BigDecimal positive(String text) {
        BigDecimal value = nonnegative(text);
        if (value.signum() <= 0) {
            throw invalid();
        }
        return value;
    }

    private BigDecimal nonnegative(String text) {
        BigDecimal value = new BigDecimal(text);
        if (value.signum() < 0) {
            throw invalid();
        }
        return value;
    }

    private BigDecimal optional(String text) {
        return text == null || text.isBlank() || "--".equals(text) || "-".equals(text) ? null : nonnegative(text);
    }

    private ProviderContractException invalid() {
        return new ProviderContractException("SCHEMA_DRIFT", "独立资金行情字段或时间无效", false);
    }
}
