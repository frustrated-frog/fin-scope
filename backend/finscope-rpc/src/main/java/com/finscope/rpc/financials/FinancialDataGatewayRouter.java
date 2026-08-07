package com.finscope.rpc.financials;

import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Primary
public class FinancialDataGatewayRouter implements StructuredFinancialDataGateway {
    private final List<StructuredFinancialDataGateway> gateways;

    @Autowired
    public FinancialDataGatewayRouter(PythonFinancialDataClient python,
                                      SecFinancialDataClient sec,
                                      DartFinancialDataClient dart) {
        this(java.util.Arrays.<StructuredFinancialDataGateway>asList(python, sec, dart));
    }

    FinancialDataGatewayRouter(List<StructuredFinancialDataGateway> gateways) {
        this.gateways = gateways;
    }

    @Override
    public boolean supports(Instrument instrument) {
        for (StructuredFinancialDataGateway gateway : gateways) {
            if (gateway.supports(instrument)) return true;
        }
        return false;
    }

    @Override
    public String providerCode() {
        return "FINANCIAL_DATA_ROUTER";
    }

    @Override
    public ExternalFinancialStatements fetch(Instrument instrument, LocalDate periodEnd,
                                             FinancialReportType reportType) {
        for (StructuredFinancialDataGateway gateway : gateways) {
            if (gateway.supports(instrument)) {
                return gateway.fetch(instrument, periodEnd, reportType);
            }
        }
        String market = instrument == null ? null : instrument.getMarket();
        throw new ProviderContractException("FINANCIAL_INSTRUMENT_UNSUPPORTED",
                "暂不支持该市场的结构化财报抓取：" + (market == null ? "UNKNOWN" : market), false);
    }
}
