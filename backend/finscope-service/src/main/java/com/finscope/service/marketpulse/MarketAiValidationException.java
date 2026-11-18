package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketAiFailureCode;
import lombok.Getter;

/** 只携带固定错误码与程序生成的字段路径，避免异常回显模型内容。 */
@Getter
public class MarketAiValidationException extends IllegalArgumentException {
    private final MarketAiFailureCode code;
    private final String path;

    public MarketAiValidationException(MarketAiFailureCode code, String path) {
        super("市场研判校验失败 code=" + code + " path=" + path);
        this.code = code;
        this.path = path;
    }
}
