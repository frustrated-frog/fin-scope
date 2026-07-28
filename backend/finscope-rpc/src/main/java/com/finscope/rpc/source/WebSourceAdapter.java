package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class WebSourceAdapter implements SourceAdapter {
    private final AcquisitionRuntime acquisitionRuntime;
    private final WebArticleExtractor articleExtractor;
    private final EmbeddedDataExtractor embeddedDataExtractor;

    public WebSourceAdapter() {
        this(new JdkAcquisitionRuntime(), new WebArticleExtractor(), new EmbeddedDataExtractor());
    }

    WebSourceAdapter(WebArticleExtractor articleExtractor) {
        this(new JdkAcquisitionRuntime(), articleExtractor, new EmbeddedDataExtractor());
    }

    @Autowired
    public WebSourceAdapter(AcquisitionRuntime acquisitionRuntime) {
        this(acquisitionRuntime, new WebArticleExtractor(), new EmbeddedDataExtractor());
    }

    WebSourceAdapter(AcquisitionRuntime acquisitionRuntime, WebArticleExtractor articleExtractor) {
        this(acquisitionRuntime, articleExtractor, new EmbeddedDataExtractor());
    }

    WebSourceAdapter(AcquisitionRuntime acquisitionRuntime, WebArticleExtractor articleExtractor,
                     EmbeddedDataExtractor embeddedDataExtractor) {
        this.acquisitionRuntime = acquisitionRuntime;
        this.articleExtractor = articleExtractor;
        this.embeddedDataExtractor = embeddedDataExtractor;
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
        AcquisitionResponse response = acquisitionRuntime.fetch(AcquisitionRequest
                .get(URI.create(source.getUrl()))
                .purpose("WEB_ARTICLE")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build());
        Document document = Jsoup.parse(response.getBodyText(), response.getFinalUri().toString());

        RawItem visible = articleExtractor.extract(document, source);
        Optional<RawItem> embedded = embeddedDataExtractor.extract(document, source);
        if (embedded.isPresent() && embedded.get().getQualityScore() > visible.getQualityScore()) {
            return Collections.singletonList(embedded.get());
        }
        return Collections.singletonList(visible);
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
