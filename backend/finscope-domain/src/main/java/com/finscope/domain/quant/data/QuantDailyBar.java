package com.finscope.domain.quant.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public class QuantDailyBar {
    private Long id;
    private Long datasetId;
    private LocalDate tradeDate;
    private String instrumentCode;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal adjustedClose;
    private BigDecimal volume;
    private BigDecimal amount;
    private String tradeStatus;
    private boolean st;
    private boolean limitUp;
    private boolean limitDown;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getInstrumentCode() { return instrumentCode; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }
    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getAdjustedClose() { return adjustedClose; }
    public void setAdjustedClose(BigDecimal adjustedClose) { this.adjustedClose = adjustedClose; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getTradeStatus() { return tradeStatus; }
    public void setTradeStatus(String tradeStatus) { this.tradeStatus = tradeStatus; }
    public boolean isSt() { return st; }
    public void setSt(boolean st) { this.st = st; }
    public boolean isLimitUp() { return limitUp; }
    public void setLimitUp(boolean limitUp) { this.limitUp = limitUp; }
    public boolean isLimitDown() { return limitDown; }
    public void setLimitDown(boolean limitDown) { this.limitDown = limitDown; }
}
