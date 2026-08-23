package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.MarketPulseSectorResult;
import com.finscope.domain.marketpulse.SectorHistoryItem;
import com.finscope.domain.marketpulse.SectorHistorySnapshot;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.rpc.marketpulse.SectorHistorySource;
import com.finscope.service.instrument.SectorMarketService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将同花顺全行业当日截面和指定日期历史转换为可解释的行业轮动序列。 */
@Service
public class MarketPulseSectorService {
    private static final int HISTORY_LIMIT = 20;
    private static final int PROVIDER_HISTORY_WINDOW = 60;

    @Resource
    private SectorMarketService sectorMarketService;
    @Resource
    private SectorHistorySource historySource;
    @Resource
    private MarketPulseRepository repository;
    @Resource
    private SectorRotationScoringService scoringService;

    public List<SectorRotationItem> calculate(LocalDate businessDate) {
        return calculateResult(businessDate).getSectors();
    }

    public MarketPulseSectorResult calculateResult(LocalDate businessDate) {
        List<SectorMarketEntry> current = currentEntries();
        MarketPulseSectorResult value = new MarketPulseSectorResult();
        try {
            SectorHistorySnapshot providerHistory = historySource.fetch(businessDate, PROVIDER_HISTORY_WINDOW);
            value.setSectors(fromProviderHistory(current, providerHistory, businessDate));
            value.setQualityStatus("FRESH_PRIMARY".equals(providerHistory.getQualityStatus())
                    && providerHistory.getWarnings().isEmpty()
                    ? MarketPulseQualityStatus.READY : MarketPulseQualityStatus.PARTIAL);
            value.setWarnings(new ArrayList<>(providerHistory.getWarnings()));
        } catch (RuntimeException error) {
            value.setSectors(fromWorkspaceHistory(current, businessDate));
            value.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
            value.getWarnings().add("同花顺行业历史不可用，已回退已有工作区历史：" + message(error));
        }
        return value;
    }

    public double dispersion(List<SectorRotationItem> sectors) {
        List<Double> values = new ArrayList<>();
        for (SectorRotationItem sector : sectors) {
            if (sector.getReturn1d() != null && Double.isFinite(sector.getReturn1d())) {
                values.add(sector.getReturn1d() / 100D);
            }
        }
        if (values.size() < 2) {
            return 0D;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double variance = 0D;
        for (Double value : values) {
            variance += Math.pow(value - mean, 2D);
        }
        return Math.sqrt(variance / values.size());
    }

    private List<SectorMarketEntry> currentEntries() {
        try {
            return sectorMarketService.listEntries(SectorCategory.INDUSTRY, true);
        } catch (RuntimeException error) {
            return new ArrayList<>();
        }
    }

    private List<SectorRotationItem> fromProviderHistory(List<SectorMarketEntry> current,
                                                         SectorHistorySnapshot history,
                                                         LocalDate businessDate) {
        Map<String, SectorMarketEntry> currentByCode = currentByCode(current);
        Map<String, Integer> previousFlowRanks = previousFlowRanks(businessDate);
        List<SectorRotationItem> raw = new ArrayList<>();
        for (SectorHistoryItem historyItem : history.getEntries()) {
            raw.add(providerItem(historyItem, currentByCode.get(historyItem.getSectorCode()),
                    previousFlowRanks.get(historyItem.getSectorCode())));
        }
        applyExcessReturn(raw);
        return scoringService.score(raw);
    }

    private SectorRotationItem providerItem(SectorHistoryItem history, SectorMarketEntry current,
                                            Integer previousFlowRank) {
        SectorRotationItem value = new SectorRotationItem();
        value.setSectorCode(history.getSectorCode());
        value.setSectorName(history.getSectorName());
        value.setReturn1d(history.getReturn1d());
        value.setReturn5d(history.getReturn5d());
        value.setReturn20d(history.getReturn20d());
        value.setPersistenceDays(history.getPositiveDays5() == null ? 0 : history.getPositiveDays5());
        if (current != null && sameDailyReturn(current.getChangePct(), history.getReturn1d())) {
            value.setMainNetInflow(current.getMainNetInflow());
            value.setBreadthRatio(current.getBreadthRatio());
            value.setFlowRank(current.getSourceRank());
            value.setReturn1d(current.getChangePct());
        }
        value.setPreviousFlowRank(previousFlowRank);
        value.setCrowdingScore(crowding(value));
        return value;
    }

    private boolean sameDailyReturn(Double current, Double historical) {
        return current != null && historical != null && Math.abs(current - historical) <= 0.25D;
    }

    private Map<String, Integer> previousFlowRanks(LocalDate businessDate) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (LocalDate date : repository.findRecentDates(1, businessDate.minusDays(1))) {
            MarketPulseWorkspace workspace = repository.findWorkspace(date).orElse(null);
            if (workspace == null) {
                continue;
            }
            for (SectorRotationItem previous : workspace.getSectors()) {
                values.put(previous.getSectorCode(), previous.getFlowRank());
            }
        }
        return values;
    }

    private String message(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private Map<String, SectorMarketEntry> currentByCode(List<SectorMarketEntry> current) {
        Map<String, SectorMarketEntry> values = new LinkedHashMap<>();
        for (SectorMarketEntry entry : current) {
            values.putIfAbsent(entry.getCode(), entry);
        }
        return values;
    }

    private List<SectorRotationItem> fromWorkspaceHistory(List<SectorMarketEntry> current,
                                                          LocalDate businessDate) {
        List<MarketPulseWorkspace> history = workspaceHistory(businessDate);
        List<SectorRotationItem> raw = new ArrayList<>();
        int fallbackRank = 1;
        for (SectorMarketEntry entry : current) {
            raw.add(workspaceItem(entry, fallbackRank, history));
            fallbackRank++;
        }
        applyExcessReturn(raw);
        return scoringService.score(raw);
    }

    private List<MarketPulseWorkspace> workspaceHistory(LocalDate businessDate) {
        List<MarketPulseWorkspace> values = new ArrayList<>();
        for (LocalDate date : repository.findRecentDates(HISTORY_LIMIT)) {
            if (date.isBefore(businessDate)) {
                repository.findWorkspace(date).ifPresent(values::add);
            }
        }
        return values;
    }

    private SectorRotationItem workspaceItem(SectorMarketEntry entry, int fallbackRank,
                                             List<MarketPulseWorkspace> history) {
        SectorRotationItem value = new SectorRotationItem();
        value.setSectorCode(entry.getCode());
        value.setSectorName(entry.getName());
        value.setReturn1d(entry.getChangePct());
        value.setMainNetInflow(entry.getMainNetInflow());
        value.setBreadthRatio(entry.getBreadthRatio());
        value.setFlowRank(entry.getSourceRank() == null ? fallbackRank : entry.getSourceRank());
        List<Double> returns = new ArrayList<>();
        if (entry.getChangePct() != null) {
            returns.add(entry.getChangePct());
        }
        int persistence = entry.getChangePct() != null && entry.getChangePct() > 0D ? 1 : 0;
        boolean continuePersistence = persistence == 1;
        for (MarketPulseWorkspace workspace : history) {
            SectorRotationItem previous = find(workspace.getSectors(), entry.getCode());
            if (previous == null) {
                continue;
            }
            if (value.getPreviousFlowRank() == null) {
                value.setPreviousFlowRank(previous.getFlowRank());
            }
            if (previous.getReturn1d() != null) {
                returns.add(previous.getReturn1d());
                if (continuePersistence && previous.getReturn1d() > 0D) {
                    persistence++;
                } else {
                    continuePersistence = false;
                }
            }
        }
        value.setReturn5d(compound(returns, 5));
        value.setReturn20d(compound(returns, 20));
        value.setPersistenceDays(persistence);
        value.setCrowdingScore(crowding(value));
        return value;
    }

    private SectorRotationItem find(List<SectorRotationItem> sectors, String code) {
        for (SectorRotationItem sector : sectors) {
            if (code.equals(sector.getSectorCode())) {
                return sector;
            }
        }
        return null;
    }

    private Double compound(List<Double> returns, int window) {
        if (returns.size() < window) {
            return null;
        }
        double result = 1D;
        for (int index = 0; index < window; index++) {
            result *= 1D + returns.get(index) / 100D;
        }
        return (result - 1D) * 100D;
    }

    private int crowding(SectorRotationItem value) {
        double score = value.getReturn1d() == null ? 0D : Math.max(0D, value.getReturn1d()) * 12D;
        if (value.getReturn5d() != null) {
            score += Math.max(0D, value.getReturn5d()) * 5D;
        }
        score += Math.min(20D, value.getPersistenceDays() * 4D);
        return (int) Math.round(Math.min(100D, score));
    }

    private void applyExcessReturn(List<SectorRotationItem> values) {
        double mean = values.stream().filter(value -> value.getReturn5d() != null)
                .mapToDouble(SectorRotationItem::getReturn5d).average().orElse(0D);
        for (SectorRotationItem value : values) {
            if (value.getReturn5d() != null) {
                value.setExcessReturn5d(value.getReturn5d() - mean);
            }
        }
    }
}
