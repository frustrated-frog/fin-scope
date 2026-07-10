package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class WebListSourceAdapter implements SourceAdapter {
    private static final int MAX_LINKS = 20;
    private final WebArticleExtractor articleExtractor;

    public WebListSourceAdapter() {
        this(new WebArticleExtractor());
    }

    WebListSourceAdapter(WebArticleExtractor articleExtractor) {
        this.articleExtractor = articleExtractor;
    }

    @Override
    public boolean supports(String type) {
        return "WEB_LIST".equalsIgnoreCase(type);
    }

    @Override
    public List<RawItem> fetch(Source source) throws Exception {
        Document listDocument = Jsoup.connect(source.getUrl())
                .userAgent("FinScope/0.1")
                .timeout(10000)
                .get();
        List<String> urls = articleUrls(listDocument, source.getUrl(), linkLimit(source));
        List<RawItem> items = new ArrayList<RawItem>();
        for (String url : urls) {
            Document articleDocument = Jsoup.connect(url)
                    .userAgent("FinScope/0.1")
                    .timeout(10000)
                    .get();
            Source articleSource = copyWithUrl(source, url);
            RawItem item = articleExtractor.extract(articleDocument, articleSource);
            item.withExtraction(item.getContentType(), "web-list:" + item.getExtractionMethod(),
                    item.getQualityScore(), "从网页列表页抽取链接后抓取正文");
            items.add(item);
        }
        return items;
    }

    private List<String> articleUrls(Document document, String sourceUrl, int limit) {
        Set<String> urls = new LinkedHashSet<String>();
        collect(document, urls, "article a[href]");
        collect(document, urls, "main h1 a[href], main h2 a[href], main h3 a[href]");
        collect(document, urls, "h1 a[href], h2 a[href], h3 a[href]");
        if (urls.isEmpty()) {
            collect(document, urls, "main a[href]");
        }
        List<String> filtered = new ArrayList<String>();
        for (String url : urls) {
            if (url.equals(sourceUrl) || looksNonArticle(url)) {
                continue;
            }
            filtered.add(url);
            if (filtered.size() >= limit) {
                break;
            }
        }
        return filtered;
    }

    private int linkLimit(Source source) {
        int requested = source.getMaxItemsPerRun();
        return requested > 0 ? Math.min(requested, MAX_LINKS) : MAX_LINKS;
    }

    private void collect(Document document, Set<String> urls, String selector) {
        for (Element link : document.select(selector)) {
            String href = link.absUrl("href");
            if (href != null && (href.startsWith("http://") || href.startsWith("https://"))) {
                urls.add(href);
            }
        }
    }

    private boolean looksNonArticle(String url) {
        String normalized = url.toLowerCase();
        return normalized.contains("/about")
                || normalized.contains("/contact")
                || normalized.contains("/privacy")
                || normalized.contains("/terms")
                || normalized.endsWith("#")
                || normalized.startsWith("mailto:");
    }

    private Source copyWithUrl(Source source, String url) {
        Source copy = new Source();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setType("WEB");
        copy.setUrl(url);
        copy.setEnabled(source.isEnabled());
        copy.setFetchFrequencyMinutes(source.getFetchFrequencyMinutes());
        copy.setScheduledEnabled(source.isScheduledEnabled());
        copy.setScheduleTimes(source.getScheduleTimes());
        copy.setMaxItemsPerRun(source.getMaxItemsPerRun());
        copy.setCredibility(source.getCredibility());
        copy.setTags(source.getTags());
        return copy;
    }
}
