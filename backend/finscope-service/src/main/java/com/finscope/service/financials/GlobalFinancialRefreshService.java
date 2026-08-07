package com.finscope.service.financials;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.instrument.Instrument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;

@Service
public class GlobalFinancialRefreshService {
    private final InstrumentRepository instruments;
    private final FinancialRefreshService refresh;

    public GlobalFinancialRefreshService(InstrumentRepository instruments,
                                         FinancialRefreshService refresh) {
        this.instruments = instruments;
        this.refresh = refresh;
    }

    @Transactional
    public FinancialReportView refresh(String providerCode, String providerCompanyId,
                                       String displayName, String symbol, String exchange,
                                       LocalDate periodEnd, FinancialReportType reportType) {
        if (!"SEC_EDGAR".equals(providerCode)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    "该公司目录暂未接入结构化财报抓取：" + providerCode);
        }
        String normalizedSymbol = required(symbol, "股票代码").toUpperCase(Locale.ROOT);
        String cik = normalizeCik(providerCompanyId);
        Instrument instrument = instruments.findByCodeAndType(normalizedSymbol, "STOCK")
                .map(existing -> update(existing, displayName, cik))
                .orElseGet(() -> create(normalizedSymbol, displayName, cik));
        return refresh.refresh(instrument.getId(), periodEnd, reportType);
    }

    private Instrument create(String symbol, String name, String cik) {
        Instrument value = new Instrument();
        value.setCode(symbol);
        value.setType("STOCK");
        value.setName(required(name, "公司名称"));
        value.setMarket("US");
        value.setAliases("SEC_CIK:" + cik);
        return instruments.save(value);
    }

    private Instrument update(Instrument value, String name, String cik) {
        boolean changed = false;
        String alias = "SEC_CIK:" + cik;
        if (!alias.equals(value.getAliases())) {
            value.setAliases(alias);
            changed = true;
        }
        if (!"US".equals(value.getMarket())) {
            value.setMarket("US");
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
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "SEC CIK 格式不正确");
        }
        try {
            return String.format("%010d", Long.parseLong(digits));
        } catch (NumberFormatException error) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "SEC CIK 格式不正确");
        }
    }

    private String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, label + "不能为空");
        }
        return value.trim();
    }
}
