package com.finscope.service.news;

import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.cache.ViewRevisionService;
import com.finscope.service.radar.RadarHotspotRefreshService;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.service.research.material.ResearchMaterialGatewayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** 定时生产各资讯来源的本地快照，页面请求不负责外部抓取。 */
@Service
public class NewsSourceRefreshService {
    private static final Logger log = LoggerFactory.getLogger(NewsSourceRefreshService.class);
    private static final ResearchMaterialRequest REQUEST = new ResearchMaterialRequest("000001", "", 50);

    private final ResearchMaterialGateway gateway;
    private final RadarHotspotRefreshService radarRefresh;
    private final ViewRevisionService viewRevisions;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public NewsSourceRefreshService(ResearchMaterialGateway gateway,
                                    RadarHotspotRefreshService radarRefresh,
                                    ViewRevisionService viewRevisions,
                                    @Qualifier("newsRefreshExecutor") Executor executor) {
        this.gateway = gateway;
        this.radarRefresh = radarRefresh;
        this.viewRevisions = viewRevisions;
        this.executor = executor;
    }

    public boolean requestRefresh() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    refreshNow();
                } catch (RuntimeException error) {
                    log.error("资讯来源快照刷新失败", error);
                } finally {
                    running.set(false);
                }
            });
            return true;
        } catch (RuntimeException error) {
            running.set(false);
            return false;
        }
    }

    public ResearchMaterialGatewayResult refreshNow() {
        ResearchMaterialGatewayResult result = gateway.refreshNewsFlashSources(REQUEST);
        viewRevisions.invalidate("news");
        radarRefresh.requestScheduledRefresh();
        return result;
    }

    public boolean isRunning() {
        return running.get();
    }
}
