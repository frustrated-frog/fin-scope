package com.finscope.domain.instrument;

import java.time.LocalDateTime;

/**
 * 行情快照：某标的在某个时刻的价格与涨跌。
 */
public class Quote {
    private String instrumentCode;
    private String name;
    /** 最新价 / 基金估值净值 */
    private Double price;
    /** 基金最近确认的单位净值 */
    private Double confirmedNav;
    /** 确认净值对应日期，格式 yyyy-MM-dd */
    private String confirmedNavDate;
    private Double confirmedNavChangePct;
    /** 昨收 */
    private Double previousClose;
    /** 涨跌幅（百分比，如 -3.1 表示 -3.1%） */
    private Double changePct;
    /** 涨跌额 */
    private Double changeAmount;
    /** 成交额（元） */
    private Double turnover;
    /** 成交量 */
    private Double volume;
    /** 今开 */
    private Double open;
    /** 当日最高 */
    private Double high;
    /** 当日最低 */
    private Double low;
    /** 振幅（百分比，(最高-最低)/昨收*100） */
    private Double amplitude;
    /** 行情时间 */
    private LocalDateTime quoteTime;
    /** 是否取到有效行情 */
    private boolean valid = true;
    /** 取数失败或估值等提示信息 */
    private String note;

    public String getInstrumentCode() {
        return instrumentCode;
    }

    public void setInstrumentCode(String instrumentCode) {
        this.instrumentCode = instrumentCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getConfirmedNav() { return confirmedNav; }
    public void setConfirmedNav(Double confirmedNav) { this.confirmedNav = confirmedNav; }
    public String getConfirmedNavDate() { return confirmedNavDate; }
    public void setConfirmedNavDate(String confirmedNavDate) { this.confirmedNavDate = confirmedNavDate; }
    public Double getConfirmedNavChangePct() { return confirmedNavChangePct; }
    public void setConfirmedNavChangePct(Double confirmedNavChangePct) { this.confirmedNavChangePct = confirmedNavChangePct; }

    public Double getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(Double previousClose) {
        this.previousClose = previousClose;
    }

    public Double getChangePct() {
        return changePct;
    }

    public void setChangePct(Double changePct) {
        this.changePct = changePct;
    }

    public Double getChangeAmount() {
        return changeAmount;
    }

    public void setChangeAmount(Double changeAmount) {
        this.changeAmount = changeAmount;
    }

    public Double getTurnover() {
        return turnover;
    }

    public void setTurnover(Double turnover) {
        this.turnover = turnover;
    }

    public Double getVolume() {
        return volume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }

    public Double getOpen() {
        return open;
    }

    public void setOpen(Double open) {
        this.open = open;
    }

    public Double getHigh() {
        return high;
    }

    public void setHigh(Double high) {
        this.high = high;
    }

    public Double getLow() {
        return low;
    }

    public void setLow(Double low) {
        this.low = low;
    }

    public Double getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(Double amplitude) {
        this.amplitude = amplitude;
    }

    public LocalDateTime getQuoteTime() {
        return quoteTime;
    }

    public void setQuoteTime(LocalDateTime quoteTime) {
        this.quoteTime = quoteTime;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
