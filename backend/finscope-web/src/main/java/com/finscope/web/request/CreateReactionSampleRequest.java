package com.finscope.web.request;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
public class CreateReactionSampleRequest {
    @NotNull
    @Positive
    private Long majorEventId;
}
