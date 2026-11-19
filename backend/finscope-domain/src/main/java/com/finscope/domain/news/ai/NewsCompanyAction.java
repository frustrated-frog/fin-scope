package com.finscope.domain.news.ai;

/** 明确参与事件的公司，角色与原文证据分别保存。 */
public record NewsCompanyAction(String company, String role, String quote) {
}
