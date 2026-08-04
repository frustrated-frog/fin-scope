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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MajorEventService {
    private final MajorEventRepository events;
    private final ArticleRepository articles;
    private final RadarRepository radar;

    public MajorEventService(MajorEventRepository events, ArticleRepository articles, RadarRepository radar) {
        this.events = events;
        this.articles = articles;
        this.radar = radar;
    }

    public MajorEvent create(MajorEventCreateCommand command) {
        validateOrigin(command);
        if (events.findByOrigin(command.getOriginType(), command.getOriginKey()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_OPERATION, "该事件已记入大事记");
        }
        MajorEvent event = "ARTICLE".equals(command.getOriginType()) ? articleSnapshot(command)
                : "RADAR_EVENT".equals(command.getOriginType()) ? radarSnapshot(command) : liveNewsSnapshot(command);
        event.setOccurredDate(command.getOccurredDate() == null ? event.getOccurredDate() : command.getOccurredDate());
        event.setNote(trimToNull(command.getNote()));
        return events.save(event);
    }

    public List<MajorEvent> list(String originType, String categoryCode, LocalDate from, LocalDate to) {
        return events.find(trimToNull(originType), trimToNull(categoryCode), from, to);
    }

    public MajorEvent update(Long id, LocalDate occurredDate, String note) {
        if (occurredDate == null) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "事件日期不能为空");
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
        Long id = parsePersistentId(command.getOriginKey(), "雷达事件");
        RadarEvent radarEvent = radar.findEvent(id).orElseThrow(() -> new ResourceNotFoundException("雷达事件不存在：" + id));
        MajorEvent event = base(command);
        event.setTitle(radarEvent.getCanonicalTitle());
        event.setSummary(radarEvent.getSummary());
        event.setSourceName("研究雷达");
        event.setCategoryCode(radarEvent.getCategoryCode());
        event.setOccurredDate(radarEvent.getFirstSeenAt() == null ? LocalDate.now() : radarEvent.getFirstSeenAt().toLocalDate());
        return event;
    }

    private MajorEvent liveNewsSnapshot(MajorEventCreateCommand command) {
        if (trimToNull(command.getTitle()) == null) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "资讯标题不能为空");
        MajorEvent event = base(command);
        event.setTitle(command.getTitle().trim());
        event.setSummary(trimToNull(command.getSummary()));
        event.setSourceName(trimToNull(command.getSourceName()));
        event.setSourceUrl(trimToNull(command.getSourceUrl()));
        event.setCategoryCode(trimToNull(command.getCategoryCode()));
        event.setOccurredDate(command.getOccurredDate() == null ? LocalDate.now() : command.getOccurredDate());
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
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "来源类型和来源标识不能为空");
        }
        String originType = command.getOriginType().trim().toUpperCase();
        if (!"NEWS_ITEM".equals(originType) && !"ARTICLE".equals(originType) && !"RADAR_EVENT".equals(originType)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "不支持的大事来源类型");
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
