package com.finscope.service.marketpulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.marketpulse.*;
import com.finscope.domain.marketpulse.ai.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.*;

/** 从冻结快照构建事实目录；同时保留可审计的结构基准，供模型有界调整。 */
@Service
public class MarketAiEvidenceService {
    @Resource
    private MarketPulseRepository repository;
    @Resource
    private ObjectMapper mapper;

    @Resource
    private MarketStructureScoring scoring;
    @Resource
    private MarketAiProperties properties;

    public MarketAiInput assemble(MarketPulseWorkspace workspace) {
        MarketAiInput input = new MarketAiInput();
        input.setBusinessDate(workspace.getBusinessDate());
        input.setAsOf(workspace.getGeneratedAt());
        addWorkspace(input, workspace, true, workspace.getBreadth());
        for (MarketPulseWorkspace previous : repository.findRecentWorkspaces(6, workspace.getBusinessDate())) {
            if (previous.getBusinessDate() != null && previous.getBusinessDate().isBefore(workspace.getBusinessDate())) {
                addWorkspace(input, previous, false, workspace.getBreadth());
            }
        }
        var history = repository.findRecentWorkspaces(21, workspace.getBusinessDate());
        input.setStructure(scoring.calculate(workspace, history));
        history.stream().map(MarketPulseWorkspace::getBusinessDate).filter(Objects::nonNull).filter(d -> d.isBefore(workspace.getBusinessDate()))
                .max(LocalDate::compareTo).ifPresent(d -> input.setPreviousBusinessDate(d.toString()));
        scoring.observations(input, workspace);
        input.setAdjustmentLimit(properties.getMaxScoreAdjustment());
        for (var score : input.getStructure()) {
            score.setFactIds(input.getFacts().stream().filter(f -> f.getSectorCode() == null && f.getBusinessDate().equals(input.getBusinessDate().toString()))
                    .limit(6).map(MarketAiFact::getId).toList());
        }
        try {
            byte[] bytes = mapper.writeValueAsBytes(List.of(input.getBusinessDate().toString(), input.getFacts(), input.getSectorNames(), input.getStructure(), input.getAdjustmentLimit(), input.getObservations(), input.getObservationSamples(), MarketStructureScoring.VERSION));
            input.setFingerprint(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (Exception error) {
            throw new IllegalStateException("市场事实指纹生成失败", error);
        }
        return input;
    }

    public void applyAssessment(MarketAiView view, MarketAiInput input) {
        view.setStructure(scoring.apply(input.getStructure(), view.getReport().getAdjustments()));
    }

    public void review(MarketAiView view, MarketAiInput input, MarketAiView previous) {
        view.setPreviousScenarios(scoring.review(previous, input));
    }

    private void addWorkspace(MarketAiInput input, MarketPulseWorkspace workspace, boolean current, MarketBreadthSnapshot reference) {
        LocalDate date = workspace.getBusinessDate();
        MarketBreadthSnapshot breadth = workspace.getBreadth();
        if (breadth != null && date.equals(breadth.getBusinessDate())) {
            add(input, date, null, "市场参与", "上涨比例 " + ratio(breadth.getAdvanceRatio()) + "；涨跌中位数 " + percent(breadth.getMedianChangePct())
                    + "；上涨/下跌/有效家数 " + number(breadth.getAdvanceCount()) + "/" + number(breadth.getDeclineCount()) + "/" + number(breadth.getValidCount()));
            add(input, date, null, "成交与极端涨跌", "成交额 " + amount(breadth.getTotalAmount()) + "；涨停 " + number(breadth.getLimitUpCount()) + " 家；跌停 " + number(breadth.getLimitDownCount()) + " 家");
            if (breadth.getTrendBreadth() != null) {
                var trend = breadth.getTrendBreadth();
                boolean comparable = current || reference != null && reference.getTrendBreadth() != null
                        && Objects.equals(trend.getMa20ValidCount(), reference.getTrendBreadth().getMa20ValidCount())
                        && Objects.equals(trend.getMa60ValidCount(), reference.getTrendBreadth().getMa60ValidCount());
                String coverage = "MA20有效样本 " + number(trend.getMa20ValidCount()) + "；MA60有效样本 "
                        + number(trend.getMa60ValidCount()) + "；全A有效家数 " + number(breadth.getValidCount());
                add(input, date, null, "样本趋势宽度", comparable ? "MA20上方 " + ratio(trend.getMa20Ratio())
                        + "；MA60上方 " + ratio(trend.getMa60Ratio()) + "；" + coverage + "。均线比例仅描述日K样本，不可直接视为全A。"
                        : coverage + "。与当前截面的均线分母不同，历史占比不参与跨日比较。");
            }
            if (breadth.getNewHighLow() != null) {
                var high = breadth.getNewHighLow();
                add(input, date, null, "新高与新低", "20日新高 " + number(high.getHigh20Count()) + "；20日新低 " + number(high.getLow20Count())
                        + "；60日新高 " + number(high.getHigh60Count()) + "；60日新低 " + number(high.getLow60Count())
                        + "；20日有效样本 " + number(high.getValid20Count()) + "。仅代表所覆盖样本。");
            }
            if (breadth.getVolumePressure() != null) {
                add(input, date, null, "成交方向", "上涨成交额占比 " + ratio(breadth.getVolumePressure().getAdvanceAmountRatio()));
            }
            if (breadth.getBreadthMomentum() != null) {
                add(input, date, null, "宽度动量", "McClellan " + number(breadth.getBreadthMomentum().getMcclellanOscillator())
                        + "；宽度推力 " + ratio(breadth.getBreadthMomentum().getBreadthThrustRatio()));
            }
            for (MarketIndexPerformance index : breadth.getIndices()) {
                if (date.equals(index.getBusinessDate())) {
                    add(input, date, null, "指数 · " + index.getName(), "1日 " + percent(index.getReturn1d())
                            + "；5日 " + percent(index.getReturn5d()) + "；20日 " + percent(index.getReturn20d()));
                }
            }
        }
        List<SectorRotationItem> sectors = workspace.getSectors().stream().sorted(Comparator.comparing(SectorRotationItem::getSectorCode)).toList();
        if (current) {
            add(input, date, null, "行业样本范围", "本截面可用行业 " + sectors.size() + " 个；行业结论仅适用于本截面，不能代表未覆盖行业");
        }
        Set<String> historySectors = new LinkedHashSet<>();
        if (!current) {
            sectors.stream().filter(s -> s.getReturn5d() != null)
                    .sorted(Comparator.comparing(SectorRotationItem::getReturn5d).reversed()).limit(8).forEach(s -> historySectors.add(s.getSectorCode()));
        }
        for (SectorRotationItem sector : sectors) {
            if (!current && !historySectors.contains(sector.getSectorCode())) {
                continue;
            }
            if (current) {
                input.getSectorNames().put(sector.getSectorCode(), sector.getSectorName());
            }
            add(input, date, sector.getSectorCode(), "行业 · " + sector.getSectorName(), "1日 " + percent(sector.getReturn1d())
                    + "；5日 " + percent(sector.getReturn5d()) + "；20日 " + percent(sector.getReturn20d())
                    + "；5日超额 " + percent(sector.getExcessReturn5d()) + "；上涨占比 " + ratio(sector.getBreadthRatio())
                    + "；主力净流入 " + amount(sector.getMainNetInflow()) + "；连续性 " + sector.getPersistenceDays() + " 天");
        }
        if (current) {
            workspace.getEventConfirmations().stream().limit(16).forEach(event -> add(input, date, event.getSectorCode(),
                    "快照内新闻线索", event.getTitle() + "（关联线索，不代表已证明涨跌原因）"));
        }
    }

    private void add(MarketAiInput input, LocalDate date, String sector, String label, String value) {
        if (input.getFacts().size() >= 240) {
            return;
        }
        MarketAiFact fact = new MarketAiFact();
        fact.setId("F" + (input.getFacts().size() + 1));
        fact.setBusinessDate(date.toString());
        fact.setSectorCode(sector);
        fact.setLabel(label);
        fact.setValue(value);
        input.getFacts().add(fact);
    }

    private String number(Number value) {
        return value == null || !Double.isFinite(value.doubleValue()) ? "缺失" : String.format(Locale.ROOT, "%.2f", value.doubleValue()).replaceAll("\\.00$", "");
    }
    private String percent(Double value) {
        return value == null || !Double.isFinite(value) ? "缺失" : String.format(Locale.ROOT, "%+.2f%%", value);
    }
    private String ratio(Double value) {
        return value == null || !Double.isFinite(value) ? "缺失" : String.format(Locale.ROOT, "%.2f%%", value * 100);
    }
    private String amount(Double value) {
        return value == null ? "缺失" : number(value / 100_000_000D) + "亿元";
    }
}
