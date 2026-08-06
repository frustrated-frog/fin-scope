package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class RadarHotspotPersistenceService {
    private final RadarRepository repository;

    public RadarHotspotPersistenceService(RadarRepository repository) { this.repository = repository; }

    /**
     * 将一轮雷达事件的聚合结果连同各自的信号关系在单个事务内写入。
     * 事件与 radar_event_signal 关系保持一致，避免生产中途失败时留下"事件已写、关系未写"的半状态。
     * evidence 增强调度由调用方在事务外异步触发。
     *
     * @param events            本轮要持久化的事件（顺序与 ranked 列表一致）
     * @param linksByEventKey   每个事件对应的信号关系，按 event_key 索引
     * @return 数据库回读后的事件列表，顺序与输入一致
     */
    @Transactional
    public List<RadarEvent> persistEvents(List<RadarEvent> events, Map<String, List<RadarEventSignal>> linksByEventKey) {
        if (events == null || events.isEmpty()) return Collections.emptyList();
        List<RadarEvent> saved = new ArrayList<RadarEvent>();
        for (RadarEvent event : events) {
            RadarEvent stored = repository.saveEvent(event);
            List<RadarEventSignal> links = linksByEventKey == null ? null : linksByEventKey.get(event.getEventKey());
            repository.replaceEventSignals(stored.getId(),
                    links == null ? Collections.<RadarEventSignal>emptyList() : links);
            repository.expireDuplicateEventsByCanonicalTitle(stored.getCanonicalTitle(), stored.getId(),
                    event.getUpdatedAt() == null ? LocalDateTime.now() : event.getUpdatedAt());
            saved.add(stored);
        }
        return saved;
    }
}
