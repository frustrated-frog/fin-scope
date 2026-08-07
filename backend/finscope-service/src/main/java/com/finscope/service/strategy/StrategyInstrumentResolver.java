package com.finscope.service.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.Quote;
import com.finscope.service.instrument.QuoteService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class StrategyInstrumentResolver {
    private static final Pattern CODE = Pattern.compile("\\d{6}");
    @Resource
    private InstrumentRepository instrumentRepository;
    @Resource
    private QuoteService quoteService;

    public Instrument resolve(String rawCode, String rawType) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        String type = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "标的代码必须是 6 位数字");
        }
        if (!"FUND".equals(type) && !"STOCK".equals(type)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "策略组合只支持基金和股票");
        }
        if ("STOCK".equals(type)) {
            return instrumentRepository.findByCodeTypeAndMarket(code, type, stockMarket(code))
                    .orElseGet(() -> create(code, type));
        }
        return instrumentRepository.findByCodeAndType(code, type).orElseGet(() -> create(code, type));
    }

    private Instrument create(String code, String type) {
        Instrument value = new Instrument();
        value.setCode(code);
        value.setType(type);
        value.setName(resolveName(code, type));
        value.setAliases(code);
        if ("STOCK".equals(type)) {
            value.setMarket(stockMarket(code));
        }
        return instrumentRepository.save(value);
    }

    private String stockMarket(String code) {
        if (code.startsWith("6")) return "SH";
        if (code.startsWith("4") || code.startsWith("8") || code.startsWith("92")) return "BJ";
        if (code.startsWith("9")) return "SH";
        return "SZ";
    }

    private String resolveName(String code, String type) {
        try {
            List<Quote> quotes = quoteService.fetch(type, Collections.singletonList(code));
            return quotes.isEmpty() || quotes.get(0).getName() == null
                    ? code : quotes.get(0).getName();
        } catch (RuntimeException ignored) {
            return code;
        }
    }
}
