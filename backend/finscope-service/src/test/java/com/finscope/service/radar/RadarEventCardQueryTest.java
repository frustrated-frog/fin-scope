package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RadarEventCardQueryTest {
    @Test
    void ignoredHighRankDoesNotConsumeSlotsOrUnreadCounts() {
        RadarEventWorkspace.Summary ignored = new RadarEventWorkspace.Summary();
        ignored.setDisposition("IGNORED");
        RadarEvent first = new RadarEvent(); first.setId(1L);
        RadarEvent second = new RadarEvent(); second.setId(2L);
        List<ResearchRadarView.EventCard> cards = List.of(
                new ResearchRadarView.EventCard(first, null, ignored),
                new ResearchRadarView.EventCard(second));
        assertEquals(2L, RadarEventCardQuery.filter(cards, "ALL", 1).get(0).getId());
        assertEquals(1, RadarEventCardQuery.counts(cards).get("UNREAD"));
        assertEquals(1, RadarEventCardQuery.counts(cards).get("IGNORED"));
    }
}
