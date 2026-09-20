package com.finscope.web.request;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class FollowReactionRequest {
    @NotNull
    private Boolean followed;
}
