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
            String compact = line.replaceFirst("^\\s*(?:[-*>]\\s+|\\d+\\.\\s+)", "").trim();
            if (compact.isEmpty() || compact.startsWith("#") || compact.startsWith("|")
                    || compact.startsWith("**审计降级：**")) continue;
            List<String> paragraphRefs = matches(REF, compact);
            Matcher sentences = SENTENCE.matcher(compact);
            while (sentences.find()) {
                String raw = sentences.group().trim();
                if (raw.isEmpty() || raw.startsWith("**审计降级：**")) continue;
                List<String> refs = matches(REF, raw);
                if (refs.isEmpty() && !paragraphRefs.isEmpty()) refs = paragraphRefs;
                String text = REF.matcher(raw).replaceAll("").trim();
                List<String> numbers = matches(NUMBER, text);
                if (refs.isEmpty() && numbers.isEmpty()) continue;
                result.add(new ResearchClaim(raw, text, refs, numbers));
            }
        }
        return result;
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
