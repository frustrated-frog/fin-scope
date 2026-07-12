package com.finscope.domain.quant.factor;

import java.time.LocalDate;

public class FactorValue {
    private final LocalDate signalDate;
    private final String instrumentCode;
    private final String factorCode;
    private final double value;

    public FactorValue(LocalDate signalDate, String instrumentCode, String factorCode, double value) {
        this.signalDate = signalDate; this.instrumentCode = instrumentCode;
        this.factorCode = factorCode; this.value = value;
    }
    public LocalDate getSignalDate() { return signalDate; }
    public String getInstrumentCode() { return instrumentCode; }
    public String getFactorCode() { return factorCode; }
    public double getValue() { return value; }
}
