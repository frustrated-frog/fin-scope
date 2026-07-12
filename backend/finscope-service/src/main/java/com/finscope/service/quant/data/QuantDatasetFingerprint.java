package com.finscope.service.quant.data;

import com.finscope.domain.quant.data.QuantDailyBar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class QuantDatasetFingerprint {
    public String bars(List<QuantDailyBar> values) {
        try {
            List<QuantDailyBar> ordered = new ArrayList<QuantDailyBar>(values);
            ordered.sort(Comparator.comparing(QuantDailyBar::getTradeDate)
                    .thenComparing(QuantDailyBar::getInstrumentCode));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (QuantDailyBar value : ordered) {
                String row = value.getTradeDate() + "|" + value.getInstrumentCode() + "|"
                        + decimal(value.getOpen()) + "|" + decimal(value.getHigh()) + "|"
                        + decimal(value.getLow()) + "|" + decimal(value.getClose()) + "|"
                        + decimal(value.getAdjustedClose()) + "|" + decimal(value.getVolume()) + "|"
                        + decimal(value.getAmount()) + "|" + value.getTradeStatus() + "|"
                        + value.isSt() + "|" + value.isLimitUp() + "|" + value.isLimitDown() + "\n";
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            return hex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算数据指纹", ex);
        }
    }

    private String decimal(java.math.BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }
}
