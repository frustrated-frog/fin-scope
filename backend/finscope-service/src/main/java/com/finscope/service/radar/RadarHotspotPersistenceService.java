package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class RadarHotspotPersistenceService {
    @Resource
    private RadarRepository repository;

    /** 有界批量保存事件和关系；页面版本仍在完整生产批次成功后发布。 */
    public List<RadarEvent> persistEvents(List<RadarEvent> events, Map<String, List<RadarEventSignal>> linksByEventKey) {
        return repository.saveEventsWithSignals(events, linksByEventKey);
    }
}
