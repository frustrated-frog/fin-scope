package com.finscope.service.marketintel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 统一清洗资金行情链路中的原子告警，兼容已持久化的旧式拼接告警。 */
final class MarketIntelWarnings {
    private MarketIntelWarnings() {}

    static List<String> normalize(Iterable<String> warnings) {
        Set<String> result = new LinkedHashSet<String>();
        if (warnings != null) {
            for (String warning : warnings) add(result, warning);
        }
        return Collections.unmodifiableList(new ArrayList<String>(result));
    }

    static List<String> merge(Iterable<String> warnings, String additionalWarning) {
        Set<String> result = new LinkedHashSet<String>(normalize(warnings));
        add(result, additionalWarning);
        return Collections.unmodifiableList(new ArrayList<String>(result));
    }

    private static void add(Set<String> destination, String warning) {
        if (warning == null) return;
        for (String item : warning.split("[；;]")) {
            String normalized = item.trim();
            if (!normalized.isEmpty()) destination.add(normalized);
        }
    }
}
