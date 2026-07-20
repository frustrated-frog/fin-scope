package com.finscope.rpc.financials;

import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.instrument.Instrument;

import java.time.LocalDate;

public interface StructuredFinancialDataGateway {
    ExternalFinancialStatements fetch(Instrument instrument, LocalDate periodEnd,
                                      FinancialReportType reportType);
}
