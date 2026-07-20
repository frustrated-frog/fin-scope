package com.finscope.rpc.quant;

import com.finscope.domain.quant.data.QuantDailyBar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A normalized bar batch together with the upstream that actually produced it. */
public final class QuantDailyBarBatch {
    private final List<QuantDailyBar> bars;
    private final String sourceCode;
    private final String sourceFamily;
    private final String qualityStatus;
    private final LocalDate asOfDate;
    private final List<String> warnings;

    public QuantDailyBarBatch(List<QuantDailyBar> bars, String sourceCode, String sourceFamily,
                              String qualityStatus, LocalDate asOfDate, List<String> warnings) {
        this.bars = Collections.unmodifiableList(new ArrayList<QuantDailyBar>(bars));
        this.sourceCode = sourceCode;
        this.sourceFamily = sourceFamily;
        this.qualityStatus = qualityStatus;
        this.asOfDate = asOfDate;
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public List<QuantDailyBar> getBars() {
        return bars;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getSourceFamily() {
        return sourceFamily;
    }

    public String getQualityStatus() {
        return qualityStatus;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean isDegraded() {
        return !"FRESH_PRIMARY".equals(qualityStatus);
    }
}
