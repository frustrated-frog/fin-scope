package com.finscope.service.investmentobservation;

import com.finscope.domain.investmentobservation.ReactionRefreshResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class ReactionRefreshScheduler {
    @Resource
    private ReactionRefreshService refreshService;

    @Scheduled(cron = "${finscope.investment-reaction.refresh-cron:0 */20 16-23 * * MON-FRI}", zone = "Asia/Shanghai")
    public void refreshAfterClose() {
        try {
            ReactionRefreshResult result = refreshService.refreshPending();
            if (result.getRefreshed() > 0 || result.getFailed() > 0) {
                log.info("reaction refresh completed refreshed={} failed={}", result.getRefreshed(), result.getFailed());
            }
        } catch (Exception ex) {
            log.error("reaction scheduled refresh failed; next run will retry", ex);
        }
    }
}
