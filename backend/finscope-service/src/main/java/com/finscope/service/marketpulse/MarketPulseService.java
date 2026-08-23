package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.dao.quant.StockDiscoveryRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.marketpulse.MarketEventConfirmation;
import com.finscope.domain.marketpulse.MarketPulseCandidate;
import com.finscope.domain.marketpulse.MarketPulseRefreshResult;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.domain.quant.discovery.StockDiscoveryCandidate;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 编排市场状态、行业轮动、事件确认与研究候选，冻结为每日工作台快照。 */
@Service
public class MarketPulseService {
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private MarketPulseFeatureService featureService;
    @Resource
    private MarketPulseSectorService sectorService;
    @Resource
    private RadarRepository radarRepository;
    @Resource
    private MarketEventConfirmationService confirmationService;
    @Resource
    private StockDiscoveryRepository discoveryRepository;
    @Resource
    private MarketPulseCandidateService candidateService;
    @Resource
    private MarketPulseRepository repository;

    public MarketPulseRefreshResult refresh() {
        return refresh(featureService.latestBusinessDate());
    }

    public MarketPulseRefreshResult refresh(LocalDate businessDate) {
        List<SectorRotationItem> sectors = sectorService.calculate(businessDate);
        double dispersion = sectorService.dispersion(sectors);
        MarketRegimeSnapshot regime = featureService.calculate(businessDate, dispersion);
        List<RadarEvent> events = radarRepository.findEventsSince(businessDate.atStartOfDay().minusDays(2), 100);
        List<MarketEventConfirmation> confirmations = confirmationService.confirm(events, sectors);
        List<MarketPulseCandidate> candidates = candidates(sectors);

        MarketPulseWorkspace workspace = new MarketPulseWorkspace();
        workspace.setBusinessDate(businessDate);
        workspace.setRegime(regime);
        workspace.setSectors(sectors);
        workspace.setEventConfirmations(confirmations);
        workspace.setCandidates(candidates);
        workspace.setQualityStatus(regime.getQualityStatus() == null
                ? MarketPulseQualityStatus.PARTIAL : regime.getQualityStatus());
        workspace.setGeneratedAt(LocalDateTime.now(CHINA_ZONE));
        if (sectors.isEmpty()) {
            workspace.getWarnings().add("行业行情暂不可用，市场状态仅供低置信度参考");
        }
        if (candidates.isEmpty()) {
            workspace.getWarnings().add("当前没有同时通过行业轮动与模型门禁的研究候选");
        }
        repository.saveWorkspace(workspace);
        return result(workspace);
    }

    public MarketPulseWorkspace latest() {
        Optional<MarketPulseWorkspace> latest = repository.findLatestWorkspace();
        if (!latest.isPresent()) {
            return unavailable();
        }
        return hydrate(latest.get());
    }

    public MarketPulseWorkspace detail(LocalDate businessDate) {
        MarketPulseWorkspace workspace = repository.findWorkspace(businessDate)
                .orElseThrow(() -> new IllegalArgumentException("市场机会快照不存在"));
        return hydrate(workspace);
    }

    public List<LocalDate> dates(int limit) {
        return repository.findRecentDates(Math.max(1, Math.min(limit, 100)));
    }

    private List<MarketPulseCandidate> candidates(List<SectorRotationItem> sectors) {
        Optional<StockDiscoveryRun> run = discoveryRepository.findLatestSuccess();
        if (!run.isPresent()) {
            return Collections.emptyList();
        }
        List<StockDiscoveryCandidate> frozen = discoveryRepository.findCandidatesByRunId(run.get().getId());
        return candidateService.assemble(run.get(), frozen, sectors);
    }

    private MarketPulseWorkspace hydrate(MarketPulseWorkspace workspace) {
        List<MarketRegimeSnapshot> recent = new ArrayList<>();
        for (LocalDate date : repository.findRecentDates(5)) {
            repository.findWorkspace(date).map(MarketPulseWorkspace::getRegime).ifPresent(recent::add);
        }
        workspace.setRecentRegimes(recent);
        return workspace;
    }

    private MarketPulseWorkspace unavailable() {
        MarketPulseWorkspace value = new MarketPulseWorkspace();
        value.setQualityStatus(MarketPulseQualityStatus.UNAVAILABLE);
        value.getWarnings().add("尚未生成市场机会快照，请先刷新");
        return value;
    }

    private MarketPulseRefreshResult result(MarketPulseWorkspace workspace) {
        MarketPulseRefreshResult value = new MarketPulseRefreshResult();
        value.setBusinessDate(workspace.getBusinessDate());
        value.setStatus("SUCCEEDED");
        value.setQualityStatus(workspace.getQualityStatus());
        value.setSectorCount(workspace.getSectors().size());
        value.setEventConfirmationCount(workspace.getEventConfirmations().size());
        value.setCandidateCount(workspace.getCandidates().size());
        return value;
    }
}
