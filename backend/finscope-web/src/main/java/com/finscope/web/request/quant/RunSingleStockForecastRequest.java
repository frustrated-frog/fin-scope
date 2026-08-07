package com.finscope.web.request.quant;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class RunSingleStockForecastRequest {
    @NotBlank(message = "股票代码不能为空")
    @Pattern(regexp = "\\d{6}", message = "股票代码必须是六位 A 股代码")
    private String code;
}
