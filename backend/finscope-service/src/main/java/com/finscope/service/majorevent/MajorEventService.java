package com.finscope.service.majorevent;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.domain.majorevent.MajorEventCreateCommand;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.service.news.NewsFeedItem;
import com.finscope.service.news.NewsFeedService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDate;
import java.util.List;
import com.finscope.common.exception.BizErrorCode;

@Service
public class MajorEventService {
    @Resource
    private MajorEventRepository events;
    @Resource
    private ArticleRepository articles;
    @Resource
    private RadarRepository radar;
    @Resource
    private NewsFeedService news;

    public MajorEvent create(MajorEventCreateCommand command) {
        validateOrigin(command);
        MajorEvent event = "ARTICLE".equals(command.getOriginType()) ? articleSnapshot(command)
                : "RADAR_EVENT".equals(command.getOriginType()) ? radarSnapshot(command) : liveNewsSnapshot(command);
        if (events.findByOrigin(event.getOriginType(), event.getOriginKey()).isPresent()) {
            throw new BusinessException(BizErrorCode.MAJOR_EVENT_ALREADY_RECORDED);
        }
        event.setOccurredDate(command.getOccurredDate() == null ? event.getOccurredDate() : command.getOccurredDate());
        event.setNote(trimToNull(command.getNote()));
        return events.save(event);
    }

    public List<MajorEvent> list(String originType, String categoryCode, LocalDate from, LocalDate to) {
        return events.find(trimToNull(originType), trimToNull(categoryCode), from, to);
    }

    public MajorEvent update(Long id, LocalDate occurredDate, String note) {
        if (occurredDate == null) throw new BusinessException(BizErrorCode.EVENT_DATE_REQUIRED);
        MajorEvent event = events.findById(id).orElseThrow(() -> new ResourceNotFoundException("大事记录不存在：" + id));
        event.setOccurredDate(occurredDate);
        event.setNote(trimToNull(note));
        return events.update(event);
    }

    public void delete(Long id) {
        if (events.deleteById(id) == 0) throw new ResourceNotFoundException("大事记录不存在：" + id);
    }

    private MajorEvent articleSnapshot(MajorEventCreateCommand command) {
        Long id = parsePersistentId(command.getOriginKey(), "文章");
        Article article = articles.findById(id).orElseThrow(() -> new ResourceNotFoundException("文章不存在：" + id));
        MajorEvent event = base(command);
        event.setTitle(article.getTitle());
        event.setSummary(article.getSummary());
        event.setSourceName(article.getSourceName());
        event.setSourceUrl(article.getUrl());
        event.setCategoryCode(article.getCategory());
        event.setOccurredDate(article.getPublishedAt() == null ? LocalDate.now() : article.getPublishedAt().toLocalDate());
        return event;
    }

    private MajorEvent radarSnapshot(MajorEventCreateCommand command) {
        RadarEvent radarEvent = findRadarEvent(command.getOriginKey());
        MajorEvent event = base(command);
        event.setOriginKey(radarEvent.getEventKey());
        event.setTitle(radarEvent.getCanonicalTitle());
        event.setSummary(radarEvent.getSummary());
        event.setSourceName("研究雷达");
        event.setCategoryCode(radarEvent.getCategoryCode());
        event.setOccurredDate(radarEvent.getFirstSeenAt() == null ? LocalDate.now() : radarEvent.getFirstSeenAt().toLocalDate());
        return event;
    }

    private RadarEvent findRadarEvent(String originKey) {
        java.util.Optional<RadarEvent> byKey = radar.findEventByKey(originKey);
        if (byKey.isPresent()) {
            return byKey.get();
        }
        try {
            return radar.findEvent(Long.valueOf(originKey)).orElseThrow(() ->
                    new ResourceNotFoundException("雷达事件不存在或缓存已过期：" + originKey));
        } catch (NumberFormatException error) {
            throw new ResourceNotFoundException("雷达事件不存在或缓存已过期：" + originKey);
        }
    }

    private MajorEvent liveNewsSnapshot(MajorEventCreateCommand command) {
        NewsFeedItem item = news.load("ALL", 100).getItems().stream()
                .filter(value -> command.getOriginKey().equals(value.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "新闻不存在或缓存已过期：" + command.getOriginKey()));
        MajorEvent event = base(command);
        event.setTitle(item.getTitle());
        event.setSummary(trimToNull(item.getContent()));
        event.setSourceName(trimToNull(item.getSourceName()));
        event.setSourceUrl(trimToNull(item.getUrl()));
        event.setCategoryCode(trimToNull(item.getCategoryCode()));
        event.setOccurredDate(item.getPublishedAt() == null ? LocalDate.now() : item.getPublishedAt().toLocalDate());
        return event;
    }

    private MajorEvent base(MajorEventCreateCommand command) {
        MajorEvent event = new MajorEvent();
        event.setOriginType(command.getOriginType());
        event.setOriginKey(command.getOriginKey());
        return event;
    }

    private void validateOrigin(MajorEventCreateCommand command) {
        if (command == null || trimToNull(command.getOriginType()) == null || trimToNull(command.getOriginKey()) == null) {
            throw new BusinessException(BizErrorCode.SOURCE_TYPE_REFERENCE_REQUIRED);
        }
        String originType = command.getOriginType().trim().toUpperCase();
        if (!"NEWS_ITEM".equals(originType) && !"ARTICLE".equals(originType) && !"RADAR_EVENT".equals(originType)) {
            throw new BusinessException(BizErrorCode.MAJOR_EVENT_SOURCE_TYPE_UNSUPPORTED);
        }
        command.setOriginType(originType);
        command.setOriginKey(command.getOriginKey().trim());
    }

    private Long parsePersistentId(String value, String label) {
        try { return Long.valueOf(value); }
        catch (NumberFormatException exception) { throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, label + "标识不合法"); }
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
