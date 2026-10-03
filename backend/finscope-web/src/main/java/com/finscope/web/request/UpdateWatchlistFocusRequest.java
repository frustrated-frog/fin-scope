package com.finscope.web.request;

import lombok.Data;
import javax.validation.constraints.Size;

@Data
public class UpdateWatchlistFocusRequest {
    @Size(max = 500)
    private String reason;
    @Size(max = 500)
    private String nextWatch;
    @Size(max = 80)
    private String direction;
}
