package com.finscope.service.factorresearch;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.factorresearch.QuantCapitalFlowRepository;
import com.finscope.dao.factorresearch.QuantDatasetPartitionRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.InstrumentCodeCanonicalizer;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantDatasetPartition;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.service.quant.data.QuantDatasetFingerprint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Freezes point-in-time capital-flow evidence into a versioned research dataset.
 * Source retrieval time is the only availability timestamp used by this bridge.
 */
@Service
public class CapitalFlowFreezeService {
    private static final String PARTITION_TYPE = "CAPITAL_FLOW_DAILY";
    private static final String FINGERPRINT_VERSION = "quant-dataset-v2";

    @Resource private QuantDatasetRepository datasets;
    @Resource private CapitalFlowRepository sourceFlows;
    @Resource private InstrumentRepository instruments;
    @Resource private QuantMarketDataRepository marketData;
    @Resource private QuantCapitalFlowRepository capitalFlows;
    @Resource private QuantDatasetPartitionRepository partitions;
    @Resource private QuantDatasetFingerprint fingerprint;

    @Transactional
    public QuantDataset freeze(Long datasetId, LocalDate from, LocalDate to, LocalDateTime asOfTime) {
        validateRequest(datasetId, from, to, asOfTime);
        QuantDataset dataset = datasets.findById(datasetId).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "量化数据集不存在"));
        validateDataset(dataset);

        List<QuantUniverseMember> universe = marketData.findUniverseMembers(datasetId);
        List<QuantDailyBar> bars = marketData.findBars(datasetId);
        List<QuantFundamentalSnapshot> fundamentals = marketData.findFundamentals(datasetId);
        Map<Long, String> codes = instrumentCodes();
        Set<String> issues = new LinkedHashSet<String>();

        Set<String> universeCodes = new LinkedHashSet<String>();
        for (QuantUniverseMember event : universe) universeCodes.add(event.getInstrumentCode());
        Set<Long> targetInstrumentIds = new LinkedHashSet<Long>();
        for (Map.Entry<Long, String> entry : codes.entrySet()) {
            if (universeCodes.contains(entry.getValue())) targetInstrumentIds.add(entry.getKey());
        }
        if (targetInstrumentIds.size() < universeCodes.size()) issues.add("unmappedUniverseInstrument");
        List<CapitalFlowPoint> source = sourceFlows.findDailyPointInTime(
                from, to, asOfTime, targetInstrumentIds);

        List<QuantUniverseMember> orderedUniverse = new ArrayList<QuantUniverseMember>(universe);
        orderedUniverse.sort(Comparator.comparing(QuantUniverseMember::getTradeDate)
                .thenComparing(QuantUniverseMember::getInstrumentCode));
        if (orderedUniverse.stream().anyMatch(value -> !"POINT_IN_TIME".equals(value.getSourceKind()))) {
            issues.add("nonPointInTimeUniverse");
        }

        Set<LocalDate> tradingDates = tradingDates(bars, from, to);
        Set<LocalDate> snapshotDates = new LinkedHashSet<LocalDate>(tradingDates);
        for (CapitalFlowPoint point : source) {
            if (point.getDataDate() != null && !point.getDataDate().isBefore(from)
                    && !point.getDataDate().isAfter(to)) snapshotDates.add(point.getDataDate());
        }
        Map<LocalDate, Set<String>> activeByDate = activeUniverseByDate(orderedUniverse, snapshotDates);
        Set<String> expected = expectedPairs(tradingDates, activeByDate);
        Set<String> availableBars = barPairs(bars, from, to);
        if (tradingDates.isEmpty()) issues.add("noTradingCalendar");
        if (!availableBars.containsAll(expected)) issues.add("missingMarketBar");
        List<QuantCapitalFlowDaily> frozen = new ArrayList<QuantCapitalFlowDaily>();
        Set<String> actual = new LinkedHashSet<String>();
        for (CapitalFlowPoint point : source) {
            String code = codes.get(point.getInstrumentId());
            Set<String> active = activeByDate.get(point.getDataDate());
            if (code == null || active == null || !tradingDates.contains(point.getDataDate())
                    || !active.contains(code)) continue;
            QuantCapitalFlowDaily row = freezeRow(datasetId, code, point, issues);
            frozen.add(row);
            actual.add(pair(point.getDataDate(), code));
        }
        if (frozen.isEmpty()) issues.add("noRows");
        if (expected.isEmpty()) issues.add("noActiveUniverse");
        if (!actual.containsAll(expected)) issues.add("missingCapitalFlow");

        frozen.sort(Comparator.comparing(QuantCapitalFlowDaily::getTradeDate)
                .thenComparing(QuantCapitalFlowDaily::getInstrumentCode));
        boolean complete = issues.isEmpty() && !frozen.isEmpty()
                && frozen.stream().allMatch(value -> "COMPLETE".equals(value.getQualityStatus()));
        String state = complete ? "READY" : "BLOCKED";
        String partitionState = complete ? "COMPLETE" : "BLOCKED";

        List<QuantDatasetPartition> manifestPartitions = new ArrayList<QuantDatasetPartition>();
        List<QuantDatasetPartition> existingPartitions = partitions.findByDatasetId(datasetId);
        if (existingPartitions != null) {
            for (QuantDatasetPartition existing : existingPartitions) {
                if (!PARTITION_TYPE.equals(existing.getPartitionType())) manifestPartitions.add(existing);
            }
        }
        capitalFlows.deleteByDatasetId(datasetId);
        partitions.deleteByDatasetIdAndType(datasetId, PARTITION_TYPE);
        if (!frozen.isEmpty()) capitalFlows.saveAll(frozen);

        String partitionFingerprint = fingerprint.capitalPartition(frozen);
        QuantDatasetPartition capitalPartition = partition(datasetId, frozen, partitionFingerprint,
                partitionState, asOfTime);
        partitions.save(capitalPartition);

        dataset.setAsOfTime(asOfTime);
        dataset.setFingerprintVersion(FINGERPRINT_VERSION);
        manifestPartitions.add(capitalPartition);
        manifestPartitions.sort(Comparator.comparing(QuantDatasetPartition::getPartitionType));
        String manifest = manifest(manifestPartitions);
        String datasetFingerprint = fingerprint.datasetV2(dataset, bars, fundamentals, universe, manifestPartitions);
        String summary = qualitySummary(frozen.size(), expected.size(), issues);
        if (!datasets.updateResearchState(datasetId, from, to, state, asOfTime,
                FINGERPRINT_VERSION, manifest, datasetFingerprint, summary, dataset.getRevision())) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT, "数据集已被更新，请刷新后重试");
        }
        return datasets.findById(datasetId).orElse(dataset);
    }

    private void validateRequest(Long datasetId, LocalDate from, LocalDate to, LocalDateTime asOfTime) {
        if (datasetId == null || from == null || to == null || asOfTime == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "冻结请求缺少数据集、日期范围或信息截止时间");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "冻结开始日期不能晚于结束日期");
        }
        if (asOfTime.isBefore(to.atStartOfDay())) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "信息截止时间不能早于研究区间结束日");
        }
    }

    private void validateDataset(QuantDataset dataset) {
        if ("READY".equals(dataset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "已就绪数据集不可原地修改，请创建新版本");
        }
        if (!"REAL".equals(dataset.getDataKind()) || !"RESEARCH".equals(dataset.getDatasetLevel())
                || !FINGERPRINT_VERSION.equals(dataset.getFingerprintVersion())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "仅专业研究数据集 v2 支持资金行为冻结");
        }
        if (!"BUILDING".equals(dataset.getStatus()) && !"QUALITY_PENDING".equals(dataset.getStatus())
                && !"BLOCKED".equals(dataset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "当前数据集状态不允许冻结资金行为");
        }
    }

    private Map<Long, String> instrumentCodes() {
        Map<Long, String> result = new LinkedHashMap<Long, String>();
        for (Instrument instrument : instruments.findAll()) {
            if (instrument.getId() == null) continue;
            try {
                result.put(instrument.getId(), InstrumentCodeCanonicalizer.canonical(
                        instrument.getCode(), instrument.getMarket()));
            } catch (IllegalArgumentException ignored) {
                // Unsupported instruments remain an explicit gap during row mapping.
            }
        }
        return result;
    }

    private Set<LocalDate> tradingDates(List<QuantDailyBar> bars, LocalDate from, LocalDate to) {
        Set<LocalDate> result = new LinkedHashSet<LocalDate>();
        for (QuantDailyBar bar : bars) {
            if (!bar.getTradeDate().isBefore(from) && !bar.getTradeDate().isAfter(to)) {
                result.add(bar.getTradeDate());
            }
        }
        return result;
    }

    private Set<String> barPairs(List<QuantDailyBar> bars, LocalDate from, LocalDate to) {
        Set<String> result = new LinkedHashSet<String>();
        for (QuantDailyBar bar : bars) {
            if (bar.getTradeDate().isBefore(from) || bar.getTradeDate().isAfter(to)) continue;
            result.add(pair(bar.getTradeDate(), bar.getInstrumentCode()));
        }
        return result;
    }

    private Map<LocalDate, Set<String>> activeUniverseByDate(List<QuantUniverseMember> universe,
                                                              Set<LocalDate> requestedDates) {
        List<LocalDate> dates = new ArrayList<LocalDate>(requestedDates);
        Collections.sort(dates);
        Map<LocalDate, Set<String>> result = new LinkedHashMap<LocalDate, Set<String>>();
        Set<String> active = new LinkedHashSet<String>();
        int cursor = 0;
        for (LocalDate date : dates) {
            while (cursor < universe.size() && !universe.get(cursor).getTradeDate().isAfter(date)) {
                QuantUniverseMember event = universe.get(cursor++);
                if (event.isMember()) active.add(event.getInstrumentCode());
                else active.remove(event.getInstrumentCode());
            }
            result.put(date, new LinkedHashSet<String>(active));
        }
        return result;
    }

    private Set<String> expectedPairs(Set<LocalDate> tradingDates,
                                      Map<LocalDate, Set<String>> activeByDate) {
        Set<String> result = new LinkedHashSet<String>();
        for (LocalDate date : tradingDates) {
            Set<String> active = activeByDate.get(date);
            if (active == null) continue;
            for (String code : active) result.add(pair(date, code));
        }
        return result;
    }

    private QuantCapitalFlowDaily freezeRow(Long datasetId, String code, CapitalFlowPoint point,
                                            Set<String> issues) {
        QuantCapitalFlowDaily value = new QuantCapitalFlowDaily();
        value.setDatasetId(datasetId);
        value.setTradeDate(point.getDataDate());
        value.setInstrumentCode(code);
        value.setAvailableAt(point.getRetrievedAt());
        value.setSourceFlowId(point.getId());
        value.setProviderCode(point.getProviderCode());
        value.setMainNetInflow(point.getMainNetInflow());
        value.setSuperLargeNetInflow(point.getSuperLargeNetInflow());
        value.setLargeNetInflow(point.getLargeNetInflow());
        value.setMediumNetInflow(point.getMediumNetInflow());
        value.setSmallNetInflow(point.getSmallNetInflow());
        value.setTurnoverRate(point.getTurnoverRate());
        value.setAmount(point.getIntervalTradeAmount());
        value.setSourceFingerprint(point.getPayloadHash());
        value.setCalculationVersion(point.getCalculationVersion());

        boolean completeSource = "COMPLETE".equals(point.getQualityStatus());
        boolean validAmount = point.getIntervalTradeAmount() != null
                && point.getIntervalTradeAmount().compareTo(BigDecimal.ZERO) > 0;
        boolean required = point.getId() != null && point.getDataDate() != null
                && point.getRetrievedAt() != null && point.getMainNetInflow() != null;
        if (validAmount && point.getMainNetInflow() != null) {
            value.setMainFlowShare(point.getMainNetInflow().divide(
                    point.getIntervalTradeAmount(), 10, RoundingMode.HALF_UP));
        }
        if (point.getRetrievedAt() != null && point.getDataDate() != null
                && point.getRetrievedAt().toLocalDate().isAfter(point.getDataDate())) {
            value.setQualityStatus("POINT_IN_TIME_BLOCKED");
            issues.add("backfilled");
        } else if (!completeSource || !validAmount || !required) {
            value.setQualityStatus("MISSING_INPUT");
            issues.add("incompleteCapitalFlow");
        } else {
            value.setQualityStatus("COMPLETE");
        }
        return value;
    }

    private QuantDatasetPartition partition(Long datasetId, List<QuantCapitalFlowDaily> rows,
                                                String partitionFingerprint, String qualityStatus,
                                                LocalDateTime createdAt) {
        QuantDatasetPartition value = new QuantDatasetPartition();
        value.setDatasetId(datasetId);
        value.setPartitionType(PARTITION_TYPE);
        value.setRowCount(rows.size());
        if (!rows.isEmpty()) {
            value.setMinDate(rows.get(0).getTradeDate());
            value.setMaxDate(rows.get(rows.size() - 1).getTradeDate());
        }
        value.setPartitionFingerprint(partitionFingerprint);
        value.setQualityStatus(qualityStatus);
        value.setCreatedAt(createdAt);
        return value;
    }

    private String manifest(List<QuantDatasetPartition> values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) result.append(',');
            QuantDatasetPartition value = values.get(i);
            result.append("{\"type\":\"").append(value.getPartitionType())
                    .append("\",\"rowCount\":").append(value.getRowCount())
                    .append(",\"minDate\":").append(json(value.getMinDate()))
                    .append(",\"maxDate\":").append(json(value.getMaxDate()))
                    .append(",\"fingerprint\":\"").append(value.getPartitionFingerprint())
                    .append("\",\"qualityStatus\":\"").append(value.getQualityStatus()).append("\"}");
        }
        return result.append(']').toString();
    }

    private String qualitySummary(int rows, int expected, Set<String> issues) {
        StringBuilder result = new StringBuilder("{\"capitalFlowRowCount\":").append(rows)
                .append(",\"expectedRowCount\":").append(expected)
                .append(",\"blockingIssues\":").append(issues.size()).append(",\"issueCodes\":[");
        int index = 0;
        for (String issue : issues) {
            if (index++ > 0) result.append(',');
            result.append('\"').append(issue).append('\"');
        }
        return result.append("]}").toString();
    }

    private String json(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String pair(LocalDate date, String code) {
        return date + "|" + code;
    }
}
