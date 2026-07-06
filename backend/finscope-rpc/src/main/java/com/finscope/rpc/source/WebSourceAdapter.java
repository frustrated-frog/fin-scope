package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

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
    public List<RawItem> fetch(Source source) throws Exception {
        Document document = Jsoup.connect(source.getUrl())
                .userAgent("FinScope/0.1")
                .timeout(10000)
                .get();

        return Collections.singletonList(articleExtractor.extract(document, source));
    }
}
