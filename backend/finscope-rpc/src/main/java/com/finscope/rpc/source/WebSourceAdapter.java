package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class WebSourceAdapter implements SourceAdapter {
    private final WebArticleExtractor articleExtractor;

    public WebSourceAdapter() {
        this(new WebArticleExtractor());
    }

    WebSourceAdapter(WebArticleExtractor articleExtractor) {
        this.articleExtractor = articleExtractor;
    }

    @Override
    public boolean supports(String type) {
        return "WEB".equalsIgnoreCase(type);
    }

    @Override
    public boolean supports(Source source) {
        return source != null && (supports(source.getType()) || isHttpUrl(source.getUrl()));
    }

    @Override
    public List<RawItem> fetch(Source source) throws Exception {
        Document document = Jsoup.connect(source.getUrl())
                .userAgent("FinScope/0.1")
                .timeout(10000)
                .get();

        return Collections.singletonList(articleExtractor.extract(document, source));
    }

    private boolean isHttpUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        try {
            String scheme = new URI(url.trim()).getScheme();
            if (scheme == null) {
                return false;
            }
            String normalized = scheme.toLowerCase(Locale.ROOT);
            return "http".equals(normalized) || "https".equals(normalized);
        } catch (Exception ignored) {
            return false;
        }
    }
}
