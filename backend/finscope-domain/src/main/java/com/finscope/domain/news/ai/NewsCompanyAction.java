package com.finscope.domain.news.ai;

/** 明确参与事件的公司，角色与原文证据分别保存。 */
public record NewsCompanyAction(String company, String role, String quote) {
    /** 保留原文中未中标、预中标等限定，避免把参与投标标为确定中标。 */
    public boolean overstatesAward() {
        if (!role.contains("中标") || provisional(role)) {
            return false;
        }
        return provisional(quote) || quote.contains("投标") && !quote.contains("中标") && !quote.contains("成交");
    }

    private boolean provisional(String text) {
        return text.contains("中标候选") || text.contains("拟中标") || text.contains("预中标")
                || text.contains("未中标") || text.contains("尚未确定中标");
    }
}
