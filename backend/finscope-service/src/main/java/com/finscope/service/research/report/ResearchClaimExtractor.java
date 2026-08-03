package com.finscope.service.research.report;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResearchClaimExtractor {
    private static final Pattern REF = Pattern.compile("\\[(E\\d+)]");
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z])\\d+(?:\\.\\d+)?%?");
    private static final Pattern SENTENCE = Pattern.compile(
            "[^。！？!?]+(?:[。！？!?]+|$)(?:\\s*(?:\\[E\\d+])+)?");

    public List<ResearchClaim> extract(String markdown) {
        String value = markdown == null ? "" : markdown;
        int appendix = firstSection(value, "## 资料来源", "## 证据附录");
        if (appendix >= 0) value = value.substring(0, appendix);
        List<ResearchClaim> result = new ArrayList<ResearchClaim>();
        for (String line : value.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("> 研究日期：") || trimmed.startsWith("> 原始问题：")
                    || trimmed.startsWith("> 判断：")) continue;
            if (trimmed.startsWith("|")) {
                ResearchClaim tableClaim = tableFact(trimmed);
                if (tableClaim != null) result.add(tableClaim);
                continue;
            }
            String compact = line.replaceFirst("^\\s*(?:[-*>]\\s+|\\d+\\.\\s+)", "").trim();
            if (compact.isEmpty() || compact.startsWith("#")
                    || compact.startsWith("**审计降级：**")) continue;
            boolean explicitFact = isExplicitFact(compact);
            if (isReasoning(compact)) continue;
            List<String> paragraphRefs = matches(REF, compact);
            Matcher sentences = SENTENCE.matcher(compact);
            while (sentences.find()) {
                String raw = sentences.group().trim();
                if (raw.isEmpty() || raw.startsWith("**审计降级：**")) continue;
                List<String> refs = matches(REF, raw);
                if (refs.isEmpty() && !paragraphRefs.isEmpty()) refs = paragraphRefs;
                String text = cleanLabel(REF.matcher(raw).replaceAll("").trim());
                List<String> numbers = matches(NUMBER, text);
                if (!explicitFact && numbers.isEmpty() && (refs.isEmpty() || !isQualitativeFact(text))) continue;
                result.add(new ResearchClaim(raw, text, refs, numbers));
            }
        }
        return result;
    }

    private ResearchClaim tableFact(String line) {
        if (line.contains("可验证事实") || line.matches("^\\|?[\\s:|-]+\\|?$")) return null;
        String value = line;
        if (value.startsWith("|")) value = value.substring(1);
        if (value.endsWith("|")) value = value.substring(0, value.length() - 1);
        String[] cells = value.split("\\|", -1);
        if (cells.length < 4) return null;
        List<String> refs = matches(REF, cells[0]);
        String fact = REF.matcher(cells[3]).replaceAll("").trim();
        if (fact.isEmpty()) return null;
        return new ResearchClaim(fact, fact, refs, matches(NUMBER, fact));
    }

    private boolean isExplicitFact(String value) {
        return value.startsWith("**事实：**") || value.startsWith("事实：")
                || value.startsWith("**可验证事实：**") || value.startsWith("可验证事实：");
    }

    private boolean isReasoning(String value) {
        return value.startsWith("**推理：**") || value.startsWith("推理：")
                || value.startsWith("**判断：**") || value.startsWith("判断：")
                || value.startsWith("**另一种解释：**") || value.startsWith("另一种解释：")
                || value.startsWith("**AI 解读：**") || value.startsWith("AI 解读：");
    }

    private boolean isQualitativeFact(String value) {
        String text = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        if (containsAny(text, "可能", "或许", "预计", "预期", "推测", "意味着", "如果", "若", "取决于",
                "不能证明", "尚待", "仍需", "could", "may", "might", "if ", "suggests")) return false;
        return containsAny(text, "已经", "已获", "获得", "获批", "批准", "完成", "披露", "公告", "显示",
                "发生", "维持", "增长", "下降", "上调", "下调", "达到", "签署", "发布", "confirmed",
                "approved", "completed", "reported", "announced");
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private String cleanLabel(String value) {
        return value.replaceFirst("^\\*{0,2}(?:事实|可验证事实)：\\*{0,2}\\s*", "").trim();
    }

    private int firstSection(String value, String... headings) {
        int first = -1;
        for (String heading : headings) {
            int index = value.indexOf(heading);
            if (index >= 0 && (first < 0 || index < first)) first = index;
        }
        return first;
    }

    private List<String> matches(Pattern pattern, String value) {
        Set<String> result = new LinkedHashSet<String>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) result.add(matcher.groupCount() > 0 ? matcher.group(1) : matcher.group());
        return new ArrayList<String>(result);
    }
}
