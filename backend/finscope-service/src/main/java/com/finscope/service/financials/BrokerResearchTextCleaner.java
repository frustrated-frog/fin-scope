package com.finscope.service.financials;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Converts layout-oriented PDFBox text into analysis-oriented paragraphs. */
public final class BrokerResearchTextCleaner {
    private static final Pattern CONTACT_PREFIX = Pattern.compile(
            "^(?:公?邮箱|电话|手机|传真)\\s*[：:]\\s*\\S+\\s*");
    private static final Pattern LICENSE_PREFIX = Pattern.compile(
            "^(?:SAC\\s*)?执业证书编号\\s*[：:]\\s*[A-Z0-9]+\\s*");
    private static final Pattern CONTENTS_LINE = Pattern.compile(
            "^(?:图|表)?\\s*\\d+(?:[.．]\\d+)*\\s*[：:].*[.．·…]{4,}\\s*\\d*\\s*$|" +
                    "^\\d+(?:[.．]\\d+)*[.、．]?\\s+.*[.．·…]{4,}\\s*\\d+\\s*$");
    private static final Pattern STANDALONE_DATE = Pattern.compile(
            "^\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日$");
    private static final Pattern REPORT_HEADER = Pattern.compile(
            ".*[（(]\\d{6}\\.(?:SZ|SH)[）)].*(?:深度报告|公司点评|研究报告).*");
    private static final Pattern MARKET_SNAPSHOT = Pattern.compile(
            "^(?:收盘价|总市值|总股本|流通股本|ROE\\s*\\(TTM\\)|12\\s*月最高价|12\\s*月最低价).*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENDS_SENTENCE = Pattern.compile(".*[。！？；：:]$");

    public String clean(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String normalized = value.replace('\u0000', ' ')
                .replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        List<String> paragraphs = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : normalized.split("\\n")) {
            String line = normalizeLine(rawLine);
            if (line.isEmpty()) {
                flush(paragraphs, current);
                continue;
            }
            boolean bullet = line.startsWith("") || line.startsWith("■")
                    || line.startsWith("•") || line.startsWith("●");
            line = stripLayoutPrefix(line);
            if (line.isEmpty() || isNoise(line)) continue;
            if (bullet) {
                flush(paragraphs, current);
                line = line.replaceFirst("^[■•●]\\s*", "");
            }
            if (isSectionBoundary(line)) flush(paragraphs, current);
            append(current, line);
            if (bullet || ENDS_SENTENCE.matcher(line).matches()) flush(paragraphs, current);
        }
        flush(paragraphs, current);
        return join(paragraphs);
    }

    private String stripLayoutPrefix(String value) {
        String line = value;
        int bullet = firstBullet(line);
        if (bullet >= 0) return line.substring(bullet).trim();
        line = CONTACT_PREFIX.matcher(line).replaceFirst("").trim();
        line = LICENSE_PREFIX.matcher(line).replaceFirst("").trim();
        return line.replaceFirst("^[深度研报究\\s]*[（(]可公开[）)]\\s*", "").trim();
    }

    private int firstBullet(String value) {
        int result = -1;
        for (char marker : new char[]{'', '■', '•', '●'}) {
            int index = value.indexOf(marker);
            if (index >= 0 && (result < 0 || index < result)) result = index;
        }
        return result;
    }

    private boolean isNoise(String line) {
        if (line.matches("^[公司研究证券报告]{1,2}$")) return true;
        if (line.matches("^(?:目录|插图目录|表格目录|相关报告|股价走势)$")) return true;
        if (line.matches("^(?:深|度)?(?:买入|增持|中性|卖出)\\s*[（(].{0,8}[）)]$")) return true;
        if (line.matches("^[\\p{IsHan}·]{2,4}$")) return true;
        if (line.startsWith("本报告的风险等级为")
                || line.startsWith("本报告的信息均来自已公开信息")
                || line.startsWith("请务必阅读末页声明")
                || line.startsWith("资料来源：")
                || line.startsWith("主要数据 ")) return true;
        return CONTENTS_LINE.matcher(line).matches()
                || STANDALONE_DATE.matcher(line).matches()
                || REPORT_HEADER.matcher(line).matches()
                || MARKET_SNAPSHOT.matcher(line).matches();
    }

    private boolean isSectionBoundary(String line) {
        return line.matches("^(?:投资要点|投资建议|风险提示)[：:].*")
                || line.matches("^\\d+(?:[.．]\\d+)*[.、．]?\\s+.*")
                || line.matches("^[（(]\\d+[）)].*");
    }

    private void append(StringBuilder target, String line) {
        if (target.length() > 0 && needsSpace(target.charAt(target.length() - 1), line.charAt(0))) {
            target.append(' ');
        }
        target.append(line);
    }

    private boolean needsSpace(char left, char right) {
        return (isAsciiWord(left) && isAsciiWord(right))
                || (isAsciiWord(left) && Character.isDigit(right))
                || (Character.isDigit(left) && isAsciiWord(right));
    }

    private boolean isAsciiWord(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }

    private void flush(List<String> target, StringBuilder current) {
        String value = current.toString().replaceAll("\\s+", " ").trim();
        if (!value.isEmpty()) target.add(value);
        current.setLength(0);
    }

    private String normalizeLine(String line) {
        return line == null ? "" : line.replaceAll("[\\t ]+", " ").trim();
    }

    private String join(List<String> paragraphs) {
        StringBuilder result = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (result.length() > 0) result.append('\n');
            result.append(paragraph);
        }
        return result.toString();
    }
}
