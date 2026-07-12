package com.finscope.service.quant.data;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@Service
public class QuantDatasetService {
    @Resource private QuantDatasetRepository datasets;
    @Resource private QuantMarketDataRepository marketData;
    @Resource private QuantLearningDatasetFactory learningDatasetFactory;
    private final QuantDatasetFingerprint defaultFingerprint = new QuantDatasetFingerprint();
    private final QuantDataQualityService defaultQuality = new QuantDataQualityService();
    private QuantDatasetFingerprint fingerprint = defaultFingerprint;
    private QuantDataQualityService quality = defaultQuality;

    public List<QuantDataset> list() { return datasets.findAll(); }

    public QuantDataset get(Long id) {
        return datasets.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "量化数据集不存在"));
    }

    public boolean hasFundamentals(Long datasetId) {
        get(datasetId);
        return !marketData.findFundamentals(datasetId).isEmpty();
    }

    public QuantDataset create(String name, String dataKind) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据集名称不能为空");
        }
        if (!"REAL".equals(dataKind) && !"LEARNING_SAMPLE".equals(dataKind)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据性质不受支持");
        }
        QuantDataset value = new QuantDataset();
        value.setName(name.trim()); value.setMarket("A_SHARE"); value.setUniverseType("CUSTOM");
        value.setSourceType("LEARNING_SAMPLE".equals(dataKind) ? "BUILT_IN" : "MANUAL_IMPORT");
        value.setDataKind(dataKind); value.setStatus("EMPTY");
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
            throw new BusinessException(ErrorCode.CONFLICT, "学习数据集创建期间发生并发更新");
        }
        return get(dataset.getId());
    }

    @Transactional
    public QuantDataset importBars(Long datasetId, List<QuantDailyBar> values) {
        QuantDataset dataset = get(datasetId);
        quality.assertValidBars(values);
        for (QuantDailyBar value : values) value.setDatasetId(datasetId);
        try {
            marketData.insertBars(values);
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "日行情包含已存在的记录", ex);
        }
        List<QuantDailyBar> all = marketData.findBars(datasetId);
        LocalDate start = all.stream().map(QuantDailyBar::getTradeDate).min(LocalDate::compareTo).orElse(null);
        LocalDate end = all.stream().map(QuantDailyBar::getTradeDate).max(LocalDate::compareTo).orElse(null);
        String digest = fingerprint.dataset(all, marketData.findFundamentals(datasetId), marketData.findUniverseMembers(datasetId));
        if (!datasets.updateSummary(datasetId, start, end, "READY", digest,
                "{\"barCount\":" + all.size() + ",\"blockingIssues\":0}", dataset.getRevision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "数据集已被更新，请刷新后重试");
        }
        return get(datasetId);
    }

    @Transactional
    public QuantDataset importFundamentals(Long datasetId, List<QuantFundamentalSnapshot> values) {
        QuantDataset dataset = get(datasetId);
        quality.assertValidFundamentals(values);
        for (QuantFundamentalSnapshot value : values) value.setDatasetId(datasetId);
        try { marketData.insertFundamentals(values); }
        catch (DataAccessException ex) { throw new BusinessException(ErrorCode.CONFLICT, "财务快照包含已存在的记录", ex); }
        return refreshFingerprint(dataset);
    }

    @Transactional
    public QuantDataset importUniverse(Long datasetId, List<QuantUniverseMember> values) {
        QuantDataset dataset = get(datasetId);
        if (values == null || values.isEmpty() || values.size() > 100_000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "股票池成员不能为空且单次不能超过 100000 条");
        }
        for (QuantUniverseMember value : values) {
            if (value.getTradeDate() == null || value.getInstrumentCode() == null || value.getInstrumentCode().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "股票池成员缺少日期或标的代码");
            }
            value.setDatasetId(datasetId);
            if (value.getSourceKind() == null) value.setSourceKind("POINT_IN_TIME");
        }
        try { marketData.insertUniverseMembers(values); }
        catch (DataAccessException ex) { throw new BusinessException(ErrorCode.CONFLICT, "股票池包含已存在的记录", ex); }
        return refreshFingerprint(dataset);
    }

    private QuantDataset refreshFingerprint(QuantDataset dataset) {
        List<QuantDailyBar> bars = marketData.findBars(dataset.getId());
        String digest = fingerprint.dataset(bars, marketData.findFundamentals(dataset.getId()),
                marketData.findUniverseMembers(dataset.getId()));
        if (!datasets.updateSummary(dataset.getId(), dataset.getStartDate(), dataset.getEndDate(), dataset.getStatus(),
                digest, dataset.getQualitySummary(), dataset.getRevision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "数据集已被更新，请刷新后重试");
        }
        return get(dataset.getId());
    }
}
