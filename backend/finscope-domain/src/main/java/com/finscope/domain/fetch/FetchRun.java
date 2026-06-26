package com.finscope.domain.fetch;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Getter
@Setter
public class FetchRun {
    private Long id;
    private Long sourceId;
    private String sourceName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private int successCount;
    private int duplicateCount;
    private String errorMessage;
}
