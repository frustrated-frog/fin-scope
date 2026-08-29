package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.SectorRotationStage;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.dao.quant.StockDiscoveryRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.marketpulse.MarketEventConfirmation;
import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketPulseCandidate;
import com.finscope.domain.marketpulse.MarketPulseBackfillResult;
import com.finscope.domain.marketpulse.MarketPulseRefreshResult;
import com.finscope.domain.marketpulse.MarketPulseHistoryPoint;
import com.finscope.domain.marketpulse.MarketPulseSectorResult;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 编排市场状态、行业轮动、事件确认与研究候选，冻结为每日工作台快照。 */
@Service
public class MarketPulseService {
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private MarketPulseFeatureService featureService;
    @Resource
    private MarketBreadthService breadthService;
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
    @Resource
    private DailyMarketReviewService reviewService;

    public MarketPulseRefreshResult refresh() {
        LocalDate businessDate = featureService.latestBusinessDate();
        MarketPulseRefreshResult value = refreshDate(businessDate, false);
        repairRecentBreadth(businessDate);
        return value;
    }

    public MarketPulseRefreshResult refresh(LocalDate businessDate) {
        LocalDate latestBusinessDate = featureService.latestBusinessDate();
        if (latestBusinessDate == null || !latestBusinessDate.equals(businessDate)) {
            throw new IllegalArgumentException("只允许刷新最新有效交易日，历史日期必须读取冻结快照");
        }
        return refreshDate(businessDate, false);
    }

    public MarketPulseBackfillResult backfill(LocalDate startDate, LocalDate endDate) {
        LocalDate latestBusinessDate = featureService.latestBusinessDate();
        validateBackfillRange(startDate, endDate, latestBusinessDate);
        MarketPulseBackfillResult value = new MarketPulseBackfillResult();
        value.setStartDate(startDate);
        value.setEndDate(endDate);
        for (LocalDate businessDate : featureService.businessDates(startDate, endDate)) {
            try {
                value.getResults().add(refreshDate(businessDate, true));
            } catch (RuntimeException error) {
                value.getFailures().put(businessDate.toString(), safe(error));
            }
        }
        if (value.getFailures().isEmpty() && !value.getResults().isEmpty()) {
            value.setStatus("SUCCEEDED");
        } else if (value.getResults().isEmpty()) {
            value.setStatus("FAILED");
        } else {
            value.setStatus("PARTIAL");
        }
        return value;
    }

    private MarketPulseRefreshResult refreshDate(LocalDate businessDate, boolean historical) {
        MarketPulseSectorResult sectorResult = historical
                ? sectorService.calculateHistoricalResult(businessDate)
                : sectorService.calculateResult(businessDate);
        List<SectorRotationItem> sectors = sectorResult.getSectors();
        MarketBreadthSnapshot breadth = breadthService.calculate(businessDate);
        double dispersion = sectorService.dispersion(sectors);
        MarketRegimeSnapshot regime = featureService.calculate(businessDate, dispersion, breadth.getAdvanceRatio());
        List<RadarEvent> events = radarRepository.findEventsBetween(
                businessDate.atStartOfDay().minusDays(2), businessDate.plusDays(1).atStartOfDay(), 100);
        List<MarketEventConfirmation> confirmations = confirmationService.confirm(events, sectors);
        List<MarketPulseCandidate> candidates = candidates(businessDate, sectors);

        MarketPulseWorkspace workspace = new MarketPulseWorkspace();
        workspace.setBusinessDate(businessDate);
        workspace.setRegime(regime);
        workspace.setBreadth(breadth);
        workspace.setSectors(sectors);
        workspace.setEventConfirmations(confirmations);
        workspace.setCandidates(candidates);
        workspace.setQualityStatus(regime.getQualityStatus() == null
                ? MarketPulseQualityStatus.PARTIAL : regime.getQualityStatus());
        if (!completeBreadth(breadth)) {
            workspace.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        }
        if (sectorResult.getQualityStatus() != MarketPulseQualityStatus.READY) {
            workspace.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        }
        workspace.setGeneratedAt(LocalDateTime.now(CHINA_ZONE));
        if (sectors.isEmpty()) {
            workspace.getWarnings().add("行业行情暂不可用，市场状态仅供低置信度参考");
        }
        workspace.getWarnings().addAll(breadth.getWarnings());
        workspace.getWarnings().addAll(sectorResult.getWarnings());
        if (candidates.isEmpty()) {
            workspace.getWarnings().add("当前没有同时通过行业轮动与模型门禁的研究候选");
        }
        workspace.setDailyReview(reviewService.generate(workspace));
        repository.saveWorkspace(workspace);
        return result(workspace);
    }

    private void validateBackfillRange(LocalDate startDate, LocalDate endDate,
                                       LocalDate latestBusinessDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("回填起止日期无效");
        }
        if (latestBusinessDate == null || endDate.isAfter(latestBusinessDate)) {
            throw new IllegalArgumentException("回填结束日期不能晚于最新有效交易日");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 9L) {
            throw new IllegalArgumentException("单次最多回填十个自然日");
        }
    }

    private String safe(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    public MarketPulseWorkspace latest() {
        LocalDate maximum = featureService.latestBusinessDate();
        Optional<MarketPulseWorkspace> latest = repository.findLatestWorkspace(maximum);
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
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        LocalDate maximumBusinessDate = featureService.latestBusinessDate();
        Set<LocalDate> tradingDates = tradingDates(maximumBusinessDate);
        List<LocalDate> values = new ArrayList<>();
        for (LocalDate date : repository.findRecentDates(100, maximumBusinessDate)) {
            if (!isEligibleTradingDate(date, tradingDates)) {
                continue;
            }
            values.add(date);
            if (values.size() >= boundedLimit) {
                break;
            }
        }
        return values;
    }

    private List<MarketPulseCandidate> candidates(LocalDate businessDate, List<SectorRotationItem> sectors) {
        Optional<StockDiscoveryRun> run = discoveryRepository.findLatestSuccessOnOrBefore(businessDate);
        if (!run.isPresent()) {
            return Collections.emptyList();
        }
        List<StockDiscoveryCandidate> frozen = discoveryRepository.findCandidatesByRunId(run.get().getId());
        return candidateService.assemble(run.get(), frozen, sectors);
    }

    private MarketPulseWorkspace hydrate(MarketPulseWorkspace workspace) {
        List<MarketRegimeSnapshot> recent = new ArrayList<>();
        List<MarketPulseHistoryPoint> history = new ArrayList<>();
        Set<LocalDate> tradingDates = tradingDates(workspace.getBusinessDate());
        List<MarketPulseWorkspace> recentWorkspaces = repository.findRecentWorkspaces(20, workspace.getBusinessDate());
        for (MarketPulseWorkspace recentWorkspace : recentWorkspaces) {
            if (!isEligibleTradingDate(recentWorkspace.getBusinessDate(), tradingDates)) {
                continue;
            }
            if (recent.size() < 5 && recentWorkspace.getRegime() != null) {
                recent.add(recentWorkspace.getRegime());
            }
            history.add(historyPoint(recentWorkspace));
        }
        workspace.setRecentRegimes(recent);
        workspace.setHistoryPoints(history);
        return workspace;
    }

    private void repairRecentBreadth(LocalDate maximumBusinessDate) {
        Set<LocalDate> tradingDates = tradingDates(maximumBusinessDate);
        for (MarketPulseWorkspace workspace : repository.findRecentWorkspaces(20, maximumBusinessDate)) {
            LocalDate businessDate = workspace.getBusinessDate();
            if (!isEligibleTradingDate(businessDate, tradingDates)) {
                continue;
            }
            if (usableBreadth(workspace.getBreadth())) {
                continue;
            }
            MarketBreadthSnapshot recovered = breadthService.calculate(businessDate);
            if (!usableBreadth(recovered)) {
                continue;
            }
            workspace.setBreadth(recovered);
            workspace.getWarnings().removeIf(this::isStaleBreadthWarning);
            for (String warning : recovered.getWarnings()) {
                if (warning != null && !workspace.getWarnings().contains(warning)) {
                    workspace.getWarnings().add(warning);
                }
            }
            workspace.setDailyReview(reviewService.generate(workspace));
            repository.saveWorkspace(workspace);
        }
    }

    private boolean isEligibleTradingDate(LocalDate date, Set<LocalDate> tradingDates) {
        if (date == null || date.getDayOfWeek().getValue() >= 6) {
            return false;
        }
        return tradingDates.isEmpty() || tradingDates.contains(date);
    }

    private Set<LocalDate> tradingDates(LocalDate maximumBusinessDate) {
        if (maximumBusinessDate == null) {
            return Collections.emptySet();
        }
        try {
            return new HashSet<>(featureService.businessDates(maximumBusinessDate.minusDays(60), maximumBusinessDate));
        } catch (RuntimeException ignored) {
            return Collections.emptySet();
        }
    }

    private boolean usableBreadth(MarketBreadthSnapshot breadth) {
        return breadth != null
                && breadth.getAdvanceRatio() != null
                && breadth.getValidCount() != null
                && breadth.getValidCount() > 0
                && breadth.getTotalAmount() != null
                && breadth.getMedianChangePct() != null;
    }

    private boolean isStaleBreadthWarning(String warning) {
        return warning != null && warning.startsWith("全A市场宽度不可用");
    }

    private MarketPulseHistoryPoint historyPoint(MarketPulseWorkspace workspace) {
        MarketPulseHistoryPoint value = new MarketPulseHistoryPoint();
        value.setBusinessDate(workspace.getBusinessDate());
        value.setQualityStatus(workspace.getQualityStatus());
        if (workspace.getRegime() != null) {
            value.setMarketStage(workspace.getRegime().getMarketStage());
            value.setConfidenceScore(workspace.getRegime().getConfidenceScore());
        }
        if (workspace.getBreadth() != null) {
            value.setAdvanceRatio(workspace.getBreadth().getAdvanceRatio());
            value.setTotalAmount(workspace.getBreadth().getTotalAmount());
            value.setMedianChangePct(workspace.getBreadth().getMedianChangePct());
        }
        if (workspace.getDailyReview() != null) {
            value.setHeadline(workspace.getDailyReview().getHeadline());
        }
        SectorRotationItem leader = workspace.getSectors().stream()
                .filter(item -> item.getStage() != SectorRotationStage.INSUFFICIENT_DATA)
                .filter(item -> item.getReturn5d() != null)
                .max(java.util.Comparator.comparingInt(SectorRotationItem::getRotationScore)).orElse(null);
        if (leader != null) {
            value.setLeadingSectorName(leader.getSectorName());
            value.setLeadingSectorScore(leader.getRotationScore());
        }
        return value;
    }

    private boolean completeBreadth(MarketBreadthSnapshot breadth) {
        return breadth != null && breadth.getAdvanceRatio() != null && breadth.getIndices().size() == 5
                && ("FRESH_PRIMARY".equals(breadth.getQualityStatus())
                || "FRESH_FALLBACK".equals(breadth.getQualityStatus()));
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
