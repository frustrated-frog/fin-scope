package com.finscope.domain.attribution;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import com.finscope.common.enums.attribution.EventContextStatus;

/** 事件脉络扩展快照，与已有归因字段独立保存。 */
@Data
public class AttributionEventContext {
    private int version = 1;
    private EventContextStatus status;
    private String asOfDate;
    private String summary;
    private int searchCount;
    private List<AttributionEventDossier> events = new ArrayList<>();
    private List<AttributionEventSource> sources = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
