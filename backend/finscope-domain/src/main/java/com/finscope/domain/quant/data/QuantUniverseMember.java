package com.finscope.domain.quant.data;

import java.time.LocalDate;

public class QuantUniverseMember {
    private Long datasetId; private LocalDate tradeDate; private String instrumentCode;
    private boolean member = true; private String sourceKind;
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getInstrumentCode() { return instrumentCode; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }
    public boolean isMember() { return member; }
    public void setMember(boolean member) { this.member = member; }
    public String getSourceKind() { return sourceKind; }
    public void setSourceKind(String sourceKind) { this.sourceKind = sourceKind; }
}
