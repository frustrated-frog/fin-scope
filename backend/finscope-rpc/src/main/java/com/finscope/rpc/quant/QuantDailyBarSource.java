package com.finscope.rpc.quant;

/** Supplies normalized, provenance-carrying daily bars for quant research. */
public interface QuantDailyBarSource {

    QuantDailyBarBatch fetch(String instrumentCode, int limit);
}
