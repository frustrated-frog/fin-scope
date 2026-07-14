package com.finscope.domain.quant.factor;

import java.time.LocalDate;

public class FactorValue {
    /**
     * 信号日期。
     */
    private final LocalDate signalDate;
    /**
     * 标的代码。
     */
    private final String instrumentCode;
    /**
     * 因子编码。
     */
    private final String factorCode;
    /**
     * 数值。
     */
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
