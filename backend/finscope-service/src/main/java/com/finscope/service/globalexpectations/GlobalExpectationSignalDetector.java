package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只根据相邻市场快照生成可复现的异动事实，不调用模型。 */
@Component
public class GlobalExpectationSignalDetector {
    private static final int SIGNAL_THRESHOLD = 40;

    public void enrich(List<GlobalExpectationItem> current, List<GlobalExpectationItem> previous) {
        Map<String, GlobalExpectationItem> previousByMarket = indexPrevious(previous);
        Map<String, Integer> ranksByTheme = new HashMap<String, Integer>();
        boolean hasPreviousSnapshot = previous != null && !previous.isEmpty();
        for (GlobalExpectationItem item : current) {
            int rank = ranksByTheme.merge(item.getTheme(), 1, Integer::sum);
            item.setRank(rank);
            GlobalExpectationItem old = previousByMarket.get(key(item));
            evaluate(item, old, hasPreviousSnapshot);
        }
    }

    private Map<String, GlobalExpectationItem> indexPrevious(List<GlobalExpectationItem> previous) {
        Map<String, GlobalExpectationItem> indexed = new LinkedHashMap<String, GlobalExpectationItem>();
        if (previous == null) {
            return indexed;
        }
        Map<String, Integer> inferredRanks = new HashMap<String, Integer>();
        for (GlobalExpectationItem item : previous) {
            if (item.getRank() == null) {
                item.setRank(inferredRanks.merge(item.getTheme(), 1, Integer::sum));
            }
            indexed.put(key(item), item);
        }
        return indexed;
    }

    private void evaluate(GlobalExpectationItem item, GlobalExpectationItem previous, boolean hasPreviousSnapshot) {
        List<String> reasons = new ArrayList<String>();
        int score = movementScore(item, reasons);
        if (previous == null) {
            if (hasPreviousSnapshot) {
                reasons.add("新进入分类成交榜");
                score += 35;
            }
        } else {
            item.setPreviousRank(previous.getRank());
            int rankChange = previous.getRank() == null ? 0 : previous.getRank() - item.getRank();
            item.setRankChange(rankChange);
            if (rankChange >= 3) {
                reasons.add("分类成交排名快速上升");
                score += 25;
            }
            if (crossedFifty(previous.getProbability(), item.getProbability())) {
                reasons.add("突破50%分歧线");
                score += 20;
            }
            if (volumeAccelerated(previous.getVolume24h(), item.getVolume24h())) {
                reasons.add("24h成交量明显加速");
                score += 15;
            }
        }
        item.setSignalScore(Math.min(100, score));
        item.setSignalReasons(reasons);
        item.setStatus(score >= SIGNAL_THRESHOLD ? "SIGNAL" : "WATCHING");
    }

    private int movementScore(GlobalExpectationItem item, List<String> reasons) {
        int score = 0;
        if (item.getChange5m() != null && Math.abs(item.getChange5m()) >= 3D) {
            reasons.add(item.getChange5m() > 0 ? "5分钟概率快速上升" : "5分钟概率快速下降");
            score += 40;
        }
        if (item.getChange1h() != null && Math.abs(item.getChange1h()) >= 5D) {
            reasons.add(item.getChange1h() > 0 ? "1小时概率显著上升" : "1小时概率显著下降");
            score += 35;
        }
        if (item.getChange24h() != null && Math.abs(item.getChange24h()) >= 8D) {
            reasons.add(item.getChange24h() > 0 ? "24小时概率显著上升" : "24小时概率显著下降");
            score += 25;
        }
        return score;
    }

    private boolean crossedFifty(Integer previous, Integer current) {
        return previous != null && current != null
                && (previous < 50 && current >= 50 || previous >= 50 && current < 50);
    }

    private boolean volumeAccelerated(Double previous, Double current) {
        return previous != null && current != null && previous > 0D
                && current - previous >= 10000D && current >= previous * 1.3D;
    }

    private String key(GlobalExpectationItem item) {
        String identity = item.getMarketId();
        if (identity == null || identity.isBlank()) {
            identity = item.getMarketUrl();
        }
        return item.getTheme() + "|" + identity;
    }
}
