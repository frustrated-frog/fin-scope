package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.SectorRotationStage;
import com.finscope.domain.marketpulse.SectorRotationItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SectorRotationScoringService {
    public static final String RULE_VERSION = "SECTOR_ROTATION_V1";

    public List<SectorRotationItem> score(List<SectorRotationItem> items) {
        List<SectorRotationItem> values = new ArrayList<>();
        for (SectorRotationItem source : items) {
            SectorRotationItem value = copy(source);
            evaluate(value);
            values.add(value);
        }
        values.sort(Comparator.comparingInt(SectorRotationItem::getRotationScore).reversed()
                .thenComparing(SectorRotationItem::getSectorCode));
        return values;
    }

    private void evaluate(SectorRotationItem value) {
        if (!finite(value.getReturn5d())) {
            value.setRotationScore(25);
            value.setStage(SectorRotationStage.INSUFFICIENT_DATA);
            value.getExplanations().add("五日历史或行业宽度不足，暂不参与机会排序");
            return;
        }
        int score = 35;
        score += bounded(value.getReturn5d() * 2D, -15, 20);
        score += bounded(value.getExcessReturn5d() == null ? 0D : value.getExcessReturn5d() * 2D, -10, 15);
        if (finite(value.getBreadthRatio())) {
            score += bounded((value.getBreadthRatio() - 0.5D) * 40D, -12, 16);
        } else {
            score -= 5;
            value.getExplanations().add("行业宽度未接入，评分已降低置信度");
        }
        score += value.getFlowRank() == null ? 0 : Math.max(0, 12 - value.getFlowRank());
        score += Math.min(10, value.getPersistenceDays() * 2);
        if (value.getPreviousFlowRank() != null && value.getFlowRank() != null
                && value.getPreviousFlowRank() > value.getFlowRank()) {
            score += Math.min(8, value.getPreviousFlowRank() - value.getFlowRank());
            value.getExplanations().add("资金排名较前次改善");
        }
        score -= Math.max(0, value.getCrowdingScore() - 65) / 2;
        value.setRotationScore(Math.max(0, Math.min(100, score)));
        value.setStage(stage(value));
        if (value.getBreadthRatio() == null) {
            value.getExplanations().add(String.format("5日收益 %.2f%%", value.getReturn5d()));
        } else {
            value.getExplanations().add(String.format("5日收益 %.2f%%，行业上涨比例 %.0f%%",
                    value.getReturn5d(), value.getBreadthRatio() * 100D));
        }
        if (value.getCrowdingScore() >= 75) {
            value.getExplanations().add("短期涨幅与广度偏高，存在拥挤和分化风险");
        }
    }

    private SectorRotationStage stage(SectorRotationItem value) {
        if (value.getReturn5d() >= 8D && value.getCrowdingScore() >= 75) {
            return SectorRotationStage.OVERHEATED;
        }
        if (value.getReturn1d() != null && value.getReturn1d() >= 1.5D
                && value.getReturn5d() >= 3D && value.getBreadthRatio() != null
                && value.getBreadthRatio() >= 0.6D) {
            return SectorRotationStage.ACCELERATING;
        }
        if (value.getPersistenceDays() >= 3 && value.getReturn5d() > 1D) {
            return SectorRotationStage.PERSISTENT;
        }
        if (value.getReturn1d() != null && value.getReturn1d() > 0D && value.getReturn5d() < 0D) {
            return SectorRotationStage.REVERSING;
        }
        if (value.getReturn1d() != null && value.getReturn1d() < 0D && value.getReturn5d() > 1D) {
            return SectorRotationStage.FADING;
        }
        if (value.getReturn5d() <= -2D || value.getRotationScore() < 35) {
            return SectorRotationStage.WEAK;
        }
        return SectorRotationStage.EMERGING;
    }

    private SectorRotationItem copy(SectorRotationItem source) {
        SectorRotationItem value = new SectorRotationItem();
        value.setSectorCode(source.getSectorCode());
        value.setSectorName(source.getSectorName());
        value.setReturn1d(source.getReturn1d());
        value.setReturn5d(source.getReturn5d());
        value.setReturn20d(source.getReturn20d());
        value.setExcessReturn5d(source.getExcessReturn5d());
        value.setMainNetInflow(source.getMainNetInflow());
        value.setFlowRank(source.getFlowRank());
        value.setPreviousFlowRank(source.getPreviousFlowRank());
        value.setBreadthRatio(source.getBreadthRatio());
        value.setPersistenceDays(source.getPersistenceDays());
        value.setCrowdingScore(source.getCrowdingScore());
        value.setRotationTrail(new ArrayList<>(source.getRotationTrail()));
        return value;
    }

    private int bounded(double value, int minimum, int maximum) {
        return (int) Math.round(Math.max(minimum, Math.min(maximum, value)));
    }

    private boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }
}
