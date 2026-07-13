package com.finscope.domain.attribution;

import lombok.Data;

import java.util.List;

/**
 * 归因驱动因素：报告中的一条"原因"，带影响力与置信度双维度。
 * 作为 AttributionReport 的子对象，持久化时序列化为 JSON。
 */
@Data
public class AttributionDriver {
    /** 原因描述 */
    private String claim;
    /** 影响力：HIGH | MID | LOW */
    private String impactLevel;
    /** 置信度：HIGH | MID | LOW */
    private String confidence;
    /** 支撑说明 */
    private String detail;
    /** 关联证据的 url 列表（指向 AttributionEvidence） */
    private List<String> evidenceUrls;
    /** 支撑该驱动的可核验事实。 */
    private List<String> facts;
    /** 事件如何传导到预期、资金或估值，再影响价格。 */
    private String transmissionPath;
    /** 与该驱动相冲突或限制其解释力的信息。 */
    private String counterEvidence;
    /** 后续验证该驱动的观察窗口。 */
    private String observationWindow;

}
