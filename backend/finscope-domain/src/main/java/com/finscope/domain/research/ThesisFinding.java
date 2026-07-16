package com.finscope.domain.research;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可追溯的简短发现，用于支持、挑战或暂时悬置某个研究命题。
 */
@Data
public class ThesisFinding {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 研究命题 ID。
     */
    private Long thesisId;
    /**
     * 研究运行 ID。
     */
    private Long researchRunId;
    /**
     * 立场。
     */
    private String stance;
    /**
     * 摘要。
     */
    private String summary;
    /**
     * 证据 ID。
     */
    private Long evidenceId;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;

}
