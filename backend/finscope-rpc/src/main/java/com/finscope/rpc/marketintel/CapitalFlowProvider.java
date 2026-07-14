package com.finscope.rpc.marketintel;

import com.finscope.domain.instrument.Instrument;

import java.time.LocalDate;

public interface CapitalFlowProvider {
    String providerCode();
    boolean supports(Instrument instrument);
    CapitalFlowData fetch(Instrument instrument, LocalDate asOfDate);
}
