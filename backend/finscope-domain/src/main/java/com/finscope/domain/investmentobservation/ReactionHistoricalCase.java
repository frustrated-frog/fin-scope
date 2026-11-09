package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** 只归并有明确共同事实的报道；原始样本、时间和行情计算独立保存。 */
public record ReactionHistoricalCase(ReactionEventSubtype subtype, String key, String topicKey) {
    private static final ReactionEventRules RULES = new ReactionEventRules();
    private static final Pattern YEAR = Pattern.compile("(20[0-9]{2})年");
    private static final Pattern QUARTER_ONE = Pattern.compile("第一季度|一季度|首季|一季报|Q1|1[至-]3月");
    private static final Pattern HALF = Pattern.compile("半年度|上半年|半年报|1[至-]6月");
    private static final Pattern QUARTER_TWO = Pattern.compile("第二季度|二季度|Q2");
    private static final Pattern QUARTER_THREE = Pattern.compile("前三季度|第三季度|三季度|三季报|Q3|1[至-]9月");
    private static final Pattern QUARTER_FOUR = Pattern.compile("第四季度|四季度|Q4");
    private static final Pattern ANNUAL = Pattern.compile("年度|年报|全年");
    private static final Pattern PROJECT = Pattern.compile("《([^《》]{4,80}(?:项目|合同|协议))》");
    private static final Pattern COUNTERPARTY = Pattern.compile("与([\\p{IsHan}A-Za-z0-9·（）()]{3,26})(?:正式)?签[订署]");
    private static final Pattern AMENDMENT_AMOUNT = Pattern.compile("(?:调增至|调减至|调整为|上调至|下调至|提高至|变更为|升至|增至|降至|至)\\s*(?:人民币)?([0-9]+(?:\\.[0-9]+)?)(亿元|万元|元)");
    private static final Pattern AMOUNT = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(亿元|万元|元)");
    private static final Pattern CHANGE_RATE = Pattern.compile("(?:较原|比原)[^0-9%]{0,20}([0-9]+(?:\\.[0-9]+)?)%");
    private static final Pattern AMENDMENT_KIND = Pattern.compile("(算力服务|算力设备|算力板卡|电解液|设备采购|施工总承包)(?:合同|协议)");

    public ReactionHistoricalCase(ReactionEventSubtype subtype, String key) {
        this(subtype, key, null);
    }

    public static ReactionHistoricalCase from(ReactionSample sample) {
        return from(sample.getTitle(), sample.getSummary(), sample.getInstrumentName(), sample.getInstrumentCode(), sample.getPublishedAt(),
                sample.getEventSubtype(), sample.isAutomatic(), sample.getRuleVersion());
    }

    public static ReactionHistoricalCase from(String title, String summary, String name, String code, LocalDateTime publishedAt,
                                              ReactionEventSubtype original, boolean automatic, String ruleVersion) {
        ReactionEventSubtype fallback = original == null ? ReactionEventSubtype.UNCLASSIFIED : original;
        if (title == null || code == null || code.isBlank()) {
            return new ReactionHistoricalCase(fallback, null);
        }
        var decision = RULES.evaluate(title);
        String bareCode = code.replaceFirst("\\.(SH|SZ|BJ)$", "");
        boolean sameCompany = decision.getSubjects().stream().anyMatch(subject -> {
            String plain = subject.replaceFirst("[（(][0-9]{6}(?:\\.(?:SH|SZ|BJ))?[）)]$", "").strip();
            return plain.equals(name) || plain.equals(code) || plain.equals(bareCode)
                    || subject.contains("(" + bareCode + ")") || subject.contains("（" + bareCode + "）")
                    || subject.contains("(" + code + ")") || subject.contains("（" + code + "）");
        });
        if (!sameCompany && !(automatic && ReactionEventDecision.CURRENT_RULE_VERSION.equals(ruleVersion)
                && fallback.isSpecific() && fallback == decision.getSubtype())) {
            return new ReactionHistoricalCase(automatic ? ReactionEventSubtype.OPERATING_UPDATE : fallback, null);
        }
        ReactionEventSubtype subtype = decision.getSubtype();
        if (!subtype.isSpecific() || publishedAt == null) {
            return new ReactionHistoricalCase(subtype, null);
        }
        String prefix = code + "|" + subtype + "|";
        boolean earnings = decision.getEventType() == com.finscope.common.enums.investmentobservation.ReactionEventType.EARNINGS;
        String period = earnings ? earningsPeriod(title, publishedAt.toLocalDate()) : null;
        String project = earnings ? null : namedProject(title);
        String topicKey = period != null ? code + "|EARNINGS|" + period
                : project != null ? code + "|PROJECT|" + project : null;
        if (decision.getMergeAnchor() != null) {
            return new ReactionHistoricalCase(subtype,
                    prefix + "NOTICE|" + decision.getMergeAnchor().substring(decision.getMergeAnchor().lastIndexOf('|') + 1), topicKey);
        }
        String detail = earnings ? period == null ? null : period + "|" + (title.contains("快报") ? "FLASH" : "STANDARD")
                : project != null ? "PROJECT|" + project : contractFact(title, summary, subtype);
        return new ReactionHistoricalCase(subtype, detail == null ? null : prefix + publishedAt.toLocalDate() + "|" + detail, topicKey);
    }

    private static String earningsPeriod(String title, LocalDate date) {
        String normalized = title.toUpperCase(java.util.Locale.ROOT).replace('－', '-').replace('—', '-');
        String period;
        if (QUARTER_THREE.matcher(normalized).find()) {
            period = "Q3";
        } else if (HALF.matcher(normalized).find()) {
            period = "H1";
        } else if (QUARTER_ONE.matcher(normalized).find()) {
            period = "Q1";
        } else if (QUARTER_TWO.matcher(normalized).find()) {
            period = "Q2";
        } else if (QUARTER_FOUR.matcher(normalized).find()) {
            period = "Q4";
        } else if (ANNUAL.matcher(normalized).find()) {
            period = "FY";
        } else {
            return null;
        }
        var explicitYear = YEAR.matcher(normalized);
        int year;
        if (explicitYear.find()) {
            year = Integer.parseInt(explicitYear.group(1));
            if (explicitYear.find() && year != Integer.parseInt(explicitYear.group(1))) {
                return null;
            }
        } else if (normalized.contains("去年") || period.equals("FY") && date.getMonthValue() <= 4) {
            year = date.getYear() - 1;
        } else {
            year = date.getYear();
        }
        return year + "|" + period;
    }

    private static String namedProject(String title) {
        var project = PROJECT.matcher(title);
        if (!project.find()) {
            return null;
        }
        String name = project.group(1).replaceAll("\\s", "");
        return name.length() >= 10 && !project.find() ? name : null;
    }

    private static String contractFact(String title, String summary, ReactionEventSubtype subtype) {
        if (subtype == ReactionEventSubtype.CONTRACT_AMENDED) {
            var amount = AMENDMENT_AMOUNT.matcher(title);
            if (!amount.find()) {
                return null;
            }
            String revisedAmount = yuan(amount.group(1), amount.group(2));
            String fingerprint = amendmentFact(title, revisedAmount);
            if (fingerprint == null && summary != null && (title.endsWith("...") || title.endsWith("…"))) {
                fingerprint = amendmentFact(summary.substring(0, Math.min(summary.length(), 1200)), revisedAmount);
            }
            if (fingerprint != null) {
                return fingerprint;
            }
        }
        if (subtype == ReactionEventSubtype.CONTRACT_SIGNED) {
            var counterparty = COUNTERPARTY.matcher(title);
            var amount = AMOUNT.matcher(title);
            if (counterparty.find() && amount.find()) {
                String value = yuan(amount.group(1), amount.group(2));
                if (!amount.find()) {
                    return "COUNTERPARTY|" + counterparty.group(1) + "|" + value;
                }
            }
        }
        return null;
    }

    private static String amendmentFact(String text, String revisedAmount) {
        var kind = AMENDMENT_KIND.matcher(text);
        if (!kind.find()) {
            return null;
        }
        String category = kind.group(1);
        while (kind.find()) {
            if (!category.equals(kind.group(1))) {
                return null;
            }
        }
        var amount = AMENDMENT_AMOUNT.matcher(text);
        while (amount.find()) {
            if (!revisedAmount.equals(yuan(amount.group(1), amount.group(2)))) {
                continue;
            }
            var rate = CHANGE_RATE.matcher(text.substring(amount.end(), Math.min(text.length(), amount.end() + 40)));
            if (rate.find()) {
                return "AMOUNT_CHANGE|" + category + "|" + revisedAmount
                        + "|" + new BigDecimal(rate.group(1)).stripTrailingZeros().toPlainString();
            }
        }
        return null;
    }

    private static String yuan(String value, String unit) {
        return new BigDecimal(value).movePointRight(unit.equals("亿元") ? 8 : unit.equals("万元") ? 4 : 0)
                .stripTrailingZeros().toPlainString();
    }
}
