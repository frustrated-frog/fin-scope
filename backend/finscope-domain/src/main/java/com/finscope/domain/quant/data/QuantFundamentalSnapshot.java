package com.finscope.domain.quant.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public class QuantFundamentalSnapshot {
    private Long id;
    private Long datasetId;
    private String instrumentCode;
    private LocalDate reportPeriod;
    private LocalDate disclosedAt;
    private BigDecimal pe;
    private BigDecimal pb;
    private BigDecimal marketCap;
    private BigDecimal roe;
    private BigDecimal revenueGrowth;
    private BigDecimal profitGrowth;
    private BigDecimal debtRatio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getInstrumentCode() { return instrumentCode; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }
    public LocalDate getReportPeriod() { return reportPeriod; }
    public void setReportPeriod(LocalDate reportPeriod) { this.reportPeriod = reportPeriod; }
    public LocalDate getDisclosedAt() { return disclosedAt; }
    public void setDisclosedAt(LocalDate disclosedAt) { this.disclosedAt = disclosedAt; }
    public BigDecimal getPe() { return pe; }
    public void setPe(BigDecimal pe) { this.pe = pe; }
    public BigDecimal getPb() { return pb; }
    public void setPb(BigDecimal pb) { this.pb = pb; }
    public BigDecimal getMarketCap() { return marketCap; }
    public void setMarketCap(BigDecimal marketCap) { this.marketCap = marketCap; }
    public BigDecimal getRoe() { return roe; }
    public void setRoe(BigDecimal roe) { this.roe = roe; }
    public BigDecimal getRevenueGrowth() { return revenueGrowth; }
    public void setRevenueGrowth(BigDecimal revenueGrowth) { this.revenueGrowth = revenueGrowth; }
    public BigDecimal getProfitGrowth() { return profitGrowth; }
    public void setProfitGrowth(BigDecimal profitGrowth) { this.profitGrowth = profitGrowth; }
    public BigDecimal getDebtRatio() { return debtRatio; }
    public void setDebtRatio(BigDecimal debtRatio) { this.debtRatio = debtRatio; }
}
