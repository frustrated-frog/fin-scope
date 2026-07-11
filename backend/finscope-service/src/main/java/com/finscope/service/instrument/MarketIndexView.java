package com.finscope.service.instrument;

import com.finscope.domain.instrument.Quote;

/** 服务层市场指数展示模型，不与 web 层响应对象耦合。 */
public class MarketIndexView {
    private final String code;
    private final String name;
    private final Quote quote;

    public MarketIndexView(String code, String name, Quote quote) {
        this.code = code;
        this.name = name;
        this.quote = quote;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Quote getQuote() {
        return quote;
    }
}
