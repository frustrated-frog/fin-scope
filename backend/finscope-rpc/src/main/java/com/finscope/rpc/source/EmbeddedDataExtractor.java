package com.finscope.rpc.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.Optional;

/**
 * 从现代前端页面的内嵌状态中恢复文章，避免把 JavaScript 空壳误判为正文。
 */
public class EmbeddedDataExtractor {
    private static final String[] TITLE_KEYS = {"title", "headline", "name"};
    private static final String[] BODY_KEYS = {"articleBody", "content", "body", "markdown"};
    private static final String[] DATE_KEYS = {"publishedAt", "datePublished", "publishTime"};
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<RawItem> extract(Document document, Source source) {
        for (Element script : document.select("script#__NEXT_DATA__[type=application/json]")) {
            try {
                JsonNode candidate = findArticle(objectMapper.readTree(script.data()));
                if (candidate == null) {
                    candidate = findArticle(objectMapper.readTree(script.html()));
                }
                if (candidate != null) {
                    return Optional.of(toRawItem(candidate, source));
                }
            } catch (Exception ignored) {
                // 单个内嵌状态损坏时继续使用其他抽取策略。
            }
        }
        return Optional.empty();
    }

    private JsonNode findArticle(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject() && !text(node, TITLE_KEYS).isEmpty()
                && text(node, BODY_KEYS).length() >= 30) {
            return node;
        }
        if (node.isContainerNode()) {
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                JsonNode found = findArticle(children.next());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private RawItem toRawItem(JsonNode node, Source source) {
        String title = text(node, TITLE_KEYS);
        String body = Jsoup.parse(text(node, BODY_KEYS)).text().trim();
        String summary = body.length() <= 160 ? body : body.substring(0, 160) + "…";
        RawItem item = new RawItem(title, source.getUrl(), publishedAt(node), summary, body);
        return item.withExtraction("ARTICLE", "web:embedded-next-data", 85,
                "从页面 __NEXT_DATA__ 内嵌状态恢复文章正文");
    }

    private String text(JsonNode node, String[] keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual() && !value.asText().trim().isEmpty()) {
                return value.asText().trim();
            }
        }
        return "";
    }

    private LocalDateTime publishedAt(JsonNode node) {
        String value = text(node, DATE_KEYS);
        if (!value.isEmpty()) {
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (Exception ignored) {
                try {
                    return LocalDateTime.parse(value);
                } catch (Exception ignoredAgain) {
                    // 使用抓取时间兜底。
                }
            }
        }
        return LocalDateTime.now();
    }
}
