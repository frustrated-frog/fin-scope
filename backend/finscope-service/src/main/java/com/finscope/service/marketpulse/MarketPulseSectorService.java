package com.finscope.service.marketpulse;

import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.service.instrument.SectorMarketOverview;
import com.finscope.service.instrument.SectorMarketService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将行业日榜和历史快照转换为可解释的行业轮动序列。 */
@Service
public class MarketPulseSectorService {
    private static final int HISTORY_LIMIT = 20;

    @Resource
    private SectorMarketService sectorMarketService;
    @Resource
    private MarketPulseRepository repository;
    @Resource
    private SectorRotationScoringService scoringService;

    public List<SectorRotationItem> calculate(LocalDate businessDate) {
        SectorMarketOverview overview = sectorMarketService.overview(SectorCategory.INDUSTRY, 10, true);
        Map<String, SectorMarketEntry> current = new LinkedHashMap<>();
        addEntries(current, overview.getLeaders());
        addEntries(current, overview.getLaggards());
        List<MarketPulseWorkspace> history = history(businessDate);
        List<SectorRotationItem> raw = new ArrayList<>();
        int rank = 1;
        for (SectorMarketEntry entry : current.values()) {
            raw.add(item(entry, rank, history));
            rank++;
        }
        applyExcessReturn(raw);
        return scoringService.score(raw);
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

    private void addEntries(Map<String, SectorMarketEntry> target, List<SectorMarketEntry> entries) {
        for (SectorMarketEntry entry : entries) {
            target.putIfAbsent(entry.getCode(), entry);
        }
    }

    private List<MarketPulseWorkspace> history(LocalDate businessDate) {
        List<MarketPulseWorkspace> values = new ArrayList<>();
        for (LocalDate date : repository.findRecentDates(HISTORY_LIMIT)) {
            if (date.isBefore(businessDate)) {
                repository.findWorkspace(date).ifPresent(values::add);
            }
        }
        return values;
    }

    private SectorRotationItem item(SectorMarketEntry entry, int fallbackRank,
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
