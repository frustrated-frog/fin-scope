package com.finscope.service.research.quality;

import com.finscope.common.enums.research.ResearchEvidenceIssue;
import com.finscope.common.enums.research.ResearchFactKind;
import com.finscope.common.enums.research.ResearchValueRelation;
import com.finscope.common.util.WebContentText;
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
    private static final Pattern EVENT_DATE = Pattern.compile("(?:发生日期|事件日期|交易日期)\\s*[:：]\\s*((?:20\\d{2})[-年/](?:\\d{1,2})[-月/](?:\\d{1,2})日?)");

    public List<StructuredResearchFact> extract(ResearchTaskContract contract, ResearchEvidenceDocument doc) {
        List<StructuredResearchFact> facts = new ArrayList<>();
        String raw = text(doc.getText());
        Matcher sentences = SENTENCE.matcher(WebContentText.clean(raw));
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
        if (!eventBelongsToSubject(contract, quote)) {
            fact.getIssues().add(ResearchEvidenceIssue.ENTITY_MISMATCH);
        }
        if (!WebContentText.clean(quote).equals(quote)) {
            fact.getIssues().add(ResearchEvidenceIssue.NOISY_CONTENT);
        }
        // Dates and scopes are always derived from the cited text, not trusted from the model.
        Set<String> dates = dates(quote);
        if (dates.isEmpty()) {
            Matcher explicitDate = EVENT_DATE.matcher(text(doc.getText()));
            if (explicitDate.find()) {
                dates = dates(explicitDate.group(1));
                if (explicitDate.find()) {
                    dates = Collections.emptySet();
                }
            }
        }
        fact.setEventDate(dates.size() == 1 ? dates.iterator().next() : null);
        Matcher period = PERIOD.matcher(quote);
        String scope = quote.matches("(?s).*(单笔|一笔|该笔).*" ) ? "SINGLE_TRADE"
                : quote.matches("(?s).*(累计|合计|总额|全日|当日).*" ) ? "DAILY_TOTAL"
                : "REPORTED_EVENT";
        if (fact.getKind() == ResearchFactKind.PRICE) {
            scope += quote.contains("均价") ? "_AVERAGE_PRICE" : "_TRADE_PRICE";
        }
        fact.setScope(period.find() ? period.group() + "|" + scope
                : fact.getEventDate() == null ? null : fact.getEventDate() + "|" + scope);
        if (fact.getEventDate() != null && fact.getEventDate().compareTo(contract.getAsOfDate()) > 0) {
            fact.getIssues().add(ResearchEvidenceIssue.DATE_MISMATCH);
        }
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
        fact.setValueRelation(relation(fact, quote));
        if (fact.getKind() != null && numeric(fact.getKind())
                && quote.matches("(?s).*\\d+(?:\\.\\d+)?\\s*(?:亿|万|千)?\\s*[~～—至-]\\s*\\d+(?:\\.\\d+)?\\s*(?:亿|万|千)?\\s*(?:元|股|[%％]).*")) {
            fact.getIssues().add(ResearchEvidenceIssue.SCOPE_UNKNOWN);
        }
        if (fact.getKind() != null && numeric(fact.getKind()) && !belongsToSubject(contract, fact, quote)) {
            fact.getIssues().add(ResearchEvidenceIssue.ENTITY_MISMATCH);
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

    public boolean conflictingValues(StructuredResearchFact left, StructuredResearchFact right) {
        if (left.getValueRelation() == ResearchValueRelation.APPROXIMATE || right.getValueRelation() == ResearchValueRelation.APPROXIMATE) {
            return false;
        }
        ValueInterval first = interval(left);
        ValueInterval second = interval(right);
        return before(first, second) || before(second, first);
    }

    private ValueInterval interval(StructuredResearchFact fact) {
        BigDecimal value = new BigDecimal(normalizedValue(fact));
        return switch (fact.getValueRelation()) {
            case GREATER_THAN -> new ValueInterval(value, null, false, false);
            case AT_LEAST -> new ValueInterval(value, null, true, false);
            case LESS_THAN -> new ValueInterval(null, value, false, false);
            case AT_MOST -> new ValueInterval(null, value, false, true);
            default -> new ValueInterval(value, value, true, true);
        };
    }

    private boolean before(ValueInterval first, ValueInterval second) {
        if (first.maximum() == null || second.minimum() == null) {
            return false;
        }
        int comparison = first.maximum().compareTo(second.minimum());
        return comparison < 0 || (comparison == 0 && !(first.maximumInclusive() && second.minimumInclusive()));
    }

    private ResearchValueRelation relation(StructuredResearchFact fact, String quote) {
        int start = numberStart(fact, quote);
        String prefix = start < 0 ? "" : quote.substring(Math.max(0, start - 6), start).trim();
        if (prefix.matches(".*(不超过|至多|不高于)$")) {
            return ResearchValueRelation.AT_MOST;
        }
        if (prefix.matches(".*(至少|不低于|不少于)$")) {
            return ResearchValueRelation.AT_LEAST;
        }
        if (prefix.matches(".*(超过|涨超|高于|大于)$")) {
            return ResearchValueRelation.GREATER_THAN;
        }
        if (prefix.matches(".*(低于|不到|不足|小于)$")) {
            return ResearchValueRelation.LESS_THAN;
        }
        if (prefix.matches(".*(约|近|大约)$")) {
            return ResearchValueRelation.APPROXIMATE;
        }
        return ResearchValueRelation.EXACT;
    }

    private record ValueInterval(BigDecimal minimum, BigDecimal maximum, boolean minimumInclusive, boolean maximumInclusive) { }

    private boolean hasExactNumber(StructuredResearchFact fact, String quote) {
        return numberStart(fact, quote) >= 0;
    }

    private int numberStart(StructuredResearchFact fact, String quote) {
        Matcher numbers = NUMBER.matcher(quote);
        while (numbers.find()) {
            if (numbers.group(1).equals(fact.getValue())
                    && (text(numbers.group(2)) + numbers.group(3)).equals(fact.getUnit())
                    && numberKind(numbers.group(3), quote.substring(0, numbers.start())) == fact.getKind()) {
                return numbers.start();
            }
        }
        return -1;
    }

    private boolean belongsToSubject(ResearchTaskContract contract, StructuredResearchFact fact, String quote) {
        int number = numberStart(fact, quote);
        if (number < 0) {
            return false;
        }
        return nearestSubject(contract, quote.substring(0, number));
    }

    private boolean eventBelongsToSubject(ResearchTaskContract contract, String quote) {
        if ("研究命题".equals(contract.getEvent())) {
            return true;
        }
        Matcher action = Pattern.compile("股价上涨".equals(contract.getEvent())
                ? "上涨|涨幅|涨超|均涨|收涨" : Pattern.quote(contract.getEvent())).matcher(quote);
        return action.find() && nearestSubject(contract, quote.substring(0, action.start()));
    }

    private boolean nearestSubject(ResearchTaskContract contract, String before) {
        int target = -1;
        for (String alias : contract.getAliases()) {
            if (alias != null && !alias.isBlank()) {
                target = Math.max(target, before.toLowerCase(Locale.ROOT).lastIndexOf(alias.toLowerCase(Locale.ROOT)));
            }
        }
        // Known cross-entity contamination must not survive merely by sharing a sentence.
        for (String other : List.of("特斯拉", "Tesla", "兆易创新", "微软", "Microsoft", "亚马逊", "Amazon",
                "英伟达", "NVIDIA", "长鑫科技", "长电科技", "杭电股份")) {
            if (contract.getAliases().stream().noneMatch(other::equalsIgnoreCase)
                    && before.toLowerCase(Locale.ROOT).lastIndexOf(other.toLowerCase(Locale.ROOT)) > target) {
                return false;
            }
        }
        return target >= 0;
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
        String eventText = text.replaceAll("(?:发布时间|发布日期|更新时间|更新于|抓取时间)\\s*[:：]?\\s*20\\d{2}[-年/]\\d{1,2}[-月/]\\d{1,2}日?", "");
        Matcher matcher = DATE.matcher(eventText);
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
