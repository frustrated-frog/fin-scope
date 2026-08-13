package com.finscope.rpc.financials;

import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.domain.instrument.Instrument;

import java.time.LocalDate;

public interface StructuredFinancialDataGateway {
    boolean supports(Instrument instrument);

    String providerCode();

    ExternalFinancialStatements fetch(Instrument instrument, LocalDate periodEnd,
                                      FinancialReportType reportType);
}
