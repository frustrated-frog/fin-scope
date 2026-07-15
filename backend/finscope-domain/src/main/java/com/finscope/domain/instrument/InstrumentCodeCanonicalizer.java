package com.finscope.domain.instrument;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 A 股标的统一为六位代码加市场后缀的形式。
 */
public final class InstrumentCodeCanonicalizer {
    private static final Pattern CODE_PATTERN = Pattern.compile("^([0-9]{6})(?:\\.(SH|SZ|BJ))?$");

    private InstrumentCodeCanonicalizer() {
    }

    public static String canonical(String code, String market) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("code is required");
        }
        Matcher matcher = CODE_PATTERN.matcher(code);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("unsupported instrument code: " + code);
        }

        String codeMarket = matcher.group(2);
        if (market != null && !isSupportedMarket(market)) {
            throw new IllegalArgumentException("unsupported market: " + market);
        }
        if (codeMarket != null && market != null && !codeMarket.equals(market)) {
            throw new IllegalArgumentException("instrument code market conflicts with market: " + code);
        }

        String canonicalMarket = codeMarket == null ? market : codeMarket;
        if (canonicalMarket == null) {
            throw new IllegalArgumentException("market is required for non-canonical code");
        }
        return matcher.group(1) + "." + canonicalMarket;
    }

    private static boolean isSupportedMarket(String market) {
        return "SH".equals(market) || "SZ".equals(market) || "BJ".equals(market);
    }
}
