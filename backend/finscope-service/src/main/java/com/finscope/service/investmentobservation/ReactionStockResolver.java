package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionResolutionStatus;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.investmentobservation.ReactionEventRules;
import com.finscope.domain.investmentobservation.ReactionStockMatch;
import com.finscope.domain.investmentobservation.ReactionStockResolution;
import com.finscope.rpc.investmentobservation.ReactionStockNameLookup;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 只关联执行事件动作的主体；正文提及或价格上涨本身不构成关联依据。 */
@Service
public class ReactionStockResolver {
    @Resource
    private InstrumentRepository instruments;
    @Resource
    private ReactionStockNameLookup names;
    private final ReactionEventRules rules = new ReactionEventRules();

    public List<ReactionStockMatch> resolve(String title) {
        return resolve(title, null).getMatches();
    }

    public ReactionStockResolution resolve(String title, String body) {
        ReactionStockResolution result = new ReactionStockResolution();
        Set<String> subjects = new LinkedHashSet<>(rules.evaluate(title).getSubjects());
        if (body != null) {
            // 限制材料大小和外部查询数量；按句提取动作主体，排除泛化的公司提及。
            String bounded = body.substring(0, Math.min(body.length(), 12000));
            for (String sentence : bounded.split("[。；;\\n]")) {
                String clean = sentence.replaceFirst("^.*?(?:电[，,]|消息[，,])", "").trim();
                subjects.addAll(rules.evaluate(clean).getSubjects());
                if (subjects.size() >= 8) {
                    break;
                }
            }
        }
        List<Instrument> local = instruments.findAll();
        boolean unavailable = false;
        boolean ambiguous = false;
        List<String> evidence = new ArrayList<>();
        int attempted = 0;
        for (String subject : subjects) {
            if (++attempted > 8) {
                break;
            }
            String plain = subject.replaceAll("[（(][0-9]{6}(?:\\.(?:SH|SZ|BJ))?[）)]", "")
                    .replaceFirst("^(公司|本公司)$", "").trim();
            if (plain.length() < 2 || plain.length() > 45) {
                continue;
            }
            var codeMatch = Pattern.compile("(?<![0-9])([603489][0-9]{5})(?![0-9])").matcher(subject);
            String code = codeMatch.find() ? codeMatch.group(1) : null;
            List<ReactionStockMatch> candidates = new ArrayList<>();
            for (Instrument instrument : local) {
                if (!"STOCK".equals(instrument.getType()) || !Set.of("SH", "SZ", "BJ").contains(String.valueOf(instrument.getMarket()))) {
                    continue;
                }
                String localCode = instrument.getCode().replaceAll("\\.(SH|SZ|BJ)$", "");
                boolean nameMatches = plain.equals(instrument.getName()) || aliases(instrument.getAliases()).contains(plain);
                if (nameMatches && (code == null || code.equals(localCode)) || code != null && plain.equals(code) && code.equals(localCode)) {
                    add(candidates, localCode, instrument.getName());
                }
            }
            if (candidates.isEmpty()) {
                try {
                    for (ReactionStockMatch candidate : names.search(code == null ? plain : code)) {
                        if ((code == null && plain.equals(candidate.getName()))
                                || code != null && code.equals(candidate.getCode().replaceAll("\\.(SH|SZ|BJ)$", ""))
                                && (plain.equals(code) || plain.equals(candidate.getName()))) {
                            add(candidates, candidate.getCode().replaceAll("\\.(SH|SZ|BJ)$", ""), candidate.getName());
                        }
                    }
                } catch (RuntimeException ex) {
                    unavailable = true;
                }
            }
            if (candidates.size() > 1) {
                ambiguous = true;
            } else if (candidates.size() == 1) {
                ReactionStockMatch match = candidates.get(0);
                add(result.getMatches(), match.getCode().replaceAll("\\.(SH|SZ|BJ)$", ""), match.getName());
                evidence.add("材料中的动作主体“" + subject + "”对应 " + match.getName() + "（" + match.getCode() + "）");
            }
        }
        result.setStatus(!result.getMatches().isEmpty() ? ReactionResolutionStatus.RESOLVED
                : ambiguous ? ReactionResolutionStatus.AMBIGUOUS
                : unavailable ? ReactionResolutionStatus.LOOKUP_UNAVAILABLE : ReactionResolutionStatus.NO_SUBJECT);
        result.setEvidence(String.join("；", evidence));
        return result;
    }

    private List<String> aliases(String aliases) {
        return aliases == null ? List.of() : Arrays.stream(aliases.split("[,，;；|\\n\\[\\]\"]"))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
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
