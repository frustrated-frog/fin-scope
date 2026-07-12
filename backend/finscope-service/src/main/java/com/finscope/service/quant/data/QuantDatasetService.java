package com.finscope.service.quant.data;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
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
    private final QuantDatasetFingerprint defaultFingerprint = new QuantDatasetFingerprint();
    private final QuantDataQualityService defaultQuality = new QuantDataQualityService();
    private QuantDatasetFingerprint fingerprint = defaultFingerprint;
    private QuantDataQualityService quality = defaultQuality;

    public List<QuantDataset> list() { return datasets.findAll(); }

    public QuantDataset get(Long id) {
        return datasets.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "量化数据集不存在"));
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
        String digest = fingerprint.bars(all);
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
        marketData.insertFundamentals(values);
        return dataset;
    }
}
