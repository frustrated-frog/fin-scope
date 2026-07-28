package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.acquisition.DisabledBrowserFetcher;
import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class WebSourceAdapter implements SourceAdapter {
    private final WebAcquisitionStrategy acquisitionStrategy;

    public WebSourceAdapter() {
        this(new WebAcquisitionStrategy(new JdkAcquisitionRuntime(), new DisabledBrowserFetcher(),
                new WebArticleExtractor(), new EmbeddedDataExtractor()));
    }

    WebSourceAdapter(WebArticleExtractor articleExtractor) {
        this(new WebAcquisitionStrategy(new JdkAcquisitionRuntime(), new DisabledBrowserFetcher(),
                articleExtractor, new EmbeddedDataExtractor()));
    }

    @Autowired
    public WebSourceAdapter(WebAcquisitionStrategy acquisitionStrategy) {
        this.acquisitionStrategy = acquisitionStrategy;
    }

    public WebSourceAdapter(AcquisitionRuntime acquisitionRuntime) {
        this(new WebAcquisitionStrategy(acquisitionRuntime, new DisabledBrowserFetcher(),
                new WebArticleExtractor(), new EmbeddedDataExtractor()));
    }

    WebSourceAdapter(AcquisitionRuntime acquisitionRuntime, WebArticleExtractor articleExtractor) {
        this(new WebAcquisitionStrategy(acquisitionRuntime, new DisabledBrowserFetcher(),
                articleExtractor, new EmbeddedDataExtractor()));
    }

    WebSourceAdapter(AcquisitionRuntime acquisitionRuntime, WebArticleExtractor articleExtractor,
                     EmbeddedDataExtractor embeddedDataExtractor) {
        this(new WebAcquisitionStrategy(acquisitionRuntime, new DisabledBrowserFetcher(),
                articleExtractor, embeddedDataExtractor));
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
        return Collections.singletonList(acquisitionStrategy.fetch(source));
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
