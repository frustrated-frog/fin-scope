package com.finscope.service.financials;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.instrument.Instrument;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import com.finscope.common.exception.BizErrorCode;

@Service
public class GlobalFinancialRefreshService {
    private final InstrumentRepository instruments;
    private final FinancialRefreshService refresh;

    public GlobalFinancialRefreshService(InstrumentRepository instruments,
                                         FinancialRefreshService refresh) {
        this.instruments = instruments;
        this.refresh = refresh;
    }

    public FinancialReportView refresh(String providerCode, String providerCompanyId,
                                       String displayName, String symbol, String exchange,
                                       LocalDate periodEnd, FinancialReportType reportType) {
        boolean sec = "SEC_EDGAR".equals(providerCode);
        boolean dart = "KRX_KIND".equals(providerCode);
        if (!sec && !dart) {
            throw new BusinessException(BizErrorCode.FINANCIALS_PROVIDER_UNSUPPORTED, providerCode);
        }
        String normalizedSymbol = required(symbol, "股票代码").toUpperCase(Locale.ROOT);
        String market = sec ? "US" : "KR";
        String alias = sec ? "SEC_CIK:" + normalizeCik(providerCompanyId)
                : "KRX_SYMBOL:" + normalizeKrxSymbol(providerCompanyId, normalizedSymbol);
        Instrument instrument = instruments.findByCodeTypeAndMarket(normalizedSymbol, "STOCK", market)
                .map(existing -> update(existing, displayName, market, alias))
                .orElseGet(() -> create(normalizedSymbol, displayName, market, alias));
        return refresh.refresh(instrument.getId(), periodEnd, reportType);
    }

    private Instrument create(String symbol, String name, String market, String alias) {
        Instrument value = new Instrument();
        value.setCode(symbol);
        value.setType("STOCK");
        value.setName(required(name, "公司名称"));
        value.setMarket(market);
        value.setAliases(alias);
        return instruments.save(value);
    }

    private Instrument update(Instrument value, String name, String market, String alias) {
        boolean changed = false;
        if (!alias.equals(value.getAliases())) {
            value.setAliases(alias);
            changed = true;
        }
        if (!market.equals(value.getMarket())) {
            value.setMarket(market);
            changed = true;
        }
        if (name != null && !name.trim().isEmpty() && !name.trim().equals(value.getName())) {
            value.setName(name.trim());
            changed = true;
        }
        return changed ? instruments.update(value) : value;
    }

    private String normalizeCik(String value) {
        String digits = required(value, "SEC CIK").replaceAll("\\D", "");
        if (digits.isEmpty()) {
            throw new BusinessException(BizErrorCode.SEC_CIK_INVALID);
        }
        try {
            return String.format("%010d", Long.parseLong(digits));
        } catch (NumberFormatException error) {
            throw new BusinessException(BizErrorCode.SEC_CIK_INVALID);
        }
    }

    private String normalizeKrxSymbol(String providerCompanyId, String fallbackSymbol) {
        String digits = required(providerCompanyId, "KRX 公司代码").replaceAll("\\D", "");
        if (digits.length() != 6) digits = fallbackSymbol.replaceAll("\\D", "");
        if (digits.length() != 6) {
            throw new BusinessException(BizErrorCode.KRX_SYMBOL_INVALID);
        }
        return digits;
    }

    private String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, label + "不能为空");
        }
        return value.trim();
    }
}
