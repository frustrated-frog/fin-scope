package com.finscope.rpc.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.util.HtmlToMarkdownConverter;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WebArticleExtractor {
    private static final String[] GENERIC_TITLE_SELECTORS = {
            "h1", "meta[property=og:title]", "meta[name=twitter:title]"
    };
    private static final String[] GENERIC_SUMMARY_SELECTORS = {
            "meta[name=description]", "meta[property=og:description]", "meta[name=twitter:description]"
    };
    private static final String[] GENERIC_DATE_SELECTORS = {
            "meta[property=article:published_time]", "meta[name=pubdate]", "meta[name=publishdate]", "time[datetime]"
    };
    private static final String[] GENERIC_CONTENT_SELECTORS = {
            "article", "main", "[role=main]", ".article-content", ".post-content", ".entry-content", ".content", "#content"
    };
    private static final String[] GENERIC_REMOVE_SELECTORS = {
            "script", "style", "noscript", "nav", "aside", "footer", "header", "iframe",
            ".share", ".social", ".recommend", ".related", ".advert", ".ad", ".breadcrumb"
    };
    private static final String[] NOISE_TEXT_MARKERS = {
            "责任编辑", "热门推荐", "相关阅读", "Related Materials", "Share this page"
    };
    private static final DateTimeFormatter[] LOCAL_DATE_TIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"),
            DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"),
            DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss")
    };
    private static final DateTimeFormatter[] LOCAL_DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    };

    private final ArticleSiteProfileRegistry profileRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebArticleExtractor() {
        this(new ArticleSiteProfileRegistry());
    }

    WebArticleExtractor(ArticleSiteProfileRegistry profileRegistry) {
        this.profileRegistry = profileRegistry;
    }

    public RawItem extract(Document document, Source source) {
        String url = source == null ? document.baseUri() : source.getUrl();
        ArticleSiteProfile profile = profileRegistry.match(url);
        JsonNode structuredData = firstStructuredArticle(document);

        String title = firstNonBlank(
                profileText(document, profile, FieldType.TITLE),
                structuredText(structuredData, "headline", "name"),
                selectorValue(document, GENERIC_TITLE_SELECTORS),
                document.title(),
                url);
        String summary = firstNonBlank(
                profileText(document, profile, FieldType.SUMMARY),
                structuredText(structuredData, "description"),
                selectorValue(document, GENERIC_SUMMARY_SELECTORS),
                "");
        LocalDateTime publishedAt = parseDate(firstNonBlank(
                profileText(document, profile, FieldType.DATE),
                structuredText(structuredData, "datePublished", "dateCreated"),
                selectorValue(document, GENERIC_DATE_SELECTORS),
                ""));

        Element content = selectContent(document, profile);
        String bodyMarkdown = markdown(content, url);
        if (summary.isEmpty()) {
            summary = firstParagraph(bodyMarkdown);
        }

        RawItem item = new RawItem(title, canonicalUrl(document, url), publishedAt, summary, bodyMarkdown);
        String method = profile == null ? "web:generic-score" : "web:profile:" + profile.getCode();
        item.withExtraction("WEB_PAGE", method, quality(summary, bodyMarkdown),
                profile == null ? "通用网页正文候选评分抽取" : "站点 Profile + 正文候选评分抽取");
        return item;
    }

    private Element selectContent(Document document, ArticleSiteProfile profile) {
        List<Element> candidates = new ArrayList<Element>();
        if (profile != null) {
            candidates.addAll(profile.contentCandidates(document));
        }
        for (String selector : GENERIC_CONTENT_SELECTORS) {
            candidates.addAll(document.select(selector));
        }
        if (document.body() != null) {
            candidates.add(document.body());
        }

        Element best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Element candidate : candidates) {
            Element cleaned = clean(candidate, profile);
            int score = score(cleaned);
            if (score > bestScore) {
                bestScore = score;
                best = cleaned;
            }
        }
        return best == null ? document : best;
    }

    private Element clean(Element element, ArticleSiteProfile profile) {
        Element copy = element.clone();
        for (String selector : GENERIC_REMOVE_SELECTORS) {
            copy.select(selector).remove();
        }
        if (profile != null) {
            for (String selector : profile.getRemoveSelectors()) {
                copy.select(selector).remove();
            }
        }
        removeNoisyText(copy);
        return copy;
    }

    private void removeNoisyText(Element root) {
        Elements elements = root.select("p, div, span, section, aside");
        for (Element element : elements) {
            String text = element.text();
            if (text == null) {
                continue;
            }
            String trimmed = text.trim();
            for (String marker : NOISE_TEXT_MARKERS) {
                if (trimmed.contains(marker)) {
                    element.remove();
                    break;
                }
            }
        }
    }

    private int score(Element element) {
        String text = element.text().trim();
        int length = text.length();
        int paragraphCount = element.select("p").size();
        int linkTextLength = 0;
        for (Element link : element.select("a")) {
            linkTextLength += link.text().length();
        }
        int linkPenalty = length == 0 ? 0 : (linkTextLength * 100 / length);
        return length + paragraphCount * 80 - linkPenalty * 12;
    }

    private String markdown(Element element, String baseUri) {
        String body = element == null ? "" : element.outerHtml();
        return HtmlToMarkdownConverter.convert(
                body,
                HtmlToMarkdownConverter.ConversionConfig.builder()
                        .baseUri(baseUri)
                        .build());
    }

    private String profileText(Document document, ArticleSiteProfile profile, FieldType fieldType) {
        if (profile == null) {
            return "";
        }
        switch (fieldType) {
            case TITLE:
                return firstNonBlank(
                        profile.firstText(document, profile.getTitleSelectors()),
                        profile.firstAttribute(document, profile.getTitleSelectors()));
            case SUMMARY:
                return firstNonBlank(
                        profile.firstAttribute(document, profile.getSummarySelectors()),
                        profile.firstText(document, profile.getSummarySelectors()));
            case DATE:
                return firstNonBlank(
                        profile.firstAttribute(document, profile.getDateSelectors()),
                        profile.firstText(document, profile.getDateSelectors()));
            default:
                return "";
        }
    }

    private String selectorValue(Document document, String[] selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element == null) {
                continue;
            }
            String value = firstNonBlank(element.attr("content"), element.attr("datetime"), element.text());
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String canonicalUrl(Document document, String fallback) {
        Element canonical = document.selectFirst("link[rel=canonical]");
        String href = canonical == null ? "" : canonical.absUrl("href");
        return firstNonBlank(href, fallback);
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        String trimmed = normalizeDateValue(value);
        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (Exception ignored) {
            // try the next format
        }
        try {
            return ZonedDateTime.parse(trimmed).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {
            // try the next format
        }
        for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (Exception ignored) {
                // try the next format
            }
        }
        for (DateTimeFormatter formatter : LOCAL_DATE_FORMATTERS) {
            try {
                return java.time.LocalDate.parse(trimmed, formatter).atStartOfDay();
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return LocalDateTime.now();
    }

    private String normalizeDateValue(String value) {
        return value.trim()
                .replace('\u00A0', ' ')
                .replace("发布时间：", "")
                .replace("发布日期：", "")
                .replace("Published:", "")
                .trim();
    }

    private JsonNode firstStructuredArticle(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = objectMapper.readTree(script.html());
                JsonNode article = findArticleNode(root);
                if (article != null) {
                    return article;
                }
            } catch (Exception ignored) {
                // Ignore malformed structured data and continue with DOM extraction.
            }
        }
        return null;
    }

    private JsonNode findArticleNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findArticleNode(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        JsonNode graph = node.path("@graph");
        if (graph.isArray()) {
            JsonNode found = findArticleNode(graph);
            if (found != null) {
                return found;
            }
        }
        String type = node.path("@type").asText("");
        if (type.contains("Article") || type.contains("NewsArticle") || type.contains("Report")) {
            return node;
        }
        if (node.has("headline") || node.has("datePublished")) {
            return node;
        }
        return null;
    }

    private String structuredText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String firstParagraph(String markdown) {
        if (markdown == null) {
            return "";
        }
        String[] lines = markdown.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
            }
        }
        return "";
    }

    private int quality(String summary, String body) {
        int length = length(summary) + length(body);
        if (length > 1200) {
            return 88;
        }
        if (length > 300) {
            return 80;
        }
        if (length > 80) {
            return 68;
        }
        return 45;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private enum FieldType {
        TITLE,
        SUMMARY,
        DATE
    }
}
