package com.finscope.domain.research;

import com.finscope.domain.article.Article;

public final class ResearchSourceIdentity {
    private ResearchSourceIdentity() {
    }

    public static String resolve(Article article) {
        return article == null ? "未知来源" : resolve(article.getTitle(), article.getSourceName());
    }

    public static String resolve(String title, String sourceName) {
        if (title != null) {
            int separator = title.lastIndexOf(" - ");
            if (separator > 0 && separator + 3 < title.length()) {
                String publisher = title.substring(separator + 3).trim();
                if (!publisher.isEmpty() && publisher.length() <= 80) {
                    return publisher;
                }
            }
        }
        return sourceName == null || sourceName.trim().isEmpty() ? "未知来源" : sourceName.trim();
    }
}
