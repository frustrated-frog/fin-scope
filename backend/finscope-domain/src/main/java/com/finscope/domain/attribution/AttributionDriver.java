package com.finscope.domain.attribution;

import lombok.Data;

import java.util.List;

/**
 * 归因驱动因素：报告中的一条"原因"，带影响力与置信度双维度。
 * 作为 AttributionReport 的子对象，持久化时序列化为 JSON。
 */
@Data
public class AttributionDriver {
    /** 驱动在当日归因中的角色。 */
    private String role;
    /**
     * 归因或证据主张。
     */
    private String claim;
    /** 面向普通用户的白话解释。 */
    private String plainExplanation;
    /**
     * 影响力等级。
     */
    private String impactLevel;
    /**
     * 置信度。
     */
    private String confidence;
    /**
     * 详细说明。
     */
    private String detail;
    /**
     * 支撑证据 URL 列表。
     */
    private List<String> evidenceUrls;
    /**
     * 事实记录。
     */
    private List<String> facts;
    /**
     * 影响传导路径。
     */
    private String transmissionPath;
    /**
     * 反向证据或相反解释。
     */
    private String counterEvidence;
    /**
     * 后续观察窗口。
     */
    private String observationWindow;

}
