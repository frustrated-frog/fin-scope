package com.finscope.service.investmentobservation;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 从已有简介整理原句。业务召回只使用政策词，地点和生产角色仅作为核对材料。 */
record PolicyCompanyContext(List<String> sentences, String background) {
    private static final Pattern LOCATION = Pattern.compile("注册地|注册地址|总部|位于|坐落|地处|生产基地|生产工厂|设厂");
    private static final Pattern ACTIVITY = Pattern.compile("主营|主要产品|从事|生产制造|研发制造|专业生产");

    static PolicyCompanyContext prepare(String description) {
        var sentences = Arrays.stream(description.split("(?<=[。！？；;])|[\\r\\n]+"))
                .map(String::strip).filter(sentence -> !sentence.isEmpty()).toList();
        var background = new LinkedHashSet<String>();
        sentences.stream().filter(s -> LOCATION.matcher(s).find()).limit(3).forEach(background::add);
        sentences.stream().filter(s -> ACTIVITY.matcher(s).find()).limit(2).forEach(background::add);
        return new PolicyCompanyContext(sentences, background.stream().map(s -> limit(s, 240))
                .collect(Collectors.joining("\n")));
    }

    String business(List<String> terms) {
        String matches = sentences.stream().filter(sentence -> {
            String normalized = sentence.toLowerCase(Locale.ROOT);
            return terms.stream().anyMatch(normalized::contains);
        }).limit(4).collect(Collectors.joining("\n"));
        return limit(matches, 1100);
    }

    private static String limit(String text, int length) {
        return text.substring(0, Math.min(length, text.length()));
    }
}
