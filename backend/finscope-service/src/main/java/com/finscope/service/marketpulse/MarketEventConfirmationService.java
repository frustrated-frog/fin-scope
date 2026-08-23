package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketEventConfirmationState;
import com.finscope.domain.marketpulse.MarketEventConfirmation;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class MarketEventConfirmationService {
    private static final int STRONG_EVENT_SCORE = 70;
    private static final int STRONG_MARKET_SCORE = 60;

    public List<MarketEventConfirmation> confirm(List<RadarEvent> events, List<SectorRotationItem> sectors) {
        List<MarketEventConfirmation> values = new ArrayList<>();
        for (RadarEvent event : events) {
            String text = normalized(event.getCanonicalTitle()) + " " + normalized(event.getSummary())
                    + " " + normalized(event.getEvidenceSummary());
            for (SectorRotationItem sector : sectors) {
                String name = normalized(sector.getSectorName());
                if (name.isEmpty() || !text.contains(name)) {
                    continue;
                }
                values.add(confirmation(event, sector));
            }
        }
        values.sort(Comparator.comparingInt(MarketEventConfirmation::getEventScore).reversed()
                .thenComparingInt(MarketEventConfirmation::getMarketReactionScore).reversed()
                .thenComparing(MarketEventConfirmation::getSectorCode));
        return values;
    }

    private MarketEventConfirmation confirmation(RadarEvent event, SectorRotationItem sector) {
        MarketEventConfirmation value = new MarketEventConfirmation();
        value.setRadarEventId(event.getId());
        value.setTitle(event.getCanonicalTitle());
        value.setSectorCode(sector.getSectorCode());
        value.setSectorName(sector.getSectorName());
        value.setMappingSource("DIRECT_MENTION");
        value.setMappingConfidence(90);
        value.setEligibleForRanking(true);
        value.setEventScore(Math.max(event.getHotspotScore(), event.getConfidenceScore()));
        value.setMarketReactionScore(reactionScore(sector));
        value.setConfirmationState(state(value.getEventScore(), value.getMarketReactionScore()));
        value.getEvidence().add("事件标题或事实摘要直接提及行业名称");
        value.getEvidence().add("行业轮动得分 " + sector.getRotationScore());
        if (sector.getReturn1d() != null) {
            value.getEvidence().add(String.format(Locale.ROOT, "行业当日涨跌 %.2f%%", sector.getReturn1d()));
        }
        return value;
    }

    private int reactionScore(SectorRotationItem sector) {
        int score = sector.getRotationScore();
        if (sector.getReturn1d() != null && sector.getReturn1d() >= 0.5D) {
            score += 10;
        }
        if (sector.getReturn1d() != null && sector.getReturn1d() < 0D) {
            score -= 15;
        }
        return Math.max(0, Math.min(100, score));
    }

    private MarketEventConfirmationState state(int eventScore, int marketScore) {
        boolean eventStrong = eventScore >= STRONG_EVENT_SCORE;
        boolean marketStrong = marketScore >= STRONG_MARKET_SCORE;
        if (eventStrong && marketStrong) {
            return MarketEventConfirmationState.CONFIRMED;
        }
        if (eventStrong) {
            return MarketEventConfirmationState.UNCONFIRMED;
        }
        if (marketStrong) {
            return MarketEventConfirmationState.MARKET_LEADING;
        }
        return MarketEventConfirmationState.QUIET;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
