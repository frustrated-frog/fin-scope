package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketEventConfirmationState;
import com.finscope.common.enums.marketpulse.MarketLiquidityState;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.common.enums.marketpulse.SectorRotationStage;
import com.finscope.domain.marketpulse.DailyMarketReview;
import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketEventConfirmation;
import com.finscope.domain.marketpulse.MarketIndexPerformance;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.MarketRegimeFeatures;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import com.finscope.domain.marketpulse.SectorRotationItem;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** 只根据冻结事实生成收盘复盘，不猜测未被证据支持的涨跌原因。 */
@Service
public class DailyMarketReviewService {
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    public DailyMarketReview generate(MarketPulseWorkspace workspace) {
        DailyMarketReview value = new DailyMarketReview();
        value.setBusinessDate(workspace.getBusinessDate());
        value.setHeadline(headline(workspace.getRegime()));
        value.setIndexOverview(indexOverview(workspace.getBreadth()));
        value.setBreadthConclusion(breadthConclusion(workspace.getBreadth()));
        value.setLeadingSectors(leadingSectors(workspace.getSectors()));
        value.setWeakeningSectors(weakeningSectors(workspace.getSectors()));
        value.setConfirmedEvents(confirmedEvents(workspace.getEventConfirmations()));
        value.setRiskSignals(riskSignals(workspace));
        value.setNextSessionWatchlist(watchlist(workspace, value));
        value.setEvidence(evidence(workspace));
        value.setQualityStatus(workspace.getQualityStatus());
        value.setSourceFingerprint(fingerprint(workspace));
        value.setGeneratedAt(LocalDateTime.now(CHINA_ZONE));
        return value;
    }

    private String headline(MarketRegimeSnapshot regime) {
        if (regime == null || regime.getMarketStage() == null) {
            return "关键市场事实不足，暂不形成方向性结论";
        }
        if (regime.getMarketStage() == MarketStage.POST_SELL_OFF_REPAIR
                && regime.getLiquidityState() == MarketLiquidityState.SHRINKING) {
            return "急跌后缩量修复，反弹持续性仍需量能确认";
        }
        switch (regime.getMarketStage()) {
            case RISK_ON:
                return "趋势、量能与风险偏好共振，市场处于进攻阶段";
            case HIGH_LEVEL_DIVERGENCE:
                return "指数高位分歧加大，强势方向开始接受兑现检验";
            case SELL_OFF:
                return "风险集中释放，先观察抛压收敛而非急于追反弹";
            case POST_SELL_OFF_REPAIR:
                return "急跌后进入修复，市场尚未完成趋势确认";
            case RANGE_ROTATION:
                return "指数维持震荡，行业快速轮动压低追高胜率";
            default:
                return "关键市场事实不足，暂不形成方向性结论";
        }
    }

    private String indexOverview(MarketBreadthSnapshot breadth) {
        List<MarketIndexPerformance> indices = validIndices(breadth);
        if (indices.isEmpty()) {
            return "主要指数截面不可用，无法比较大盘与成长风格";
        }
        MarketIndexPerformance strongest = indices.stream()
                .max(Comparator.comparingDouble(MarketIndexPerformance::getReturn1d)).orElse(indices.get(0));
        MarketIndexPerformance weakest = indices.stream()
                .min(Comparator.comparingDouble(MarketIndexPerformance::getReturn1d)).orElse(indices.get(0));
        double spread = strongest.getReturn1d() - weakest.getReturn1d();
        String divergence = spread >= 1D ? "，指数风格分化明显" : "，主要指数表现相对同步";
        return String.format(Locale.ROOT, "%s领涨（%+.2f%%），%s相对偏弱（%+.2f%%）%s",
                strongest.getName(), strongest.getReturn1d(), weakest.getName(), weakest.getReturn1d(), divergence);
    }

    private String breadthConclusion(MarketBreadthSnapshot breadth) {
        if (breadth == null || breadth.getAdvanceRatio() == null) {
            return "全市场宽度不可用，不能用指数表现替代个股体感";
        }
        long ratio = Math.round(breadth.getAdvanceRatio() * 100D);
        String state;
        if (breadth.getAdvanceRatio() >= 0.65D) {
            state = "上涨扩散较强";
        } else if (breadth.getAdvanceRatio() <= 0.35D) {
            state = "多数个股承压";
        } else {
            state = "涨跌分布仍偏结构化";
        }
        String median = breadth.getMedianChangePct() == null
                ? "中位数不可用" : String.format(Locale.ROOT, "涨跌中位数 %+.2f%%", breadth.getMedianChangePct());
        return String.format(Locale.ROOT, "上涨比例 %d%%，%s；%s", ratio, state, median);
    }

    private List<String> leadingSectors(List<SectorRotationItem> sectors) {
        return safeSectors(sectors).stream()
                .filter(value -> value.getReturn5d() != null && value.getRotationScore() >= 55)
                .filter(value -> value.getStage() != SectorRotationStage.WEAK
                        && value.getStage() != SectorRotationStage.FADING
                        && value.getStage() != SectorRotationStage.INSUFFICIENT_DATA)
                .sorted(Comparator.comparingInt(SectorRotationItem::getRotationScore).reversed())
                .limit(3)
                .map(value -> String.format(Locale.ROOT, "%s：5日%+.2f%%，轮动分%d，阶段%s",
                        value.getSectorName(), value.getReturn5d(), value.getRotationScore(),
                        sectorStage(value.getStage())))
                .collect(Collectors.toList());
    }

    private List<String> weakeningSectors(List<SectorRotationItem> sectors) {
        return safeSectors(sectors).stream()
                .filter(value -> value.getStage() == SectorRotationStage.WEAK
                        || value.getStage() == SectorRotationStage.FADING
                        || value.getReturn5d() != null && value.getReturn5d() < 0D)
                .sorted(Comparator.comparingInt(SectorRotationItem::getRotationScore))
                .limit(3)
                .map(value -> String.format(Locale.ROOT, "%s：1日%s，5日%s，阶段%s",
                        value.getSectorName(), percent(value.getReturn1d()), percent(value.getReturn5d()),
                        sectorStage(value.getStage())))
                .collect(Collectors.toList());
    }

    private List<String> confirmedEvents(List<MarketEventConfirmation> confirmations) {
        List<String> values = new ArrayList<>();
        if (confirmations == null) {
            return values;
        }
        for (MarketEventConfirmation item : confirmations) {
            if (item.getConfirmationState() == MarketEventConfirmationState.CONFIRMED) {
                values.add(String.format(Locale.ROOT, "%s：%s（事件%d / 行情%d）",
                        item.getSectorName() == null ? "未映射行业" : item.getSectorName(),
                        item.getTitle(), item.getEventScore(), item.getMarketReactionScore()));
            }
            if (values.size() == 3) {
                break;
            }
        }
        return values;
    }

    private List<String> riskSignals(MarketPulseWorkspace workspace) {
        List<String> values = new ArrayList<>();
        MarketRegimeFeatures features = workspace.getRegime() == null ? null : workspace.getRegime().getFeatures();
        if (features != null && features.getAmountRatio5To20() != null
                && features.getAmountRatio5To20() < 0.9D) {
            values.add(String.format(Locale.ROOT, "量能偏弱：5日/20日平均成交额比仅 %.2f",
                    features.getAmountRatio5To20()));
        }
        MarketBreadthSnapshot breadth = workspace.getBreadth();
        if (features != null && features.getReturn1d() != null && features.getReturn1d() > 0D
                && breadth != null && breadth.getAdvanceRatio() != null && breadth.getAdvanceRatio() < 0.45D) {
            values.add("宽度背离：基准指数上涨，但多数个股没有同步跟随");
        }
        long overheated = safeSectors(workspace.getSectors()).stream()
                .filter(value -> value.getStage() == SectorRotationStage.OVERHEATED).count();
        if (overheated > 0) {
            values.add("存在过热行业，短期涨幅与拥挤度提高了分化风险");
        }
        if (workspace.getCandidates() == null || workspace.getCandidates().isEmpty()) {
            values.add("没有股票同时通过行业轮动与模型门禁，研究候选保持为空");
        }
        if (breadth == null || breadth.getAdvanceRatio() == null) {
            values.add("市场宽度缺失，指数结论不能代表全市场赚钱效应");
        }
        return values;
    }

    private List<String> watchlist(MarketPulseWorkspace workspace, DailyMarketReview review) {
        List<String> values = new ArrayList<>();
        values.add("两市成交额能否重新放大，并与指数方向形成同向确认");
        values.add("上涨比例与涨跌中位数能否连续改善，避免指数与个股宽度背离");
        if (!review.getLeadingSectors().isEmpty()) {
            SectorRotationItem leader = safeSectors(workspace.getSectors()).stream()
                    .max(Comparator.comparingInt(SectorRotationItem::getRotationScore)).orElse(null);
            if (leader != null) {
                values.add(leader.getSectorName() + "能否保持正向5日收益与行业扩散，而非单日冲高回落");
            }
        } else {
            values.add("是否出现首个同时获得5日收益、行业宽度和资金排名确认的新主线");
        }
        return values;
    }

    private List<String> evidence(MarketPulseWorkspace workspace) {
        List<String> values = new ArrayList<>();
        MarketRegimeFeatures features = workspace.getRegime() == null ? null : workspace.getRegime().getFeatures();
        if (features != null && features.getReturn1d() != null) {
            values.add("沪深300单日收益 " + percent(features.getReturn1d() * 100D));
        }
        MarketBreadthSnapshot breadth = workspace.getBreadth();
        if (breadth != null && breadth.getAdvanceRatio() != null) {
            values.add(String.format(Locale.ROOT, "全A上涨比例 %.0f%%，涨跌中位数 %s",
                    breadth.getAdvanceRatio() * 100D, percent(breadth.getMedianChangePct())));
        }
        if (breadth != null && breadth.getTotalAmount() != null) {
            values.add(String.format(Locale.ROOT, "全市场成交额 %.2f 万亿元",
                    breadth.getTotalAmount() / 1_000_000_000_000D));
        }
        return values;
    }

    private String fingerprint(MarketPulseWorkspace workspace) {
        StringBuilder facts = new StringBuilder();
        append(facts, workspace.getBusinessDate(), workspace.getQualityStatus());
        MarketRegimeSnapshot regime = workspace.getRegime();
        if (regime != null) {
            append(facts, regime.getSourceFingerprint(), regime.getMarketStage(), regime.getTrendState(),
                    regime.getLiquidityState(), regime.getRiskAppetiteState(), regime.getRotationState(),
                    regime.getConfidenceScore());
        }
        MarketBreadthSnapshot breadth = workspace.getBreadth();
        if (breadth != null) {
            append(facts, breadth.getQualityStatus(), breadth.getSourceCode(), breadth.getAdvanceCount(),
                    breadth.getDeclineCount(), breadth.getFlatCount(), breadth.getValidCount(),
                    breadth.getAdvanceRatio(), breadth.getTotalAmount(), breadth.getLimitUpCount(),
                    breadth.getLimitDownCount(), breadth.getMedianChangePct());
            validIndices(breadth).stream().sorted(Comparator.comparing(MarketIndexPerformance::getName))
                    .forEach(index -> append(facts, index.getName(), index.getReturn1d(), index.getReturn20d()));
        }
        safeSectors(workspace.getSectors()).stream()
                .sorted(Comparator.comparing(SectorRotationItem::getSectorCode,
                        Comparator.nullsFirst(String::compareTo)))
                .forEach(sector -> append(facts, sector.getSectorCode(), sector.getSectorName(),
                        sector.getReturn1d(), sector.getReturn5d(), sector.getReturn20d(),
                        sector.getMainNetInflow(), sector.getFlowRank(), sector.getPreviousFlowRank(),
                        sector.getBreadthRatio(), sector.getPersistenceDays(), sector.getCrowdingScore(),
                        sector.getRotationScore(), sector.getStage()));
        List<MarketEventConfirmation> events = workspace.getEventConfirmations() == null
                ? new ArrayList<>() : workspace.getEventConfirmations();
        events.stream().sorted(Comparator.comparing(MarketEventConfirmation::getRadarEventId,
                        Comparator.nullsFirst(Long::compareTo))
                        .thenComparing(MarketEventConfirmation::getSectorCode,
                                Comparator.nullsFirst(String::compareTo)))
                .forEach(event -> append(facts, event.getRadarEventId(), event.getTitle(), event.getSectorCode(),
                        event.getEventScore(), event.getMarketReactionScore(), event.getConfirmationState()));
        append(facts, workspace.getCandidates() == null ? 0 : workspace.getCandidates().size());
        return sha256(facts.toString());
    }

    private void append(StringBuilder target, Object... values) {
        for (Object value : values) {
            target.append(value == null ? "<null>" : value).append('|');
        }
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("无法计算市场复盘事实指纹", error);
        }
    }

    private List<MarketIndexPerformance> validIndices(MarketBreadthSnapshot breadth) {
        if (breadth == null || breadth.getIndices() == null) {
            return new ArrayList<>();
        }
        return breadth.getIndices().stream()
                .filter(value -> value.getName() != null && value.getReturn1d() != null
                        && Double.isFinite(value.getReturn1d()))
                .collect(Collectors.toList());
    }

    private List<SectorRotationItem> safeSectors(List<SectorRotationItem> sectors) {
        return sectors == null ? new ArrayList<>() : sectors;
    }

    private String sectorStage(SectorRotationStage stage) {
        if (stage == null) {
            return "待判断";
        }
        switch (stage) {
            case ACCELERATING:
                return "加速";
            case PERSISTENT:
                return "持续";
            case OVERHEATED:
                return "过热";
            case FADING:
                return "退潮";
            case REVERSING:
                return "反转试探";
            case WEAK:
                return "弱势";
            case EMERGING:
                return "萌芽";
            default:
                return "数据不足";
        }
    }

    private String percent(Double value) {
        return value == null ? "不可用" : String.format(Locale.ROOT, "%+.2f%%", value);
    }
}
