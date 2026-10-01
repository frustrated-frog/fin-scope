package com.finscope.web.request;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class ExcludeReactionSampleRequest {
    @NotNull
    private Boolean excluded;
    @NotNull
    @Min(0)
    private Integer revision;
}
