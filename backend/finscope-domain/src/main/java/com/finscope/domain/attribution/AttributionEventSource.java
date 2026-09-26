package com.finscope.domain.attribution;

import lombok.Data;

/** 事件脉络扩展快照，与已有归因字段独立保存。 */
@Data
public class AttributionEventSource {
    private String id;
    private String title;
    private String url;
    private String publishedAt;
    private String content;
    private boolean supplemental;
}
