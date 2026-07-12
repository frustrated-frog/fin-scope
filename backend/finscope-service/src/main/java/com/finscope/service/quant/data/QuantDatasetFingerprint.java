package com.finscope.service.quant.data;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class QuantDatasetFingerprint {
    public String bars(List<QuantDailyBar> values) {
        return dataset(values, java.util.Collections.<QuantFundamentalSnapshot>emptyList(),
                java.util.Collections.<QuantUniverseMember>emptyList());
    }

    public String dataset(List<QuantDailyBar> bars, List<QuantFundamentalSnapshot> fundamentals,
                          List<QuantUniverseMember> universe) {
        try {
            List<QuantDailyBar> ordered = new ArrayList<QuantDailyBar>(bars);
            ordered.sort(Comparator.comparing(QuantDailyBar::getTradeDate)
                    .thenComparing(QuantDailyBar::getInstrumentCode));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (QuantDailyBar value : ordered) {
                String row = "BAR|" + value.getTradeDate() + "|" + value.getInstrumentCode() + "|"
                        + decimal(value.getOpen()) + "|" + decimal(value.getHigh()) + "|"
                        + decimal(value.getLow()) + "|" + decimal(value.getClose()) + "|"
                        + decimal(value.getAdjustedClose()) + "|" + decimal(value.getVolume()) + "|"
                        + decimal(value.getAmount()) + "|" + value.getTradeStatus() + "|"
                        + value.isSt() + "|" + value.isLimitUp() + "|" + value.isLimitDown() + "\n";
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            List<QuantFundamentalSnapshot> orderedFundamentals = new ArrayList<QuantFundamentalSnapshot>(fundamentals);
            orderedFundamentals.sort(Comparator.comparing(QuantFundamentalSnapshot::getDisclosedAt)
                    .thenComparing(QuantFundamentalSnapshot::getInstrumentCode).thenComparing(QuantFundamentalSnapshot::getReportPeriod));
            for (QuantFundamentalSnapshot value : orderedFundamentals) {
                String row = "FUND|" + value.getInstrumentCode() + "|" + value.getReportPeriod() + "|" + value.getDisclosedAt()
                        + "|" + decimal(value.getPe()) + "|" + decimal(value.getPb()) + "|" + decimal(value.getMarketCap())
                        + "|" + decimal(value.getRoe()) + "|" + decimal(value.getRevenueGrowth()) + "|"
                        + decimal(value.getProfitGrowth()) + "|" + decimal(value.getDebtRatio()) + "\n";
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            List<QuantUniverseMember> orderedUniverse = new ArrayList<QuantUniverseMember>(universe);
            orderedUniverse.sort(Comparator.comparing(QuantUniverseMember::getTradeDate).thenComparing(QuantUniverseMember::getInstrumentCode));
            for (QuantUniverseMember value : orderedUniverse) {
                String row = "UNIVERSE|" + value.getTradeDate() + "|" + value.getInstrumentCode() + "|"
                        + value.isMember() + "|" + value.getSourceKind() + "\n";
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
