package com.finscope.service.financials;

import com.finscope.common.enums.financials.FinancialInterpretationChapter;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.domain.financials.FinancialInterpretationSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** v5 的可机械验证约束；引用存在性不等价于语义蕴含，报告仍需人工核查。 */
public final class FinancialInterpretationReportValidator {
    private static final Set<String> ASSESSMENTS = Set.of("POSITIVE", "NEUTRAL", "NEGATIVE", "INSUFFICIENT_EVIDENCE");
    private static final Set<String> CONFIDENCES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z_])[-+]?\\d+(?:\\.\\d+)?");
    private static final Pattern CAUSAL_FACT = Pattern.compile("由于|源于|驱动|导致|意味着|预示|得益于|主要受|有保障|必然|一定会");
    private static final Pattern CONTINUOUS_TREND = Pattern.compile("连续|逐季|持续改善|持续恶化|趋势确立");
    private static final Pattern UNCONFIRMED_TREND = Pattern.compile(
            "(?:不能|不足以|无法|不代表|尚未|未能|尚不能|是否|能否|核查|观察|关注|验证|缺少|缺乏|没有)[^，。；！？]{0,24}(?:连续|逐季|持续改善|持续恶化|趋势确立)");

    private FinancialInterpretationReportValidator() {
    }

    public static void validate(FinancialInterpretation.Result result, FinancialEvidencePacket packet,
                                List<String> errors) {
        if (result.getSections() == null) {
            errors.add("sections 缺失");
            return;
        }
        Set<FinancialInterpretationChapter> actual = EnumSet.noneOf(FinancialInterpretationChapter.class);
        for (FinancialInterpretationSection section : result.getSections()) {
            if (section == null || section.getCode() == null || !actual.add(section.getCode())) {
                errors.add("sections 包含空项、未知或重复章节");
                continue;
            }
            String field = section.getCode().name();
            if ((section.getAssessment() == null || !ASSESSMENTS.contains(section.getAssessment())) || (section.getConfidence() == null || !CONFIDENCES.contains(section.getConfidence()))) {
                errors.add(field + " assessment/confidence 非法");
            }
            if (rank(section.getConfidence()) > rank(packet.getQualityCeiling())) {
                errors.add(field + " 置信度超过数据质量上限");
            }
            List<String> available = FinancialInterpretationReportFramework.plan(packet.getModelEvidence()).stream()
                    .filter(item -> item.getCode() == section.getCode()).findFirst().orElseThrow().getRefs();
            boolean insufficient = "INSUFFICIENT_EVIDENCE".equals(section.getAssessment());
            if (available.isEmpty() && !insufficient) {
                errors.add(field + " 缺少本章材料，必须标记证据不足");
            }
            if (insufficient && !"LOW".equals(section.getConfidence())) {
                errors.add(field + " 证据不足只能使用低置信度");
            }
            if (!insufficient && (empty(section.getFacts()) || empty(section.getAnalysis())
                    || empty(section.getCounterEvidence()) || empty(section.getWatchpoints()))) {
                errors.add(field + " 必须包含事实、分析、替代解释与后续验证");
            }
            if (available.isEmpty() && (!empty(section.getFacts()) || !empty(section.getAnalysis())
                    || !empty(section.getCounterEvidence()) || !empty(section.getWatchpoints()))) {
                errors.add(field + " 无公司材料，不得补造公司判断");
            }
            validateLimitations(section.getLimitations(), errors, field + ".limitations");
            validateText(section.getSummary(), section.getRefs(), packet, errors, field + ".summary", !insufficient);
            if (!insufficient && section.getRefs() != null && section.getRefs().stream().noneMatch(available::contains)) {
                errors.add(field + " 缺少与章节主题有关的证据");
            }
            validateClaims(section.getFacts(), "FACT", packet, errors, field + ".facts");
            validateClaims(section.getAnalysis(), "INFERENCE", packet, errors, field + ".analysis");
            validateClaims(section.getCounterEvidence(), "INFERENCE", packet, errors, field + ".counterEvidence");
            validateClaims(section.getWatchpoints(), "WATCHPOINT", packet, errors, field + ".watchpoints");
        }
        if (!actual.equals(EnumSet.allOf(FinancialInterpretationChapter.class))) {
            errors.add("sections 必须完整覆盖十个研究章节");
        }
        validateClaims(result.getExecutiveSummary(), null, packet, errors, "executiveSummary");
        validateClaims(result.getPeriodChanges(), null, packet, errors, "periodChanges");
        validateClaims(result.getCrossStatementInsights(), null, packet, errors, "crossStatementInsights");
        if (result.getCrossStatementInsights() == null) {
            return;
        }
        validateClaims(result.getPositiveSignals(), null, packet, errors, "positiveSignals");
        validateClaims(result.getRisks(), null, packet, errors, "risks");
        validateClaims(result.getTurningPoints(), null, packet, errors, "turningPoints");
        validateClaims(result.getWatchpoints(), null, packet, errors, "watchpoints");
        validateLimitations(result.getLimitations(), errors, "limitations");
        if (result.getDimensions() != null && !result.getDimensions().isEmpty()) {
            errors.add("新版使用sections，不重复生成dimensions");
        }
        for (FinancialInterpretation.Claim claim : result.getCrossStatementInsights()) {
            if (claim == null || claim.getRefs() == null) {
                continue;
            }
            Set<String> domains = new HashSet<>();
            for (String ref : claim.getRefs()) {
                if (ref == null) {
                    continue;
                }
                if (ref.startsWith("L_INCOME_")) {
                    domains.add("INCOME");
                } else if (ref.startsWith("L_BALANCE_SHEET_")) {
                    domains.add("BALANCE_SHEET");
                } else if (ref.startsWith("L_CASH_FLOW_")) {
                    domains.add("CASH_FLOW");
                }
            }
            if (domains.size() < 2) {
                errors.add("三表联动必须引用至少两个报表域的原始科目");
            }
        }
    }

    private static void validateLimitations(List<String> limitations, List<String> errors, String field) {
        if (limitations == null || limitations.size() > 8) {
            errors.add(field + " 必须为数组且不超过八项");
            return;
        }
        for (String limitation : limitations) {
            if (limitation == null || limitation.isBlank() || limitation.length() > 1800
                    || NUMBER.matcher(limitation).find()) {
                errors.add(field + " 必须为材料限制描述，无引用限制段落不得补写数字");
            }
        }
    }

    private static void validateClaims(List<FinancialInterpretation.Claim> claims, String expectedType,
                                       FinancialEvidencePacket packet, List<String> errors, String field) {
        if (claims == null) {
            errors.add(field + " 必须为数组");
            return;
        }
        if (claims.size() > 8) {
            errors.add(field + " 最多八条");
        }
        for (FinancialInterpretation.Claim claim : claims) {
            if (claim == null) {
                errors.add(field + " 不允许空项");
                continue;
            }
            if (expectedType != null && !expectedType.equals(claim.getClaimType())) {
                errors.add(field + " 判断类型必须是 " + expectedType);
            }
            if ((claim.getClaimType() == null || !Set.of("FACT", "INFERENCE", "WATCHPOINT").contains(claim.getClaimType()))) {
                errors.add(field + " 判断类型非法");
            }
            if ((claim.getConfidence() == null || !CONFIDENCES.contains(claim.getConfidence()))
                    || rank(claim.getConfidence()) > rank(packet.getQualityCeiling())
                    || ("INFERENCE".equals(claim.getClaimType()) && "HIGH".equals(claim.getConfidence()))) {
                errors.add(field + " 判断置信度非法或超过证据上限，原因推断最高为中置信度");
            }
            validateText(claim.getClaim(), claim.getRefs(), packet, errors, field, true);
            if (claim.getClaim() != null && "FACT".equals(claim.getClaimType())
                    && CAUSAL_FACT.matcher(claim.getClaim()).find()) {
                errors.add(field + " 原因、影响与承诺不能标为事实");
            }
        }
    }

    private static void validateText(String text, List<String> refs, FinancialEvidencePacket packet,
                                     List<String> errors, String field, boolean required) {
        if (text == null || text.isBlank() || text.length() > 1800) {
            errors.add(field + " 正文不能为空且不超过一千八百字符");
            return;
        }
        if (refs == null || (required && refs.isEmpty())) {
            errors.add(field + " 缺少证据引用");
            return;
        }
        Set<String> numbers = new HashSet<>();
        boolean hasTrend = false;
        for (String ref : refs) {
            FinancialEvidence evidence = packet.getEvidenceIndex().get(ref);
            if (evidence == null) {
                errors.add("引用不存在：" + ref);
                continue;
            }
            collectNumbers(numbers, evidence.getValue());
            collectNumbers(numbers, evidence.getDetail());
            collectNumbers(numbers, evidence.getPeriod());
            if ("TREND".equals(evidence.getType()) && evidence.getDetail() != null
                    && hasConsecutivePeriods(evidence)) {
                hasTrend = true;
            }
        }
        String assertedText = UNCONFIRMED_TREND.matcher(text).replaceAll("");
        if (CONTINUOUS_TREND.matcher(assertedText).find() && !hasTrend) {
            errors.add(field + " 连续趋势必须引用多时点趋势证据");
        }
        Matcher matcher = NUMBER.matcher(text.replace(",", ""));
        while (matcher.find()) {
            String normalized = new BigDecimal(matcher.group()).stripTrailingZeros().toPlainString();
            if (!numbers.contains(normalized)) {
                errors.add(field + " 数字未被本条引用支持：" + matcher.group());
            }
        }
    }

    private static boolean hasConsecutivePeriods(FinancialEvidence evidence) {
        String[] points = evidence.getDetail().split(";");
        if (points.length < 3) {
            return false;
        }
        java.time.LocalDate previous = null;
        for (String point : points) {
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(point.split("=")[0]);
                if (previous != null) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(previous, date);
                    boolean annual = evidence.getId().endsWith("_ANNUAL");
                    if (annual ? days < 300 || days > 430 : days < 60 || days > 120) {
                        return false;
                    }
                }
                previous = date;
            } catch (java.time.DateTimeException error) {
                return false;
            }
        }
        return true;
    }

    private static void collectNumbers(Set<String> numbers, String text) {
        if (text == null) {
            return;
        }
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            BigDecimal number = new BigDecimal(matcher.group());
            numbers.add(number.stripTrailingZeros().toPlainString());
            for (int scale = 0; scale <= 2; scale++) {
                numbers.add(number.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                numbers.add(number.setScale(scale, RoundingMode.DOWN).stripTrailingZeros().toPlainString());
            }
        }
    }

    private static boolean empty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static int rank(String confidence) {
        return "HIGH".equals(confidence) ? 3 : "MEDIUM".equals(confidence) ? 2 : 1;
    }
}
