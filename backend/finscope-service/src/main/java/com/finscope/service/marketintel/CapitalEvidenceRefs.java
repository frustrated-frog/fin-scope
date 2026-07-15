package com.finscope.service.marketintel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** 为展示、信号与 Agent 输入保留最新且有代表性的可追溯引用。 */
final class CapitalEvidenceRefs {
    static final int REPRESENTATIVE_LIMIT = 8;

    private CapitalEvidenceRefs() {}

    static List<String> recentDistinct(List<String> refs) {
        List<String> unique = new ArrayList<String>(new LinkedHashSet<String>(
                refs == null ? Collections.<String>emptyList() : refs));
        int from = Math.max(0, unique.size() - REPRESENTATIVE_LIMIT);
        return Collections.unmodifiableList(new ArrayList<String>(unique.subList(from, unique.size())));
    }
}
