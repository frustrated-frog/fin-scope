package com.finscope.service.attribution;

import com.finscope.common.enums.attribution.AttributionComparisonKind;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.attribution.AttributionComparisonRow;
import com.finscope.domain.attribution.AttributionPeerCandidate;
import com.finscope.domain.attribution.AttributionResearchInsights;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.rpc.quote.PythonDailyBarClient;
import com.finscope.service.marketdata.MarketDataGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/** 模型只选择可比对象；所有涨跌、相对表现均由目标日日线/板块快照计算。 */
@Service
@Slf4j
public class AttributionPeerComparisonService {
    @Resource
    private PythonDailyBarClient dailyBarClient;
    @Resource
    private MarketDataGateway marketDataGateway;
    @Resource
    private MarketPulseRepository marketPulseRepository;

    public List<SectorRotationItem> sectors(LocalDate date) {
        try {
            return marketPulseRepository.findWorkspace(date)
                    .filter(workspace -> date.equals(workspace.getBusinessDate()))
                    .map(workspace -> workspace.getSectors()).orElse(List.of());
        } catch (RuntimeException ex) {
            log.warn("归因板块快照读取失败 date={} error={}", date, ex.getClass().getSimpleName());
            return List.of();
        }
    }

    public void capture(AttributionResearchInsights result, Instrument instrument, LocalDate date,
                        List<AttributionPeerCandidate> candidates, SectorRotationItem sector) {
        if (instrument.getCode() == null || !instrument.getCode().matches("[0-9]{6}")) {
            result.getWarnings().add("同日行业与可比行情目前支持 A 股；公司业务与预期分析仍可阅读。");
            return;
        }
        var stock = row(AttributionComparisonKind.STOCK, instrument.getCode(), instrument.getName(), "报告标的", date);
        load(stock, date, false);
        result.getComparisons().add(stock);
        var benchmark = row(AttributionComparisonKind.BENCHMARK, "000300.SH", "沪深300", "宽基市场参照", date);
        load(benchmark, date, true);
        result.getComparisons().add(benchmark);
        if (sector != null) {
            var industry = row(AttributionComparisonKind.SECTOR, sector.getSectorCode(), sector.getSectorName(), "按业务关联选择的行业参照", date);
            industry.setSource("市场复盘目标日板块快照");
            industry.setChangePct(finite(sector.getReturn1d()));
            industry.setFiveSessionChangePct(finite(sector.getReturn5d()));
            result.getComparisons().add(industry);
        } else {
            result.getWarnings().add("目标日未取得匹配的板块快照；下表分别展示宽基与业务可比公司。");
        }
        List<AttributionPeerCandidate> peers = peers(candidates, instrument.getCode());
        List<Quote> identities = identities(peers);
        for (var peer : peers) {
            var comparison = row(AttributionComparisonKind.PEER, peer.getCode(), peer.getName(), peer.getReason(), date);
            // 最新报价仅用于核对证券身份，绝不借用其涨跌幅或日期作为历史行情。
            boolean verified = identities.stream().anyMatch(quote -> peer.getCode().equals(quote.getInstrumentCode())
                    && normalizedName(peer.getName()).equals(normalizedName(quote.getName())));
            if (verified) {
                load(comparison, date, false);
            } else {
                comparison.setNote("可比公司身份暂未核实，行情待补充");
            }
            result.getComparisons().add(comparison);
        }
        for (var comparison : result.getComparisons()) {
            if (stock.getChangePct() != null && comparison.getChangePct() != null && comparison != stock) {
                comparison.setStockRelativePct(stock.getChangePct() - comparison.getChangePct());
            }
        }
        result.setComparisonSummary(summary(result.getComparisons()));
    }

    private List<AttributionPeerCandidate> peers(List<AttributionPeerCandidate> candidates, String stockCode) {
        List<AttributionPeerCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(stockCode);
        for (var peer : candidates == null ? List.<AttributionPeerCandidate>of() : candidates) {
            if (peer != null && peer.getCode() != null && peer.getCode().matches("[0-9]{6}")
                    && peer.getName() != null && !peer.getName().isBlank()
                    && peer.getReason() != null && !peer.getReason().isBlank() && seen.add(peer.getCode())) {
                result.add(peer);
            }
            if (result.size() == 3) {
                break;
            }
        }
        return result;
    }

    private List<Quote> identities(List<AttributionPeerCandidate> peers) {
        if (peers.isEmpty()) {
            return List.of();
        }
        try {
            return marketDataGateway.fetchQuotes("STOCK", peers.stream().map(AttributionPeerCandidate::getCode).toList(), false).getQuotes();
        } catch (RuntimeException ex) {
            log.warn("归因可比公司身份查询失败 error={}", ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private String normalizedName(String name) {
        return name == null ? "" : name.toUpperCase(Locale.ROOT).replaceAll("\\s|\\*|ST", "");
    }

    private AttributionComparisonRow row(AttributionComparisonKind kind, String code, String name, String reason, LocalDate date) {
        var row = new AttributionComparisonRow();
        row.setKind(kind);
        row.setCode(code);
        row.setName(name);
        row.setReason(reason);
        row.setAsOfDate(date.toString());
        row.setSource("本地行情服务日线快照");
        return row;
    }

    private void load(AttributionComparisonRow row, LocalDate date, boolean benchmark) {
        try {
            List<DailyBarPoint> bars = benchmark ? dailyBarClient.fetchMarketBenchmark(250) : dailyBarClient.fetchDailyBars(row.getCode(), 250);
            TreeMap<LocalDate, DailyBarPoint> historical = new TreeMap<>();
            for (var bar : bars) {
                if (bar != null && bar.getTradeDate() != null && !bar.getTradeDate().isAfter(date)) {
                    historical.put(bar.getTradeDate(), bar);
                }
            }
            if (historical.containsKey(date)) {
                List<DailyBarPoint> ordered = new ArrayList<>(historical.values());
                row.setChangePct(dailyChange(ordered, ordered.size() - 1));
                if (ordered.size() >= 6) {
                    double compound = 1;
                    for (int index = ordered.size() - 5; index < ordered.size(); index++) {
                        Double change = dailyChange(ordered, index);
                        if (change == null) {
                            compound = Double.NaN;
                            break;
                        }
                        compound *= 1 + change / 100;
                    }
                    row.setFiveSessionChangePct(finite((compound - 1) * 100));
                }
            }
            if (row.getChangePct() == null) {
                row.setNote("目标日日线尚未取得");
            }
        } catch (RuntimeException ex) {
            row.setNote("历史行情服务暂不可用");
            log.warn("归因对照行情失败 code={} date={} error={}", row.getCode(), date, ex.getClass().getSimpleName());
        }
    }

    private Double dailyChange(List<DailyBarPoint> bars, int index) {
        var bar = bars.get(index);
        if (bar.getChangePct() != null) {
            return finite(bar.getChangePct().doubleValue());
        }
        if (index == 0 || bar.getClose() == null || bars.get(index - 1).getClose() == null || bars.get(index - 1).getClose().signum() <= 0) {
            return null;
        }
        return finite((bar.getClose().doubleValue() / bars.get(index - 1).getClose().doubleValue() - 1) * 100);
    }

    private Double finite(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }

    private String summary(List<AttributionComparisonRow> rows) {
        var stock = rows.get(0);
        if (stock.getChangePct() == null) {
            return "目标日个股行情暂未取得；业务关联和预期分析已独立保留，可先阅读下方可比对象及选择理由。";
        }
        var peers = rows.stream().filter(row -> row.getKind() == AttributionComparisonKind.PEER && row.getChangePct() != null).toList();
        if (peers.isEmpty()) {
            return "目标股当日" + signed(stock.getChangePct()) + "% 。下表展示已取得的同日行情，可比公司的经营联系见选择理由。";
        }
        long up = peers.stream().filter(row -> row.getChangePct() > 0).count();
        long weaker = peers.stream().filter(row -> row.getChangePct() < stock.getChangePct()).count();
        return "目标股当日" + signed(stock.getChangePct()) + "% ；取得行情的 " + peers.size() + " 家可比公司中，"
                + up + " 家上涨，目标股强于其中 " + weaker + " 家。结合业务关联，可进一步区分共同题材与个股表现；此样本不代表完整行业。";
    }

    private String signed(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }
}
