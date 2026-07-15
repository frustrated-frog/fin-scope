package com.finscope.service.quant.data;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantDatasetPartition;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
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

    public String capitalPartition(List<QuantCapitalFlowDaily> values) {
        try {
            List<QuantCapitalFlowDaily> ordered = new ArrayList<QuantCapitalFlowDaily>(values);
            ordered.sort(Comparator.comparing(QuantCapitalFlowDaily::getTradeDate)
                    .thenComparing(QuantCapitalFlowDaily::getInstrumentCode)
                    .thenComparing(QuantCapitalFlowDaily::getSourceFlowId));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("capital-flow-partition-v1\n".getBytes(StandardCharsets.UTF_8));
            for (QuantCapitalFlowDaily value : ordered) {
                String row = "CAPITAL|" + value.getTradeDate() + "|" + value.getInstrumentCode() + "|"
                        + value.getAvailableAt() + "|" + value.getSourceFlowId() + "|"
                        + text(value.getProviderCode()) + "|" + decimal(value.getMainNetInflow()) + "|"
                        + decimal(value.getMainFlowShare()) + "|" + decimal(value.getSuperLargeNetInflow()) + "|"
                        + decimal(value.getLargeNetInflow()) + "|" + decimal(value.getMediumNetInflow()) + "|"
                        + decimal(value.getSmallNetInflow()) + "|" + decimal(value.getTurnoverRate()) + "|"
                        + decimal(value.getAmount()) + "|" + text(value.getQualityStatus()) + "|"
                        + text(value.getSourceFingerprint()) + "|" + text(value.getCalculationVersion()) + "\n";
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            return hex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算资金分区指纹", ex);
        }
    }

    public String datasetV2(QuantDataset dataset, List<QuantDailyBar> bars,
                            List<QuantFundamentalSnapshot> fundamentals,
                            List<QuantUniverseMember> universe,
                            List<QuantDatasetPartition> partitions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String header = "quant-dataset-v2|" + text(dataset.getDatasetLevel()) + "|"
                    + text(dataset.getDataKind()) + "|" + text(dataset.getMarket()) + "|"
                    + text(dataset.getUniverseType()) + "|" + text(dataset.getSourceType()) + "|"
                    + text(dataset.getAsOfTime()) + "|base=" + dataset(bars, fundamentals, universe) + "\n";
            digest.update(header.getBytes(StandardCharsets.UTF_8));
            List<QuantDatasetPartition> ordered = new ArrayList<QuantDatasetPartition>(partitions);
            ordered.sort(Comparator.comparing(QuantDatasetPartition::getPartitionType)
                    .thenComparing(value -> text(value.getMinDate()))
                    .thenComparing(value -> text(value.getMaxDate()))
                    .thenComparing(QuantDatasetPartition::getPartitionFingerprint));
            for (QuantDatasetPartition value : ordered) {
                String row = "PARTITION|" + text(value.getPartitionType()) + "|" + value.getRowCount() + "|"
                        + text(value.getMinDate()) + "|" + text(value.getMaxDate()) + "|"
                        + text(value.getPartitionFingerprint()) + "|" + text(value.getQualityStatus()) + "\n";
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            return hex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算研究数据集指纹", ex);
        }
    }

    private String decimal(java.math.BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }
}
