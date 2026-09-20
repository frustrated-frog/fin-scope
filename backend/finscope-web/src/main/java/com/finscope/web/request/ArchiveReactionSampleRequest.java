package com.finscope.web.request;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class ArchiveReactionSampleRequest {
    @NotNull
    private Boolean archived;
    @NotNull
    @Min(0)
    private Integer revision;
}
