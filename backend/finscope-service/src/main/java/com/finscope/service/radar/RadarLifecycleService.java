package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEventSnapshot;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/** 使用滞回阈值决定热点生命周期，避免批次间状态抖动。 */
@Service
public class RadarLifecycleService {
    public String next(RadarEventSnapshot previous, int score, int independentSources,
                       LocalDateTime lastMeaningfulChangeAt, LocalDateTime now) {
        if (previous == null) return "DISCOVERED";
        int scoreDelta = score - previous.getHotnessScore();
        int sourceDelta = independentSources - previous.getIndependentSourceCount();
        long quietMinutes = minutesSince(lastMeaningfulChangeAt, now);
        if (score < 35 && quietMinutes >= 120) return "QUIET";
        if (score >= 80 && scoreDelta >= 5 && sourceDelta >= 1
                && ("RISING".equals(previous.getLifecycleState()) || "PEAK".equals(previous.getLifecycleState()))) {
            return "PEAK";
        }
        if (scoreDelta >= 8 && sourceDelta >= 2) return "RISING";
        if (scoreDelta <= -10 || quietMinutes >= 60) return "COOLING";
        return "STABLE";
    }

    private long minutesSince(LocalDateTime at, LocalDateTime now) {
        if (at == null || now == null) return 0;
        return Math.max(0, Duration.between(at, now).toMinutes());
    }
}
