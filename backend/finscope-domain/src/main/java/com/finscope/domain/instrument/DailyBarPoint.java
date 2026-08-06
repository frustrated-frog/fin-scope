package com.finscope.domain.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单根日 K 线数据，用于自选页标的的 K 线展示。
 *
 * <p>由 Python market-data-service 的 daily-bars 接口返回，经过 rpc 层
 * {@code PythonDailyBarClient} 解析后透传给前端。字段为前端渲染所需的最小集。</p>
 */
public class DailyBarPoint {
    private String code;
    private String market;
    private LocalDate tradeDate;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    private BigDecimal amount;
    private BigDecimal amplitude;
    private BigDecimal changePct;
    private BigDecimal turnoverRate;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getAmplitude() { return amplitude; }
    public void setAmplitude(BigDecimal amplitude) { this.amplitude = amplitude; }
    public BigDecimal getChangePct() { return changePct; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }
    public BigDecimal getTurnoverRate() { return turnoverRate; }
    public void setTurnoverRate(BigDecimal turnoverRate) { this.turnoverRate = turnoverRate; }
}
