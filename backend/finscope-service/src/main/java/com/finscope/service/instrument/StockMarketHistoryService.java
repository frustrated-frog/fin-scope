package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.DailyBarHistory;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.StockMarketHistory;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.quote.PythonDailyBarClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Optional;

/** 股票自动展示的行情输入；只在用户打开股票详情时加载。 */
@Service
@Slf4j
public class StockMarketHistoryService {
    private static final int HISTORY_LIMIT = 500;
    @Resource
    private InstrumentRepository instruments;
    @Resource
    private PythonDailyBarClient dailyBars;

    public StockMarketHistory load(String code, boolean refresh) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        Instrument instrument = resolveStock(code.trim());
        StockMarketHistory result = new StockMarketHistory();
        result.setBenchmarkCode("000300.SH");
        result.setBenchmarkName("沪深300");
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofSeconds(45))) {
            result.setStock(loadAdjusted(instrument.getCode(), instrument.getMarket(), refresh));
            try {
                result.setBenchmark(loadAdjusted("000300", "SH", refresh));
            } catch (ProviderContractException error) {
                log.warn("股票基准历史不可用 code={} errorType={}", code, error.getErrorType());
                result.getWarnings().add("沪深300历史暂不可用，相对强弱对比未计算");
            }
        }
        return result;
    }

    private Instrument resolveStock(String code) {
        Optional<Instrument> existing = instruments.findByCodeAndType(code, "STOCK");
        if (existing.isPresent()) {
            return existing.get();
        }
        // 基金持仓的股票下钻沿用原有只读能力，无需先加入自选或写入股票档案。
        if (instruments.findByCodeAndType(code, "FUND").isPresent()
                || !code.matches("(?:000|001|002|003|300|301|600|601|603|605|688|689|920)\\d{3}|[48]\\d{5}")) {
            throw new ResourceNotFoundException("股票标的不存在");
        }
        Instrument stock = new Instrument();
        stock.setCode(code);
        stock.setType("STOCK");
        stock.setMarket(code.startsWith("4") || code.startsWith("8") || code.startsWith("920")
                ? "BJ" : code.startsWith("6") ? "SH" : "SZ");
        return stock;
    }

    private DailyBarHistory loadAdjusted(String code, String market, boolean refresh) {
        DailyBarHistory history = dailyBars.fetchHistory(code, market, HISTORY_LIMIT, refresh);
        if (!hasAdjustedPrices(history) && !refresh) {
            try {
                history = dailyBars.fetchHistory(code, market, HISTORY_LIMIT, true);
            } catch (ProviderContractException error) {
                log.warn("前复权日线刷新失败 code={} errorType={}", code, error.getErrorType());
                history.getWarnings().add("前复权刷新失败，继续展示原日线；分析暂不可用");
            }
        }
        if (!hasAdjustedPrices(history)) {
            history.getWarnings().add("未获得一致前复权行情，收益、量价事件及价格位置暂不计算");
        }
        return history;
    }

    private boolean hasAdjustedPrices(DailyBarHistory history) {
        return !history.getBars().isEmpty()
                && history.getBars().stream().allMatch(bar -> "QFQ".equals(bar.getAdjustment()));
    }
}
