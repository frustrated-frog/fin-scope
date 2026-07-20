package com.finscope.service.quant.data;

import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.domain.quant.data.QuantDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Conservative weekday refresh for mutable, research-grade real datasets only. */
@Service
public class QuantMarketDataSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(QuantMarketDataSyncScheduler.class);

    private final QuantDatasetRepository datasets;
    private final QuantMarketDataSyncService sync;

    public QuantMarketDataSyncScheduler(QuantDatasetRepository datasets,
                                        QuantMarketDataSyncService sync) {
        this.datasets = datasets;
        this.sync = sync;
    }

    @Scheduled(cron = "${finscope.quant.market-data-sync-cron:0 30 18 * * MON-FRI}",
            zone = "Asia/Shanghai")
    public void refreshBuildingDatasets() {
        for (QuantDataset dataset : datasets.findSyncEligible()) {
            try {
                sync.sync(dataset.getId(), "SCHEDULED");
            } catch (RuntimeException error) {
                log.warn("Scheduled quant market-data sync failed for dataset {}: {}",
                        dataset.getId(), safeMessage(error));
            }
        }
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null) return error.getClass().getSimpleName();
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
