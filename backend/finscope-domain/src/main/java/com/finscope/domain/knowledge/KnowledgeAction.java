package com.finscope.domain.knowledge;

import lombok.Data;

/** A user-facing, explainable next action for the knowledge workbench. */
@Data
public class KnowledgeAction {
    /**
     * 类型。
     */
    private String type;
    /**
     * 标题。
     */
    private String title;
    /**
     * 原因说明。
     */
    private String reason;
    /**
     * 来源标签。
     */
    private String sourceLabel;
    /**
     * 路由目标。
     */
    private String routeTarget;
    /**
     * 任务 ID。
     */
    private Long taskId;
    /**
     * 主题 ID。
     */
    private Long topicId;
}
