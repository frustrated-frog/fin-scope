package com.finscope.domain.attribution;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 事件脉络扩展快照，与已有归因字段独立保存。 */
@Data
public class AttributionEventMilestone {
    /** ISO 日期；未知日期保留 null，不借用抓取时间。 */
    private String date;
    private String description;
    private List<String> sourceIds = new ArrayList<>();
}
