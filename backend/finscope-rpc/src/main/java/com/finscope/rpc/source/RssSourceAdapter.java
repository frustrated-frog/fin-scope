package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import com.finscope.rpc.util.HtmlToMarkdownConverter;
import com.rometools.rome.feed.module.DCModule;
import com.rometools.rome.feed.module.Module;
import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndPerson;
import com.rometools.rome.io.SyndFeedInput;
import org.jdom2.Element;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RssSourceAdapter implements SourceAdapter {
    private final AcquisitionRuntime acquisitionRuntime;

    public RssSourceAdapter() {
        this(new JdkAcquisitionRuntime());
    }

    @Autowired
    RssSourceAdapter(AcquisitionRuntime acquisitionRuntime) {
        this.acquisitionRuntime = acquisitionRuntime;
    }

    @Override
    public boolean supports(String type) {
        return "RSS".equalsIgnoreCase(type);
    }

    @Override
    public List<RawItem> fetch(Source source) throws Exception {
        String xml = acquisitionRuntime.fetch(AcquisitionRequest
                .get(URI.create(source.getUrl()))
                .purpose("RSS_FEED")
                .header("Accept", "application/rss+xml,application/atom+xml,application/xml,text/xml;q=0.9,*/*;q=0.8")
                .build()).getBodyText();
        if (!xml.isEmpty() && xml.charAt(0) == '\uFEFF') {
            xml = xml.substring(1).trim();
        }
        SyndFeed feed = new SyndFeedInput().build(new StringReader(xml));
        List<RawItem> items = new ArrayList<RawItem>();

        // 准备转换配置，设置baseUri
        HtmlToMarkdownConverter.ConversionConfig config = HtmlToMarkdownConverter.ConversionConfig.builder()
                .baseUri(source.getUrl())
                .build();

        for (Object raw : feed.getEntries()) {
            SyndEntry entry = (SyndEntry) raw;
            String summaryHtml = entry.getDescription() == null ? "" : entry.getDescription().getValue();
            String contentHtml = firstNonBlank(content(entry), summaryHtml);

            // HTML转Markdown
            String summary = HtmlToMarkdownConverter.convert(summaryHtml, config);
            String contentMarkdown = HtmlToMarkdownConverter.convert(contentHtml, config);

            List<String> authors = authors(entry);
            List<String> categories = categories(entry);
            LocalDateTime publishedAt = entry.getPublishedDate() == null
                    ? LocalDateTime.now()
                    : LocalDateTime.ofInstant(entry.getPublishedDate().toInstant(), ZoneId.systemDefault());

            String body = structuredBody(authors, categories, contentMarkdown);
            RawItem item = new RawItem(entry.getTitle(), entry.getLink(), publishedAt, summary, body);
            item.withExtraction("RSS_ITEM", "rss:rome-markdown", quality(summary, body), "RSS 条目解析，HTML 转 Markdown 格式，保留作者/分类元数据");
            items.add(item);
        }
        return items;
    }

    private String content(SyndEntry entry) {
        if (entry.getContents() == null || entry.getContents().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Object raw : entry.getContents()) {
            SyndContent content = (SyndContent) raw;
            if (!isBlank(content.getValue())) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append(clean(content.getValue()));
            }
        }
        return builder.toString();
    }

    private List<String> authors(SyndEntry entry) {
        Set<String> authors = new LinkedHashSet<String>();
        if (entry.getAuthors() != null) {
            for (Object raw : entry.getAuthors()) {
                SyndPerson person = (SyndPerson) raw;
                if (!isBlank(person.getName())) {
                    authors.add(person.getName().trim());
                }
            }
        }
        if (entry.getForeignMarkup() != null) {
            for (Element element : entry.getForeignMarkup()) {
                if ("creator".equalsIgnoreCase(element.getName()) && !isBlank(element.getText())) {
                    authors.add(element.getText().trim());
                }
            }
        }
        Module module = entry.getModule(DCModule.URI);
        if (module instanceof DCModule) {
            String creator = ((DCModule) module).getCreator();
            if (!isBlank(creator)) {
                authors.add(creator.trim());
            }
        }
        return new ArrayList<String>(authors);
    }

    private List<String> categories(SyndEntry entry) {
        Set<String> categories = new LinkedHashSet<String>();
        if (entry.getCategories() != null) {
            for (Object raw : entry.getCategories()) {
                SyndCategory category = (SyndCategory) raw;
                if (!isBlank(category.getName())) {
                    categories.add(category.getName().trim());
                }
            }
        }
        return new ArrayList<String>(categories);
    }

    private String structuredBody(List<String> authors, List<String> categories, String content) {
        StringBuilder body = new StringBuilder();
        if (!authors.isEmpty()) {
            body.append("作者：").append(String.join("、", authors)).append("\n");
        }
        if (!categories.isEmpty()) {
            body.append("分类：").append(String.join("、", categories)).append("\n");
        }
        if (!isBlank(content)) {
            body.append("摘要：").append(content);
        }
        return body.toString().trim();
    }

    private String clean(String value) {
        if (isBlank(value)) {
            return "";
        }
        return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
    }

    private int quality(String summary, String body) {
        int length = (summary == null ? 0 : summary.length()) + (body == null ? 0 : body.length());
        if (length > 600) {
            return 95;
        }
        if (length > 220) {
            return 85;
        }
        return 70;
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }
        return isBlank(second) ? "" : second.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
