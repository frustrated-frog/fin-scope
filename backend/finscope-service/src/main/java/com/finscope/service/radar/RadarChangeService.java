package com.finscope.service.radar;

import com.finscope.common.enums.radar.RadarChangeKind;
import com.finscope.dao.radar.RadarChangeRepository;
import com.finscope.domain.radar.RadarChangePage;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Service
public class RadarChangeService {
    private static final int PAGE_SIZE = 40;
    private static final DateTimeFormatter REPLAY_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS");
    @Resource
    private RadarChangeRepository repository;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public RadarChangePage recent(int minutes, RadarChangeKind kind, LocalDateTime asOf, int page) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = asOf == null ? now : asOf;
        if (!Set.of(30, 60, 120).contains(minutes) || page < 0 || page > 500 || page > 0 && asOf == null
                || until.isAfter(now) || until.isBefore(now.minusDays(6))) {
            throw new IllegalArgumentException("最近变化的时间范围或页码无效");
        }
        LocalDateTime since = until.minusMinutes(minutes);
        var rows = repository.recent(REPLAY_TIME.format(since), REPLAY_TIME.format(until), since.toString(), until.toString(),
                kind == null ? "ALL" : kind.name(), PAGE_SIZE + 1, page * PAGE_SIZE);
        var result = new RadarChangePage();
        result.setAsOf(until.toString());
        result.setHasMore(rows.size() > PAGE_SIZE);
        result.setItems(rows.subList(0, Math.min(rows.size(), PAGE_SIZE)));
        return result;
    }
}
