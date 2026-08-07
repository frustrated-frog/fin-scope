package com.finscope.service.quant.data;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantDataSyncRunRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataSyncRun;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import com.finscope.common.exception.BizErrorCode;

@Service
public class QuantMarketDataSyncService {
    private static final int FETCH_LIMIT = 1000;

    private final QuantDatasetService datasets;
    private final QuantMarketDataRepository marketData;
    private final QuantDataSyncRunRepository runs;
    private final QuantDailyBarSource source;
    private final Clock clock;

    @Autowired
    public QuantMarketDataSyncService(QuantDatasetService datasets,
                                      QuantMarketDataRepository marketData,
                                      QuantDataSyncRunRepository runs,
                                      QuantDailyBarSource source) {
        this(datasets, marketData, runs, source, Clock.systemDefaultZone());
    }

    QuantMarketDataSyncService(QuantDatasetService datasets,
                               QuantMarketDataRepository marketData,
                               QuantDataSyncRunRepository runs,
                               QuantDailyBarSource source,
                               Clock clock) {
        this.datasets = datasets;
        this.marketData = marketData;
        this.runs = runs;
        this.source = source;
        this.clock = clock;
    }

    public QuantDataSyncRun sync(Long datasetId, String triggerType) {
        QuantDataset dataset = datasets.get(datasetId);
        List<QuantUniverseMember> universe = marketData.findUniverseMembers(datasetId);
        List<String> instruments = eligibleInstruments(dataset, universe);
        QuantDataSyncRun running;
        try {
            running = runs.start(datasetId, normalizeTrigger(triggerType), instruments.size(), now());
        } catch (DataIntegrityViolationException error) {
            throw new BusinessException(BizErrorCode.DATASET_SYNC_RUN_IN_PROGRESS, error);
        }

        int succeeded = 0;
        int failed = 0;
        int inserted = 0;
        int degraded = 0;
        Set<String> sources = new LinkedHashSet<String>();
        List<String> warnings = new ArrayList<String>();
        for (String instrument : instruments) {
            try {
                QuantDailyBarBatch batch = source.fetch(instrument, FETCH_LIMIT);
                sources.add(batch.getSourceCode());
                if (batch.isDegraded()) degraded++;
                for (String warning : batch.getWarnings()) addWarning(warnings, instrument + ": " + warning);
                LocalDate watermark = marketData.latestBarDate(datasetId, instrument);
                List<QuantDailyBar> fresh = batch.getBars().stream()
                        .filter(bar -> watermark == null || bar.getTradeDate().isAfter(watermark))
                        .collect(Collectors.toList());
                if (!fresh.isEmpty()) {
                    datasets.importBars(datasetId, fresh);
                    inserted += fresh.size();
                }
                succeeded++;
            } catch (RuntimeException error) {
                failed++;
                addWarning(warnings, instrument + ": " + safeMessage(error));
            }
        }
        String status = status(succeeded, failed, degraded);
        return runs.finish(running.getId(), status, succeeded, failed, inserted, degraded,
                join(sources), warnings.isEmpty() ? null : join(warnings), now());
    }

    public List<QuantDataSyncRun> runs(Long datasetId) {
        datasets.get(datasetId);
        return runs.findByDatasetId(datasetId);
    }

    private static List<String> eligibleInstruments(QuantDataset dataset,
                                                    List<QuantUniverseMember> universe) {
        if (!"REAL".equals(dataset.getDataKind()) || !"RESEARCH".equals(dataset.getDatasetLevel())
                || !"quant-dataset-v2".equals(dataset.getFingerprintVersion())) {
            throw new BusinessException(BizErrorCode.SYNC_REQUIRES_REAL_DATASET);
        }
        if (!"BUILDING".equals(dataset.getStatus())) {
            throw new BusinessException(BizErrorCode.BUILDING_DATASET_FROZEN_IMMUTABLE);
        }
        if (universe == null || universe.isEmpty()) {
            throw new BusinessException(BizErrorCode.PIT_POOL_REQUIRED);
        }
        if (universe.stream().anyMatch(value -> !"POINT_IN_TIME".equals(value.getSourceKind()))) {
            throw new BusinessException(BizErrorCode.AUTO_SYNC_NO_HISTORY_BACKFILL);
        }
        TreeSet<String> values = new TreeSet<String>();
        universe.stream().filter(QuantUniverseMember::isMember)
                .map(QuantUniverseMember::getInstrumentCode)
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(String::trim).forEach(values::add);
        if (values.isEmpty()) {
            throw new BusinessException(BizErrorCode.PIT_POOL_NO_VALID_MEMBERS);
        }
        return new ArrayList<String>(values);
    }

    private static String normalizeTrigger(String triggerType) {
        return "SCHEDULED".equals(triggerType) ? "SCHEDULED" : "MANUAL";
    }

    private static String status(int succeeded, int failed, int degraded) {
        if (succeeded == 0 && failed > 0) return "FAILED";
        if (failed > 0 || degraded > 0) return "PARTIAL";
        return "SUCCESS";
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (warnings.size() >= 20) return;
        String value = warning == null ? "unknown error" : warning.replace('\n', ' ').replace('\r', ' ').trim();
        warnings.add(value.length() <= 240 ? value : value.substring(0, 240));
    }

    private static String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static String join(Iterable<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append("; ");
            result.append(value);
        }
        return result.length() == 0 ? null : result.toString();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
