package com.finscope.rpc.research;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.research.ResearchSourceDocument;
import com.finscope.domain.source.Source;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.source.WebArticleExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

@Component
public class DefaultResearchSourceReader implements ResearchSourceReader {
    private static final int MAX_RESPONSE_BYTES = 6 * 1024 * 1024;
    private final AcquisitionRuntime acquisitionRuntime;
    private final WebArticleExtractor articleExtractor;

    @Autowired
    public DefaultResearchSourceReader(AcquisitionRuntime acquisitionRuntime) {
        this(acquisitionRuntime, new WebArticleExtractor());
    }

    DefaultResearchSourceReader(AcquisitionRuntime acquisitionRuntime, WebArticleExtractor articleExtractor) {
        this.acquisitionRuntime = acquisitionRuntime;
        this.articleExtractor = articleExtractor;
    }

    @Override
    public ResearchSourceDocument read(String url) {
        URI uri = publicUri(url);
        AcquisitionRequest request = AcquisitionRequest.get(uri)
                .purpose("RESEARCH_SOURCE")
                .header("Accept", "text/html,application/xhtml+xml,application/pdf;q=0.9,*/*;q=0.5")
                .connectTimeoutMs(2500)
                .readTimeoutMs(5000)
                .deadlineMs(7000)
                .maxResponseBytes(MAX_RESPONSE_BYTES)
                .maxRetries(0)
                .followRedirects(false)
                .build();
        AcquisitionResponse response = acquisitionRuntime.fetch(request);
        String contentType = normalizeContentType(response.getContentType(), response.getFinalUri());
        if ("PDF".equals(contentType)) {
            return pdf(response);
        }
        return html(response);
    }

    private ResearchSourceDocument html(AcquisitionResponse response) {
        Document document = Jsoup.parse(response.getBodyText(), response.getFinalUri().toString());
        Source source = new Source();
        source.setName(response.getFinalUri().getHost());
        source.setType("WEB");
        source.setUrl(response.getFinalUri().toString());
        RawItem item = articleExtractor.extract(document, source);
        if (item.getBody() == null || item.getBody().trim().length() < 40) {
            throw new IllegalStateException("网页正文过短，不能作为全文证据");
        }
        return new ResearchSourceDocument(response.getFinalUri().toString(), item.getTitle(), item.getBody(),
                "WEB_PAGE", item.getExtractionMethod(), "FETCHED");
    }

    private ResearchSourceDocument pdf(AcquisitionResponse response) {
        try (PDDocument document = Loader.loadPDF(response.getBodyBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String body = stripper.getText(document).replaceAll("[\\t ]+", " ").trim();
            if (body.length() < 20) {
                throw new IllegalStateException("PDF 文本过短，可能是扫描件");
            }
            return new ResearchSourceDocument(response.getFinalUri().toString(), fileName(response.getFinalUri()),
                    body, "PDF", "pdf:pdfbox", "FETCHED");
        } catch (Exception error) {
            throw new IllegalStateException("PDF 文本抽取失败：" + safe(error), error);
        }
    }

    private URI publicUri(String value) {
        URI uri = URI.create(value == null ? "" : value.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
            throw new IllegalArgumentException("研究来源只允许公网 HTTP/HTTPS URL");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new IllegalArgumentException("研究来源不能访问本机或私网地址");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("研究来源不能访问本机或私网地址");
                }
            }
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("研究来源主机无法解析", error);
        }
        return uri;
    }

    private String normalizeContentType(String value, URI uri) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        return normalized.contains("pdf") || path.endsWith(".pdf") ? "PDF" : "WEB_PAGE";
    }

    private String fileName(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) return uri.getHost();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String safe(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
