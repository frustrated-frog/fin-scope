package com.finscope.service.research.report;

import com.finscope.domain.article.Article;

final class ResearchSourceIdentity {
    private ResearchSourceIdentity() {
    }

    static String resolve(Article article) {
        String title = article == null ? null : article.getTitle();
        if (title != null) {
            int separator = title.lastIndexOf(" - ");
            if (separator > 0 && separator + 3 < title.length()) {
                String publisher = title.substring(separator + 3).trim();
                if (!publisher.isEmpty() && publisher.length() <= 80) return publisher;
            }
        }
        String source = article == null ? null : article.getSourceName();
        return source == null || source.trim().isEmpty() ? "未知来源" : source.trim();
    }
}
