package com.finscope.domain.radar;

import com.finscope.common.enums.radar.RadarChangeKind;
import lombok.Data;

/** 已发布事件在一个观察窗口中的主要变化。时间为上海本地时间。 */
@Data
public class RadarRecentChange {
    private String eventId;
    private String title;
    private String summary;
    private String firstObservedAt;
    private String changedAt;
    private RadarChangeKind kind;
    private String newFact;
    private String reportTitle;
    private Integer sourcesBefore;
    private Integer sourcesAfter;
    private Integer addedReports;
}
