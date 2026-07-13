package com.finscope.domain.knowledge;

import lombok.Data;

/** A user-facing, explainable next action for the knowledge workbench. */
@Data
public class KnowledgeAction {
    private String type;
    private String title;
    private String reason;
    private String sourceLabel;
    private String routeTarget;
    private Long taskId;
    private Long topicId;
}
