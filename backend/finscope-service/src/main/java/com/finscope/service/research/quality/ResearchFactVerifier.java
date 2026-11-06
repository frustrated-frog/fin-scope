package com.finscope.service.research.quality;

import com.finscope.common.enums.research.ResearchEvidenceIssue;
import com.finscope.common.enums.research.ResearchFactKind;
import com.finscope.domain.research.quality.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Model candidates and deterministic candidates pass exactly the same grounding checks. */
@Component
public class ResearchFactVerifier {
    private static final Pattern DATE = Pattern.compile("(20\\d{2})[-年/](\\d{1,2})[-月/](\\d{1,2})日?");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\d.])([0-9]+(?:\\.[0-9]+)?)\\s*(亿|万|千)?\\s*(元|股|%|％)");
    private static final Pattern SENTENCE = Pattern.compile("[^。！？!?\\n]+[。！？!?]?");
    private static final Pattern PERIOD = Pattern.compile("20\\d{2}年?(?:[一二三四1-4]季(?:度)?|上半年|下半年|全年|年度)");

    public List<StructuredResearchFact> extract(ResearchTaskContract contract, ResearchEvidenceDocument doc) {
        List<StructuredResearchFact> facts = new ArrayList<>();
        String raw = text(doc.getText());
        Matcher sentences = SENTENCE.matcher(raw);
        while (sentences.find() && facts.size() < 24) {
            String quote = sentences.group().trim();
            if (!matchesEntity(contract, quote) || !matchesEvent(contract, quote)) {
                continue;
            }
            for (ResearchFactKind kind : ResearchFactKind.values()) {
                if (!matchesKind(kind, quote)) {
                    continue;
                }
                StructuredResearchFact fact = candidate(doc, kind, quote);
                if (numeric(kind)) {
                    Matcher numbers = NUMBER.matcher(quote);
                    while (numbers.find()) {
                        if (kind == numberKind(numbers.group(3), quote.substring(0, numbers.start()))) {
                            StructuredResearchFact number = candidate(doc, kind, quote);
                            number.setValue(numbers.group(1));
                            number.setUnit(text(numbers.group(2)) + numbers.group(3));
                            facts.add(verify(contract, doc, number));
                        }
                    }
                } else {
                    facts.add(verify(contract, doc, fact));
                }
            }
        }
        return facts;
    }

    public StructuredResearchFact verify(ResearchTaskContract contract, ResearchEvidenceDocument doc,
                                         StructuredResearchFact fact) {
        fact.setEvidenceRef(doc.getReference());
        fact.setSubject(contract.getSubject());
        fact.setIssues(new ArrayList<>());
        String quote = text(fact.getQuote());
        int start = quote.isBlank() ? -1 : text(doc.getText()).indexOf(quote);
        fact.setQuoteStart(start);
        fact.setQuoteEnd(start < 0 ? -1 : start + quote.length());
        if (start < 0) {
            fact.getIssues().add(ResearchEvidenceIssue.QUOTE_NOT_FOUND);
        }
        if (!matchesEntity(contract, quote)) {
            fact.getIssues().add(ResearchEvidenceIssue.ENTITY_MISMATCH);
        }
        if (!matchesEvent(contract, quote) || fact.getKind() == null || !matchesKind(fact.getKind(), quote)) {
            fact.getIssues().add(ResearchEvidenceIssue.EVENT_MISMATCH);
        }
        // Dates and scopes are always derived from the cited text, not trusted from the model.
        Set<String> dates = dates(quote);
        if (dates.isEmpty() && dates(text(doc.getText())).size() == 1) {
            dates = dates(text(doc.getText()));
        }
        fact.setEventDate(dates.size() == 1 ? dates.iterator().next() : null);
        Matcher period = PERIOD.matcher(quote);
        fact.setScope(period.find() ? period.group() : fact.getEventDate());
        if (contract.isDateRequired()) {
            if (fact.getEventDate() == null) {
                fact.getIssues().add(ResearchEvidenceIssue.DATE_MISSING);
            } else if (fact.getEventDate().compareTo(contract.getDateFrom()) < 0
                    || fact.getEventDate().compareTo(contract.getDateTo()) > 0) {
                fact.getIssues().add(ResearchEvidenceIssue.DATE_MISMATCH);
            }
        }
        if (quote.matches("(?s).*(预计|预测|假设|有望|可能|据传|传闻|若能|如果).*")) {
            fact.getIssues().add(ResearchEvidenceIssue.SCOPE_UNKNOWN);
        }
        if (fact.getKind() != null && numeric(fact.getKind()) && !hasExactNumber(fact, quote)) {
            fact.getIssues().add(ResearchEvidenceIssue.NUMBER_MISMATCH);
        }
        if (doc.isSnippetOnly()) {
            fact.getIssues().add(ResearchEvidenceIssue.SNIPPET_ONLY);
        }
        fact.setVerified(fact.getIssues().isEmpty());
        return fact;
    }

    public boolean matchesEntity(ResearchTaskContract contract, String value) {
        for (String alias : contract.getAliases()) {
            if (alias == null || alias.isBlank()) {
                continue;
            }
            String pattern = alias.matches("[A-Za-z0-9 .-]+")
                    ? "(?i)(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])" : Pattern.quote(alias);
            if (Pattern.compile(pattern).matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesEvent(ResearchTaskContract contract, String quote) {
        if ("研究命题".equals(contract.getEvent())) {
            return true;
        }
        if ("股价上涨".equals(contract.getEvent())) {
            return quote.matches("(?s).*(上涨|涨幅|涨超|均涨|收涨).*[%％].*");
        }
        return quote.contains(contract.getEvent());
    }

    public boolean matchesKind(ResearchFactKind kind, String quote) {
        return switch (kind) {
            case EVENT -> true;
            case VOLUME -> quote.contains("股") && quote.contains("成交");
            case AMOUNT -> quote.contains("成交额") || quote.contains("成交金额");
            case PRICE -> quote.contains("均价") || quote.contains("成交价");
            case CHANGE_RATE -> quote.matches("(?s).*(涨|跌|变动|增减).*[%％].*");
            case MARKET_SPACE -> quote.matches("(?s).*(市场规模|行业规模|渗透率|市场空间|需求规模).*?");
            case PROFIT_MODEL -> quote.matches("(?s).*(盈利模式|商业模式|收入来源|毛利率|主营业务).*?");
            case COMPETITION -> quote.matches("(?s).*(竞争|市场份额|壁垒|替代).*?");
            case GOVERNANCE -> quote.matches("(?s).*(治理|股权结构|控股股东|关联交易|资本配置).*?");
            case VALUATION -> quote.matches("(?s).*(估值|市盈率|市净率|自由现金流|PE|PB).*?");
            case COUNTERCHECK -> quote.matches("(?s).*(风险|下修|下调|不及|延迟|下降|亏损|反方).*?");
        };
    }

    public String normalizedValue(StructuredResearchFact fact) {
        BigDecimal value = new BigDecimal(fact.getValue());
        String unit = text(fact.getUnit());
        BigDecimal multiplier = unit.startsWith("亿") ? new BigDecimal("100000000")
                : unit.startsWith("万") ? new BigDecimal("10000")
                : unit.startsWith("千") ? new BigDecimal("1000") : BigDecimal.ONE;
        return value.multiply(multiplier).stripTrailingZeros().toPlainString();
    }

    public boolean numeric(ResearchFactKind kind) {
        return EnumSet.of(ResearchFactKind.VOLUME, ResearchFactKind.AMOUNT, ResearchFactKind.PRICE,
                ResearchFactKind.CHANGE_RATE).contains(kind);
    }

    private boolean hasExactNumber(StructuredResearchFact fact, String quote) {
        Matcher numbers = NUMBER.matcher(quote);
        while (numbers.find()) {
            if (numbers.group(1).equals(fact.getValue())
                    && (text(numbers.group(2)) + numbers.group(3)).equals(fact.getUnit())
                    && numberKind(numbers.group(3), quote.substring(0, numbers.start())) == fact.getKind()) {
                return true;
            }
        }
        return false;
    }

    private ResearchFactKind numberKind(String unit, String quote) {
        if ("股".equals(unit)) {
            return ResearchFactKind.VOLUME;
        }
        if ("%".equals(unit) || "％".equals(unit)) {
            return ResearchFactKind.CHANGE_RATE;
        }
        int amount = Math.max(quote.lastIndexOf("成交额"), quote.lastIndexOf("成交金额"));
        int price = Math.max(quote.lastIndexOf("均价"), quote.lastIndexOf("成交价"));
        return amount > price ? ResearchFactKind.AMOUNT : ResearchFactKind.PRICE;
    }

    private StructuredResearchFact candidate(ResearchEvidenceDocument doc, ResearchFactKind kind, String quote) {
        StructuredResearchFact fact = new StructuredResearchFact();
        fact.setEvidenceRef(doc.getReference());
        fact.setKind(kind);
        fact.setQuote(quote);
        return fact;
    }

    private Set<String> dates(String text) {
        Set<String> dates = new LinkedHashSet<>();
        Matcher matcher = DATE.matcher(text);
        while (matcher.find()) {
            try {
                dates.add(LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))).toString());
            } catch (java.time.DateTimeException invalidDate) {
                // Invalid source dates provide no temporal support.
                dates.add("INVALID_DATE");
            }
        }
        dates.remove("INVALID_DATE");
        return dates;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
