package com.finscope.domain.fetch;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Getter
@Setter
public class FetchRun {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 信息源 ID。
     */
    private Long sourceId;
    /**
     * 信息源名称。
     */
    private String sourceName;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间。
     */
    private LocalDateTime endedAt;
    /**
     * 成功数量。
     */
    private int successCount;
    /**
     * 重复数量。
     */
    private int duplicateCount;
    /**
     * 错误信息。
     */
    private String errorMessage;
}
