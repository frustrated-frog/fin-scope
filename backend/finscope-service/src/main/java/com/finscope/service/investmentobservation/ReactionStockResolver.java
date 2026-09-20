package com.finscope.service.investmentobservation;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.investmentobservation.ReactionEventRules;
import com.finscope.domain.investmentobservation.ReactionStockMatch;
import com.finscope.rpc.investmentobservation.ReactionStockNameLookup;
import com.finscope.service.instrument.QuoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 纯规则模式：不注入也不调用模型。证券身份核验与主体提取分别执行。 */
@Service
@Slf4j
public class ReactionStockResolver {
    @Resource
    private InstrumentRepository instruments;
    @Resource
    private QuoteService quotes;
    @Resource
    private ReactionStockNameLookup names;
    private final ReactionEventRules rules = new ReactionEventRules();

    public List<ReactionStockMatch> resolve(String title) {
        List<ReactionStockMatch> matches = new ArrayList<>();
        List<Instrument> local = instruments.findAll();
        for (String subject : rules.evaluate(title).getSubjects()) {
            String plain = subject.replaceAll("[（(][0-9]{6}[）)]", "").trim();
            boolean found = false;
            for (Instrument instrument : local) {
                if ("STOCK".equals(instrument.getType()) && Set.of("SH", "SZ", "BJ").contains(String.valueOf(instrument.getMarket()))
                        && plain.equals(instrument.getName())) {
                    add(matches, instrument.getCode().replaceAll("\\.(SH|SZ|BJ)$", ""), instrument.getName());
                    found = true;
                }
            }
            if (found) {
                continue;
            }
            try {
                if (plain.matches("[603489][0-9]{5}")) {
                    for (var quote : quotes.fetch("STOCK", List.of(plain))) {
                        if (quote.isValid() && quote.getInstrumentCode() != null
                                && plain.equals(quote.getInstrumentCode().replaceAll("\\.(SH|SZ|BJ)$", ""))) {
                            add(matches, plain, quote.getName());
                        }
                    }
                } else {
                    for (ReactionStockMatch match : names.search(plain)) {
                        if (plain.equals(match.getName())) {
                            add(matches, match.getCode(), match.getName());
                        }
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("reaction identity lookup unavailable subject={} exceptionType={}", plain, ex.getClass().getSimpleName());
            }
        }
        return matches;
    }

    private void add(List<ReactionStockMatch> matches, String code, String name) {
        if (code == null || name == null || !code.matches("[603489][0-9]{5}") || matches.size() >= 4) {
            return;
        }
        String canonical = code + (code.startsWith("6") ? ".SH" : code.startsWith("0") || code.startsWith("3") ? ".SZ" : ".BJ");
        if (matches.stream().anyMatch(value -> canonical.equals(value.getCode()))) {
            return;
        }
        ReactionStockMatch match = new ReactionStockMatch();
        match.setCode(canonical);
        match.setName(name);
        matches.add(match);
    }
}
