package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.acquisition.AcquisitionErrorType;
import com.finscope.rpc.acquisition.AcquisitionException;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.acquisition.BrowserFetcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

@Component
public class WebAcquisitionStrategy {
    private final AcquisitionRuntime acquisitionRuntime;
    private final BrowserFetcher browserFetcher;
    private final WebArticleExtractor articleExtractor;
    private final EmbeddedDataExtractor embeddedDataExtractor;

    @Autowired
    public WebAcquisitionStrategy(AcquisitionRuntime acquisitionRuntime, BrowserFetcher browserFetcher) {
        this(acquisitionRuntime, browserFetcher, new WebArticleExtractor(), new EmbeddedDataExtractor());
    }

    WebAcquisitionStrategy(AcquisitionRuntime acquisitionRuntime, BrowserFetcher browserFetcher,
                           WebArticleExtractor articleExtractor, EmbeddedDataExtractor embeddedDataExtractor) {
        this.acquisitionRuntime = acquisitionRuntime;
        this.browserFetcher = browserFetcher;
        this.articleExtractor = articleExtractor;
        this.embeddedDataExtractor = embeddedDataExtractor;
    }

    public RawItem fetch(Source source) {
        AcquisitionRequest request = request(source.getUrl(), "WEB_ARTICLE");
        AcquisitionResponse response = acquisitionRuntime.fetch(request);
        Document document = Jsoup.parse(response.getBodyText(), response.getFinalUri().toString());
        RawItem best = extractBest(document, source);
        if (!requiresBrowser(document, best)) {
            return best;
        }

        AcquisitionResponse rendered = browserFetcher.fetch(request(source.getUrl(), "WEB_BROWSER"));
        Document renderedDocument = Jsoup.parse(rendered.getBodyText(), rendered.getFinalUri().toString());
        RawItem renderedItem = extractBest(renderedDocument, source);
        if (requiresBrowser(renderedDocument, renderedItem)) {
            throw new AcquisitionException(AcquisitionErrorType.RENDER_REQUIRED,
                    "浏览器渲染后仍未获得有效正文", false, rendered.getHttpStatus());
        }
        renderedItem.withExtraction(renderedItem.getContentType(),
                "web:browser:" + renderedItem.getExtractionMethod(),
                renderedItem.getQualityScore(), "JavaScript 页面经隔离浏览器渲染后抽取正文");
        return renderedItem;
    }

    private AcquisitionRequest request(String url, String purpose) {
        return AcquisitionRequest.get(URI.create(url))
                .purpose(purpose)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();
    }

    private RawItem extractBest(Document document, Source source) {
        RawItem visible = articleExtractor.extract(document, source);
        Optional<RawItem> embedded = embeddedDataExtractor.extract(document, source);
        return embedded.isPresent() && embedded.get().getQualityScore() > visible.getQualityScore()
                ? embedded.get() : visible;
    }

    private boolean requiresBrowser(Document document, RawItem item) {
        int bodyLength = item.getBody() == null ? 0 : item.getBody().trim().length();
        if (bodyLength >= 120 || item.getQualityScore() >= 65) {
            return false;
        }
        String text = document.text().toLowerCase();
        boolean javascriptMessage = text.contains("enable javascript")
                || text.contains("javascript is disabled")
                || text.contains("启用 javascript")
                || text.contains("开启 javascript");
        boolean applicationRoot = document.select("#__next, #app, #root, [data-reactroot]").size() > 0;
        boolean externalScripts = document.select("script[src]").size() > 0;
        return javascriptMessage || (applicationRoot && externalScripts);
    }
}
