package com.finscope.web.request;

public class StartAttributionRequest {
    private String code;
    private String type;
    private String name;
    private Double changePct;
    /** 归因对应的行情交易日，yyyy-MM-dd。 */
    private String quoteDate;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getChangePct() {
        return changePct;
    }

    public void setChangePct(Double changePct) {
        this.changePct = changePct;
    }

    public String getQuoteDate() { return quoteDate; }
    public void setQuoteDate(String quoteDate) { this.quoteDate = quoteDate; }
}
