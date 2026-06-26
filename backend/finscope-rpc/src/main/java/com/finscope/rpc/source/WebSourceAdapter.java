package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.util.HtmlToMarkdownConverter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
public class WebSourceAdapter implements SourceAdapter {
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

        String title = firstNonBlank(
                document.select("h1").text(),
                document.select("meta[property=og:title]").attr("content"),
                document.title(),
                source.getUrl());

        String summary = firstNonBlank(
                document.select("meta[name=description]").attr("content"),
                document.select("meta[property=og:description]").attr("content"),
                "");

        // 提取主要内容区域的HTML
        Element content = document.selectFirst("article, main, .article, .content, #content");
        String htmlContent = content == null ? document.html() : content.html();

        // 转换为Markdown格式，设置baseUri以支持相对路径转换
        String bodyMarkdown = HtmlToMarkdownConverter.convert(
                htmlContent,
                HtmlToMarkdownConverter.ConversionConfig.builder()
                        .baseUri(source.getUrl())
                        .build()
        );

        RawItem item = new RawItem(title, source.getUrl(), LocalDateTime.now(), summary, bodyMarkdown);
        item.withExtraction("WEB_PAGE", "web:jsoup-markdown", quality(summary, bodyMarkdown), "HTML 转 Markdown 格式，保留结构");
        return Collections.singletonList(item);
    }

    private int quality(String summary, String body) {
        int length = (summary == null ? 0 : summary.length()) + (body == null ? 0 : body.length());
        if (length > 1200) {
            return 85;
        }
        if (length > 300) {
            return 75;
        }
        return 50;
    }

    private String firstNonBlank(String first, String second, String third, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        if (third != null && !third.trim().isEmpty()) {
            return third.trim();
        }
        return fallback;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return fallback;
    }
}
