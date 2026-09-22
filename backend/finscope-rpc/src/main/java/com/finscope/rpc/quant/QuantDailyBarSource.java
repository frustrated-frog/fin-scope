package com.finscope.rpc.quant;

/** Supplies normalized, provenance-carrying daily bars for quant research. */
public interface QuantDailyBarSource {

    QuantDailyBarBatch fetch(String instrumentCode, int limit);
    /** Settlement may ignore history preceding its earliest required reference date. */
    default QuantDailyBarBatch fetchSince(String instrumentCode, int limit, java.time.LocalDate fromDate) {
        return fetch(instrumentCode, limit);
    }
}

