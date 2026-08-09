package com.finscope.web.request.quant;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.AssertTrue;

@Data
public class RunSingleStockForecastRequest {
    @NotBlank(message = "股票代码不能为空")
    @Pattern(regexp = "\\d{6}", message = "股票代码必须是六位 A 股代码")
    private String code;

    private int horizonDays = 5;

    @AssertTrue(message = "预测周期只支持 1、5、20 个交易日")
    public boolean isHorizonSupported() {
        return horizonDays == 1 || horizonDays == 5 || horizonDays == 20;
    }
}
