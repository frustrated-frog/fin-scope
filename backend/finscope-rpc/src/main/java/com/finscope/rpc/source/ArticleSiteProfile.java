package com.finscope.rpc.source;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ArticleSiteProfile {
    private final String code;
    private final List<String> hostKeywords;
    private final List<String> titleSelectors;
    private final List<String> summarySelectors;
    private final List<String> dateSelectors;
    private final List<String> contentSelectors;
    private final List<String> removeSelectors;

    private ArticleSiteProfile(Builder builder) {
        this.code = builder.code;
        this.hostKeywords = immutable(builder.hostKeywords);
        this.titleSelectors = immutable(builder.titleSelectors);
        this.summarySelectors = immutable(builder.summarySelectors);
        this.dateSelectors = immutable(builder.dateSelectors);
        this.contentSelectors = immutable(builder.contentSelectors);
        this.removeSelectors = immutable(builder.removeSelectors);
    }

    public String getCode() {
        return code;
    }

    public boolean matches(String url) {
        String host = host(url);
        if (host.isEmpty()) {
            return false;
        }
        for (String keyword : hostKeywords) {
            String domain = keyword.toLowerCase(Locale.ROOT);
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    public String firstText(Document document, List<String> selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            String value = text(element);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    public String firstAttribute(Document document, List<String> selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element == null) {
                continue;
            }
            String value = element.hasAttr("content") ? element.attr("content") : element.attr("datetime");
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    public Elements contentCandidates(Document document) {
        Elements candidates = new Elements();
        for (String selector : contentSelectors) {
            candidates.addAll(document.select(selector));
        }
        return candidates;
    }

    public List<String> getTitleSelectors() {
        return titleSelectors;
    }

    public List<String> getSummarySelectors() {
        return summarySelectors;
    }

    public List<String> getDateSelectors() {
        return dateSelectors;
    }

    public List<String> getRemoveSelectors() {
        return removeSelectors;
    }

    private String text(Element element) {
        return element == null ? "" : element.text().trim();
    }

    private String host(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    public static Builder builder(String code) {
        return new Builder(code);
    }

    public static class Builder {
        private final String code;
        private final List<String> hostKeywords = new ArrayList<String>();
        private final List<String> titleSelectors = new ArrayList<String>();
        private final List<String> summarySelectors = new ArrayList<String>();
        private final List<String> dateSelectors = new ArrayList<String>();
        private final List<String> contentSelectors = new ArrayList<String>();
        private final List<String> removeSelectors = new ArrayList<String>();

        private Builder(String code) {
            this.code = code;
        }

        public Builder hosts(String... values) {
            addAll(hostKeywords, values);
            return this;
        }

        public Builder titles(String... values) {
            addAll(titleSelectors, values);
            return this;
        }

        public Builder summaries(String... values) {
            addAll(summarySelectors, values);
            return this;
        }

        public Builder dates(String... values) {
            addAll(dateSelectors, values);
            return this;
        }

        public Builder contents(String... values) {
            addAll(contentSelectors, values);
            return this;
        }

        public Builder removes(String... values) {
            addAll(removeSelectors, values);
            return this;
        }

        public ArticleSiteProfile build() {
            return new ArticleSiteProfile(this);
        }

        private void addAll(List<String> target, String... values) {
            if (values == null) {
                return;
            }
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    target.add(value.trim());
                }
            }
        }
    }
}
