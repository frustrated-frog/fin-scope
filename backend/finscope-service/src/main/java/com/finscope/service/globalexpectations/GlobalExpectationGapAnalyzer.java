package com.finscope.service.globalexpectations;

import com.finscope.common.enums.globalexpectations.ExpectationRealityState;
import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationRadarMatch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 将预测市场活跃度与本地 Radar 新闻活跃度压缩为可解释的五态信号。 */
@Component
public class GlobalExpectationGapAnalyzer {
    private static final int ACTIVE_THRESHOLD = 40;

    public void analyze(List<GlobalExpectationEventGroup> groups) {
        for (GlobalExpectationEventGroup group : groups) {
            analyze(group);
        }
    }

    private void analyze(GlobalExpectationEventGroup group) {
        int expectationScore = value(group.getSignalScore());
        RealitySnapshot reality = reality(group.getRadarMatches());
        group.setExpectationScore(expectationScore);
        group.setRealityScore(reality.score);
        group.setNewsCount1h(reality.newsCount1h);
        group.setNewsCount24h(reality.newsCount24h);
        group.setIndependentSourceCount(reality.independentSourceCount);

        if (!"READY".equals(group.getRealityDataStatus())) {
            group.setExpectationRealityState(ExpectationRealityState.INSUFFICIENT_DATA.name());
            group.setGapReasons(List.of("Radar 现实侧数据暂不可用，暂不把缺少新闻解读为沉寂"));
            return;
        }

        boolean expectationActive = expectationScore >= ACTIVE_THRESHOLD;
        boolean realityActive = reality.score >= ACTIVE_THRESHOLD;
        ExpectationRealityState state;
        if (expectationActive && realityActive) {
            state = ExpectationRealityState.DUAL_ACCELERATING;
        } else if (expectationActive) {
            state = ExpectationRealityState.EXPECTATION_LEADING;
        } else if (realityActive) {
            state = ExpectationRealityState.REALITY_LEADING;
        } else {
            state = ExpectationRealityState.QUIET;
        }
        group.setExpectationRealityState(state.name());
        group.setGapReasons(reasons(state, expectationScore, reality));
    }

    private RealitySnapshot reality(List<GlobalExpectationRadarMatch> matches) {
        RealitySnapshot snapshot = new RealitySnapshot();
        if (matches == null) {
            return snapshot;
        }
        for (GlobalExpectationRadarMatch match : matches) {
            snapshot.newsCount1h += value(match.getNewsCount1h());
            snapshot.newsCountPrevious1h += value(match.getNewsCountPrevious1h());
            snapshot.newsCount24h += value(match.getNewsCount24h());
            snapshot.independentSourceCount = Math.max(snapshot.independentSourceCount,
                    value(match.getIndependentSourceCount()));
        }
        if (snapshot.newsCount1h >= 3) {
            snapshot.score += 35;
        }
        if (snapshot.independentSourceCount >= 2) {
            snapshot.score += 25;
        }
        if (snapshot.newsCount1h >= 3
                && snapshot.newsCount1h >= Math.max(2, snapshot.newsCountPrevious1h * 2)) {
            snapshot.score += 25;
        }
        if (snapshot.newsCount24h >= 6) {
            snapshot.score += 15;
        }
        return snapshot;
    }

    private List<String> reasons(ExpectationRealityState state, int expectationScore, RealitySnapshot reality) {
        List<String> reasons = new ArrayList<String>();
        if (state == ExpectationRealityState.EXPECTATION_LEADING) {
            reasons.add("预测市场活跃度 " + expectationScore + "，现实侧近 1 小时仅 "
                    + reality.newsCount1h + " 条相关信号");
        } else if (state == ExpectationRealityState.REALITY_LEADING) {
            reasons.add("现实侧近 1 小时出现 " + reality.newsCount1h + " 条相关信号，预测市场尚未同步升温");
        } else if (state == ExpectationRealityState.DUAL_ACCELERATING) {
            reasons.add("预测市场与现实新闻同时升温，需关注是否进入持续共振");
        } else {
            reasons.add("预测市场和现实侧均未达到活跃阈值，当前更适合继续观察");
        }
        if (reality.independentSourceCount >= 2) {
            reasons.add("相关报道来自至少 " + reality.independentSourceCount + " 个独立信源");
        }
        return reasons;
    }

    private int value(Integer source) {
        return source == null ? 0 : source;
    }

    private static class RealitySnapshot {
        private int score;
        private int newsCount1h;
        private int newsCountPrevious1h;
        private int newsCount24h;
        private int independentSourceCount;
    }
}
