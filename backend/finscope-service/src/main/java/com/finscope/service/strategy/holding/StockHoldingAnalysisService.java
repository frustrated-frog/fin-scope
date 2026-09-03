package com.finscope.service.strategy.holding;

import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockHoldingAnalysis;
import com.finscope.domain.strategy.holding.StockHoldingAnalysisRequest;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.rpc.quant.PythonHoldingAnalysisClient;
import com.finscope.service.quant.forecast.SingleStockForecastService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@Slf4j
public class StockHoldingAnalysisService {
    @Resource
    private StockAccountService accounts;
    @Resource
    private PythonHoldingAnalysisClient client;
    @Resource
    private SingleStockForecastRunRepository forecastRuns;
    @Resource
    private SingleStockForecastService forecasts;

    public StockHoldingAnalysis analyze(String requestedCode) {
        String code = canonicalInstrumentCode(requestedCode);
        StockPosition position = findPosition(accounts.snapshot(), code);
        StockHoldingAnalysis analysis;
        try {
            analysis = client.analyze(request(position));
        } catch (RuntimeException error) {
            log.warn("持仓路径分析暂不可用 code={} error={}", code, error.getMessage());
            analysis = quoteOnly(position, error.getMessage());
        }
        analysis.setInstrumentName(position.getInstrumentName());
        attachForecast(analysis, code);
        return analysis;
    }

    private StockPosition findPosition(StockAccountSnapshot account, String code) {
        for (StockPosition position : account.getPositions()) {
            if (code.equals(canonicalInstrumentCode(position.getInstrumentCode()))) {
                return position;
            }
        }
        throw new IllegalArgumentException("真实持仓不存在：" + code);
    }

    private StockHoldingAnalysisRequest request(StockPosition position) {
        if (position.getOpenedOn() == null || position.getAverageCost().signum() <= 0
                || position.getQuantity().signum() <= 0 || position.getLastPrice() == null
                || position.getLastPrice().signum() <= 0) {
            throw new IllegalArgumentException("持仓缺少建仓日期、成本或有效行情");
        }
        StockHoldingAnalysisRequest request = new StockHoldingAnalysisRequest();
        request.setInstrumentCode(canonicalInstrumentCode(position.getInstrumentCode()));
        request.setEntryDate(position.getOpenedOn());
        request.setCostBasis(position.getAverageCost().doubleValue());
        request.setQuantity(position.getQuantity().doubleValue());
        request.setMarketPrice(position.getLastPrice().doubleValue());
        return request;
    }

    private void attachForecast(StockHoldingAnalysis analysis, String code) {
        Optional<SingleStockForecastRun> latest = forecastRuns.findLatest(code);
        if (!latest.isPresent()) {
            return;
        }
        try {
            SingleStockForecastRun run = forecasts.detail(latest.get().getId());
            SingleStockForecast report = run.getReport();
            if (report == null) {
                return;
            }
            StockHoldingAnalysis.ForecastEvidence evidence = new StockHoldingAnalysis.ForecastEvidence();
            evidence.setRunId(run.getId());
            evidence.setAsOfDate(report.getAsOfDate());
            evidence.setHorizonDays(run.getHorizonDays());
            evidence.setStatus(report.getStatus());
            evidence.setUpProbability(report.getUpProbability());
            evidence.setModelVersion(run.getModelVersion());
            if (report.getReturnDistribution() != null) {
                evidence.setP10(report.getReturnDistribution().getP10());
                evidence.setP50(report.getReturnDistribution().getP50());
                evidence.setP90(report.getReturnDistribution().getP90());
            }
            analysis.setForecast(evidence);
        } catch (RuntimeException error) {
            log.warn("持仓最新预测证据读取失败 code={} error={}", code, error.getMessage());
            analysis.getWarnings().add("最新预测证据暂不可读取");
        }
    }

    private StockHoldingAnalysis quoteOnly(StockPosition position, String reason) {
        StockHoldingAnalysis value = new StockHoldingAnalysis();
        value.setInstrumentCode(canonicalInstrumentCode(position.getInstrumentCode()));
        value.setEntryDate(position.getOpenedOn());
        value.setAsOfDate(position.getQuoteDate());
        value.setCostBasis(position.getAverageCost().doubleValue());
        value.setLatestPrice(position.getLastPrice() == null ? 0 : position.getLastPrice().doubleValue());
        value.setQuantity(position.getQuantity().doubleValue());
        value.setTotalCost(position.getTotalCost().doubleValue());
        value.setMarketValue(position.getMarketValue() == null ? 0 : position.getMarketValue().doubleValue());
        value.setUnrealizedProfit(position.getUnrealizedProfit() == null
                ? 0 : position.getUnrealizedProfit().doubleValue());
        if (position.getAverageCost().signum() > 0 && position.getLastPrice() != null) {
            BigDecimal holdingReturn = position.getLastPrice().divide(
                    position.getAverageCost(), 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
            value.setHoldingReturn(holdingReturn.doubleValue());
        }
        value.setQualityStatus("UNAVAILABLE");
        value.setSourceCode("RAW_QUOTE");
        value.setMethod("CURRENT_QUOTE_ONLY");
        value.getWarnings().add("持仓路径暂不可用：" + safeMessage(reason));
        return value;
    }

    private String canonicalInstrumentCode(String code) {
        if (code == null || code.trim().isEmpty() || code.contains(".")) {
            return code;
        }
        String normalized = code.trim();
        if (normalized.startsWith("6") || normalized.startsWith("9")) {
            return normalized + ".SH";
        }
        if (normalized.startsWith("4") || normalized.startsWith("8")) {
            return normalized + ".BJ";
        }
        return normalized + ".SZ";
    }

    private String safeMessage(String value) {
        return value == null || value.trim().isEmpty() ? "未知原因" : value;
    }
}
