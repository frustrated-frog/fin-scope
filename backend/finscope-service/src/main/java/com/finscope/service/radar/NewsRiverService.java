package com.finscope.service.radar;

import com.finscope.dao.radar.RadarReplayRepository;
import com.finscope.domain.radar.NewsRiverOverview;
import com.finscope.domain.radar.NewsRiverPage;
import com.finscope.domain.radar.RadarReplayFrame;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class NewsRiverService {
    @Resource
    private RadarReplayRepository repository;
    private Clock clock = Clock.systemDefaultZone();

    public NewsRiverOverview overview(LocalDate requested) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = requested == null ? today : requested;
        validateDate(date);
        NewsRiverOverview result = new NewsRiverOverview();
        result.setDate(date);
        result.setEarliestDate(today.minusDays(6));
        result.setFrames(repository.frames(date));
        result.setBuckets(repository.buckets(date));
        return result;
    }

    public NewsRiverPage frame(long id, String category, LocalDateTime bucket, int page) {
        if (id <= 0 || page < 0 || page > 400 || category == null
                || !Set.of("ALL", "FINANCE", "TECHNOLOGY", "POLITICS", "OTHER").contains(category)) {
            throw new IllegalArgumentException("资讯回放筛选参数无效");
        }
        RadarReplayFrame frame = repository.frame(id).orElseThrow(() -> new IllegalArgumentException("该时刻的榜单已过期或尚未保存"));
        validateDate(frame.getPublishedAt().toLocalDate());
        NewsRiverPage result = new NewsRiverPage();
        result.setFrame(frame);
        result.setPage(page);
        result.setBucket(bucket);
        if (bucket == null) {
            if (page != 0) {
                throw new IllegalArgumentException("榜单回放仅包含一页");
            }
            result.setItems(repository.ranking(id, category));
            result.setTotal(result.getItems().size());
        } else {
            if (!bucket.toLocalDate().equals(frame.getPublishedAt().toLocalDate()) || bucket.isAfter(frame.getPublishedAt())
                    || bucket.getSecond() != 0 || bucket.getNano() != 0 || bucket.getMinute() % 30 != 0) {
                throw new IllegalArgumentException("请选择已记录时段的半小时起点");
            }
            LocalDateTime end = bucket.plusMinutes(30).minusNanos(1);
            LocalDateTime until = end.isBefore(frame.getPublishedAt()) ? end : frame.getPublishedAt();
            result.setItems(repository.observed(bucket, until, category, page));
            result.setTotal(repository.observedCount(bucket, until, category));
        }
        return result;
    }

    private void validateDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today.minusDays(6)) || date.isAfter(today)) {
            throw new IllegalArgumentException("请选择最近七天内的日期");
        }
    }
}
