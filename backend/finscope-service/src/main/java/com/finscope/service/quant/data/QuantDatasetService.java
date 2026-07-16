package com.finscope.service.quant.data;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.dao.factorresearch.QuantCapitalFlowRepository;
import com.finscope.dao.factorresearch.QuantDatasetPartitionRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.LinkedHashMap;

import com.finscope.service.quant.factor.FactorRegistry;
import com.finscope.domain.quant.factor.FactorDefinition;

@Service
public class QuantDatasetService {

    private final QuantDatasetFingerprint defaultFingerprint = new QuantDatasetFingerprint();

    private final QuantDataQualityService defaultQuality = new QuantDataQualityService();

    @Resource
    private QuantDatasetRepository datasets;
    @Resource
    private QuantMarketDataRepository marketData;
    @Resource
    private QuantLearningDatasetFactory learningDatasetFactory;
    @Resource
    private QuantCapitalFlowRepository capitalFlows;
    @Resource
    private QuantDatasetPartitionRepository datasetPartitions;
    @Resource
    private FactorRegistry factors;
    private QuantDatasetFingerprint fingerprint = defaultFingerprint;
    private QuantDataQualityService quality = defaultQuality;

    public List<QuantDataset> list() {
        return datasets.findAll();
    }

    public QuantDataset get(Long id) {
        return datasets.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "量化数据集不存在"));
    }

    public Set<String> availableFactorCodes(Long datasetId) {
        QuantDataset dataset = get(datasetId);
        List<QuantDailyBar> bars = marketData.findBars(datasetId);
        Set<String> result = new LinkedHashSet<String>();
        if (bars.isEmpty()) return result;
        long tradingDates = bars.stream().map(QuantDailyBar::getTradeDate).distinct().count();
        for (FactorDefinition factor : factors.list())
            if (!factor.isPointInTime() && tradingDates > factor.getLookbackDays()) result.add(factor.getCode());
        List<QuantFundamentalSnapshot> fundamentals = marketData.findFundamentals(datasetId);
        List<QuantUniverseMember> universe = marketData.findUniverseMembers(datasetId);
        for (FactorDefinition factor : factors.list()) {
            if (factor.isPointInTime() && coverage(bars, fundamentals, universe, factor.getCode()) >= 0.90d)
                result.add(factor.getCode());
        }
        List<QuantCapitalFlowDaily> frozenCapital = capitalFlows == null
                ? java.util.Collections.<QuantCapitalFlowDaily>emptyList() : capitalFlows.findByDatasetId(datasetId);
        boolean completeCapitalPartition = datasetPartitions != null && datasetPartitions.findByDatasetId(datasetId).stream()
                .anyMatch(value -> "CAPITAL_FLOW_DAILY".equals(value.getPartitionType())
                        && "COMPLETE".equals(value.getQualityStatus())
                        && value.getRowCount() == frozenCapital.size() && value.getRowCount() > 0);
        boolean capitalGate = "READY".equals(dataset.getStatus())
                && "quant-dataset-v2".equals(dataset.getFingerprintVersion())
                && completeCapitalPartition && !frozenCapital.isEmpty();
        if (capitalGate) {
            if (computableCapitalRows(frozenCapital, "MAIN_FLOW_SHARE")) result.add("MAIN_FLOW_SHARE");
            if (computableCapitalRows(frozenCapital, "SUPER_LARGE_FLOW_SHARE")) result.add("SUPER_LARGE_FLOW_SHARE");
            if (computableCapitalRows(frozenCapital, "BIG_ORDER_FLOW_SHARE")) result.add("BIG_ORDER_FLOW_SHARE");
            if (computableCapitalRows(frozenCapital, "MAIN_FLOW_SHARE") && hasCapitalWindowCoverage(frozenCapital, 5)) {
                result.add("NORMALIZED_MAIN_FLOW_SUM_5D"); result.add("FLOW_PERSISTENCE_5D");
                if (tradingDates >= 6) result.add("PRICE_FLOW_DIVERGENCE_5D");
            }
            if (computableCapitalRows(frozenCapital, "MAIN_FLOW_SHARE") && hasCapitalWindowCoverage(frozenCapital, 20))
                result.add("MAIN_FLOW_SHARE_ZSCORE_20D");
        }
        return result;
    }

    private boolean computableCapitalRows(List<QuantCapitalFlowDaily> rows, String factorCode) {
        return rows.stream().allMatch(value -> {
            boolean common = "COMPLETE".equals(value.getQualityStatus())
                    && value.getAmount() != null && value.getAmount().signum() > 0
                    && value.getAvailableAt() != null;
            if (!common) return false;
            if ("MAIN_FLOW_SHARE".equals(factorCode)) return value.getMainNetInflow() != null;
            if ("SUPER_LARGE_FLOW_SHARE".equals(factorCode)) return value.getSuperLargeNetInflow() != null;
            return value.getSuperLargeNetInflow() != null && value.getLargeNetInflow() != null;
        });
    }

    private boolean hasCapitalWindowCoverage(List<QuantCapitalFlowDaily> rows, int window) {
        Map<String, Set<LocalDate>> datesByInstrument = new LinkedHashMap<String, Set<LocalDate>>();
        for (QuantCapitalFlowDaily row : rows)
            datesByInstrument.computeIfAbsent(row.getInstrumentCode(), key -> new LinkedHashSet<LocalDate>()).add(row.getTradeDate());
        return !datesByInstrument.isEmpty() && datesByInstrument.values().stream().allMatch(dates -> dates.size() >= window);
    }

    public QuantDataset create(String name, String dataKind) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "数据集名称不能为空");
        }
        if (!"REAL".equals(dataKind) && !"LEARNING_SAMPLE".equals(dataKind)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "数据性质不受支持");
        }
        QuantDataset value = new QuantDataset();
        value.setName(name.trim());
        value.setMarket("A_SHARE");
        value.setUniverseType("CUSTOM");
        value.setSourceType("LEARNING_SAMPLE".equals(dataKind) ? "BUILT_IN" : "MANUAL_IMPORT");
        value.setDataKind(dataKind);
        boolean research = "REAL".equals(dataKind);
        value.setDatasetLevel(research ? "RESEARCH" : "LEARNING");
        value.setFingerprintVersion(research ? "quant-dataset-v2" : "quant-dataset-v1");
        value.setPartitionManifest("[]");
        value.setStatus(research ? "BUILDING" : "EMPTY");
        return datasets.save(value);
    }

    /**
     * 建立一份可立即运行策略实验的确定性虚拟数据集。
     * 学习样本永远保留显式 dataKind，避免与真实行情混用。
     */
    @Transactional
    public QuantDataset createLearningSample(String name) {
        QuantDataset dataset = create(name, "LEARNING_SAMPLE");
        List<QuantDailyBar> bars = learningDatasetFactory.bars(dataset.getId());
        List<QuantFundamentalSnapshot> fundamentals = learningDatasetFactory.fundamentals(dataset.getId());
        List<QuantUniverseMember> universe = learningDatasetFactory.universe(dataset.getId(), bars);
        quality.assertValidBars(bars);
        quality.assertValidFundamentals(fundamentals);
        marketData.insertBars(bars);
        marketData.insertFundamentals(fundamentals);
        marketData.insertUniverseMembers(universe);
        LocalDate start = bars.stream().map(QuantDailyBar::getTradeDate).min(LocalDate::compareTo).orElse(null);
        LocalDate end = bars.stream().map(QuantDailyBar::getTradeDate).max(LocalDate::compareTo).orElse(null);
        String digest = fingerprint.dataset(bars, fundamentals, universe);
        String summary = "{\"barCount\":" + bars.size() + ",\"instrumentCount\":30,"
                + "\"fundamentalCount\":" + fundamentals.size() + ",\"blockingIssues\":0}";
        if (!datasets.updateSummary(dataset.getId(), start, end, "READY", digest, summary, dataset.getRevision())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "学习数据集创建期间发生并发更新");
        }
        return get(dataset.getId());
    }

    @Transactional
    public QuantDataset importBars(Long datasetId, List<QuantDailyBar> values) {
        QuantDataset dataset = get(datasetId);
        assertMutable(dataset);
        quality.assertValidBars(values);
        for (QuantDailyBar value : values) value.setDatasetId(datasetId);
        try {
            marketData.insertBars(values);
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "日行情包含已存在的记录", ex);
        }
        List<QuantDailyBar> all = marketData.findBars(datasetId);
        LocalDate start = all.stream().map(QuantDailyBar::getTradeDate).min(LocalDate::compareTo).orElse(null);
        LocalDate end = all.stream().map(QuantDailyBar::getTradeDate).max(LocalDate::compareTo).orElse(null);
        List<QuantFundamentalSnapshot> fundamentals = marketData.findFundamentals(datasetId);
        List<QuantUniverseMember> universe = marketData.findUniverseMembers(datasetId);
        String digest = fingerprint.dataset(all, fundamentals, universe);
        String status = researchStatus(dataset, all, universe);
        if (!datasets.updateSummary(datasetId, start, end, status, digest,
                qualitySummary(all, fundamentals, universe, status), dataset.getRevision())) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT, "数据集已被更新，请刷新后重试");
        }
        return get(datasetId);
    }

    @Transactional
    public QuantDataset importFundamentals(Long datasetId, List<QuantFundamentalSnapshot> values) {
        QuantDataset dataset = get(datasetId);
        assertMutable(dataset);
        quality.assertValidFundamentals(values);
        for (QuantFundamentalSnapshot value : values) value.setDatasetId(datasetId);
        try {
            marketData.insertFundamentals(values);
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "财务快照包含已存在的记录", ex);
        }
        return refreshFingerprint(dataset);
    }

    @Transactional
    public QuantDataset importUniverse(Long datasetId, List<QuantUniverseMember> values) {
        QuantDataset dataset = get(datasetId);
        assertMutable(dataset);
        if (values == null || values.isEmpty() || values.size() > 100_000) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "股票池成员不能为空且单次不能超过 100000 条");
        }
        for (QuantUniverseMember value : values) {
            if (value.getTradeDate() == null || value.getInstrumentCode() == null || value.getInstrumentCode().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "股票池成员缺少日期或标的代码");
            }
            value.setDatasetId(datasetId);
            if (value.getSourceKind() == null) value.setSourceKind("POINT_IN_TIME");
            if (!"POINT_IN_TIME".equals(value.getSourceKind()) && !"CURRENT_SNAPSHOT".equals(value.getSourceKind())) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "股票池来源只能是 POINT_IN_TIME 或 CURRENT_SNAPSHOT");
            }
        }
        try {
            marketData.insertUniverseMembers(values);
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "股票池包含已存在的记录", ex);
        }
        return refreshFingerprint(dataset);
    }

    private QuantDataset refreshFingerprint(QuantDataset dataset) {
        List<QuantDailyBar> bars = marketData.findBars(dataset.getId());
        List<QuantFundamentalSnapshot> fundamentals = marketData.findFundamentals(dataset.getId());
        List<QuantUniverseMember> universe = marketData.findUniverseMembers(dataset.getId());
        String digest = fingerprint.dataset(bars, fundamentals, universe);
        String status = researchStatus(dataset, bars, universe);
        LocalDate start = bars.stream().map(QuantDailyBar::getTradeDate).min(LocalDate::compareTo).orElse(null);
        LocalDate end = bars.stream().map(QuantDailyBar::getTradeDate).max(LocalDate::compareTo).orElse(null);
        if (!datasets.updateSummary(dataset.getId(), start, end, status,
                digest, qualitySummary(bars, fundamentals, universe, status), dataset.getRevision())) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT, "数据集已被更新，请刷新后重试");
        }
        return get(dataset.getId());
    }

    private String researchStatus(QuantDataset dataset, List<QuantDailyBar> bars, List<QuantUniverseMember> universe) {
        if ("LEARNING_SAMPLE".equals(dataset.getDataKind())) return bars.isEmpty() ? "EMPTY" : "READY";
        if ("RESEARCH".equals(dataset.getDatasetLevel())
                && "quant-dataset-v2".equals(dataset.getFingerprintVersion())) return "BUILDING";
        if (bars.isEmpty()) return "EMPTY";
        if (universe.isEmpty()) return "QUALITY_PENDING";
        if (universe.stream().anyMatch(value -> "CURRENT_SNAPSHOT".equals(value.getSourceKind()))) return "BLOCKED";
        return hasCompleteUniverseCoverage(bars, universe) ? "READY" : "BLOCKED";
    }

    private void assertMutable(QuantDataset dataset) {
        if ("READY".equals(dataset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "已就绪数据集不可原地修改，请创建新版本");
        }
    }

    private String qualitySummary(List<QuantDailyBar> bars, List<QuantFundamentalSnapshot> fundamentals,
                                  List<QuantUniverseMember> universe, String status) {
        Set<String> instruments = new LinkedHashSet<String>();
        Set<LocalDate> dates = new LinkedHashSet<LocalDate>();
        for (QuantDailyBar bar : bars) {
            instruments.add(bar.getInstrumentCode());
            dates.add(bar.getTradeDate());
        }
        int blocking = "BLOCKED".equals(status) ? 1 : 0;
        int pending = "QUALITY_PENDING".equals(status) ? 1 : 0;
        return "{\"barCount\":" + bars.size() + ",\"instrumentCount\":" + instruments.size() + ",\"tradingDateCount\":" + dates.size()
                + ",\"fundamentalCount\":" + fundamentals.size() + ",\"universeEventCount\":" + universe.size()
                + ",\"blockingIssues\":" + blocking + ",\"pendingIssues\":" + pending + "}";
    }

    private boolean supports(QuantFundamentalSnapshot value, String code) {
        if (value == null) return false;
        if ("LOG_MARKET_CAP".equals(code)) return value.getMarketCap() != null;
        if ("EP".equals(code)) return value.getPe() != null;
        if ("BP".equals(code)) return value.getPb() != null;
        if ("ROE".equals(code)) return value.getRoe() != null;
        if ("LOW_DEBT".equals(code)) return value.getDebtRatio() != null;
        if ("REVENUE_GROWTH".equals(code)) return value.getRevenueGrowth() != null;
        if ("PROFIT_GROWTH".equals(code)) return value.getProfitGrowth() != null;
        return false;
    }

    private double coverage(List<QuantDailyBar> bars, List<QuantFundamentalSnapshot> fundamentals,
                            List<QuantUniverseMember> universe, String factorCode) {
        Map<LocalDate, Set<String>> codesByDate = new LinkedHashMap<LocalDate, Set<String>>();
        for (QuantDailyBar bar : bars)
            codesByDate.computeIfAbsent(bar.getTradeDate(), key -> new LinkedHashSet<String>()).add(bar.getInstrumentCode());
        List<LocalDate> dates = new java.util.ArrayList<LocalDate>(codesByDate.keySet());
        java.util.Collections.sort(dates);
        List<QuantUniverseMember> events = new java.util.ArrayList<QuantUniverseMember>(universe);
        events.sort(java.util.Comparator.comparing(QuantUniverseMember::getTradeDate).thenComparing(QuantUniverseMember::getInstrumentCode));
        Set<String> active = new LinkedHashSet<String>();
        int cursor = 0, available = 0, total = 0;
        for (int index = 0; index < dates.size(); index++) {
            LocalDate date = dates.get(index);
            while (cursor < events.size() && !events.get(cursor).getTradeDate().isAfter(date)) {
                QuantUniverseMember event = events.get(cursor++);
                if (event.isMember()) active.add(event.getInstrumentCode());
                else active.remove(event.getInstrumentCode());
            }
            if (index < 60) continue;
            Set<String> researchCodes = events.isEmpty() ? codesByDate.get(date) : active;
            for (String code : researchCodes) {
                total++;
                if (supports(latestVisible(fundamentals, code, date), factorCode)) available++;
            }
        }
        return total == 0 ? 0 : (double) available / total;
    }

    private QuantFundamentalSnapshot latestVisible(List<QuantFundamentalSnapshot> values, String code, LocalDate date) {
        QuantFundamentalSnapshot latest = null;
        for (QuantFundamentalSnapshot value : values)
            if (code.equals(value.getInstrumentCode()) && !value.getDisclosedAt().isAfter(date)
                    && (latest == null || value.getDisclosedAt().isAfter(latest.getDisclosedAt()))) latest = value;
        return latest;
    }

    private boolean hasCompleteUniverseCoverage(List<QuantDailyBar> bars, List<QuantUniverseMember> universe) {
        Map<LocalDate, Set<String>> barsByDate = new LinkedHashMap<LocalDate, Set<String>>();
        for (QuantDailyBar bar : bars)
            barsByDate.computeIfAbsent(bar.getTradeDate(), key -> new LinkedHashSet<String>()).add(bar.getInstrumentCode());
        List<LocalDate> dates = new java.util.ArrayList<LocalDate>(barsByDate.keySet());
        java.util.Collections.sort(dates);
        List<QuantUniverseMember> events = new java.util.ArrayList<QuantUniverseMember>(universe);
        events.sort(java.util.Comparator.comparing(QuantUniverseMember::getTradeDate).thenComparing(QuantUniverseMember::getInstrumentCode));
        Set<String> active = new LinkedHashSet<String>();
        int cursor = 0;
        for (LocalDate date : dates) {
            while (cursor < events.size() && !events.get(cursor).getTradeDate().isAfter(date)) {
                QuantUniverseMember event = events.get(cursor++);
                if (event.isMember()) active.add(event.getInstrumentCode());
                else active.remove(event.getInstrumentCode());
            }
            if (active.isEmpty() || !barsByDate.get(date).containsAll(active)) return false;
        }
        return dates.size() >= 2;
    }
}
