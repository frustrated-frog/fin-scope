package com.finscope.web.request;

import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;
import lombok.Data;
import javax.validation.constraints.*;

@Data
public class ReviewFundamentalWatchRequest {
    @NotNull
    private FundamentalWatchStatus status;
    @NotBlank @Size(max = 2000)
    private String note;
    @NotNull @Min(0)
    private Integer revision;
}
