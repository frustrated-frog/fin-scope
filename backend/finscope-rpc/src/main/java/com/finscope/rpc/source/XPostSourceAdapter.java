package com.finscope.rpc.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class XPostSourceAdapter implements SourceAdapter {


    private static final Pattern STATUS_PATTERN = Pattern.compile("^/([^/]+)/status/(\\d+).*");
    private static final DateTimeFormatter X_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);

    private final String fxTwitterBaseUrl;
    private final String vxTwitterBaseUrl;
    private final AcquisitionRuntime acquisitionRuntime;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public XPostSourceAdapter() {
        this(new JdkAcquisitionRuntime(), "https://api.fxtwitter.com", "https://api.vxtwitter.com");
    }

    XPostSourceAdapter(String fxTwitterBaseUrl, String vxTwitterBaseUrl) {
        this(new JdkAcquisitionRuntime(), fxTwitterBaseUrl, vxTwitterBaseUrl);
    }

    @Autowired
    public XPostSourceAdapter(AcquisitionRuntime acquisitionRuntime) {
        this(acquisitionRuntime, "https://api.fxtwitter.com", "https://api.vxtwitter.com");
    }

    XPostSourceAdapter(AcquisitionRuntime acquisitionRuntime, String fxTwitterBaseUrl, String vxTwitterBaseUrl) {
        this.acquisitionRuntime = acquisitionRuntime;
        this.fxTwitterBaseUrl = trimTrailingSlash(fxTwitterBaseUrl);
        this.vxTwitterBaseUrl = trimTrailingSlash(vxTwitterBaseUrl);
    }

    @Override
    public boolean supports(String type) {
        return "X_POST".equalsIgnoreCase(type) || "TWITTER".equalsIgnoreCase(type) || "X".equalsIgnoreCase(type);
    }

    @Override
    public boolean supports(Source source) {
        return source != null && (supports(source.getType()) || parse(source.getUrl()) != null);
    }

    @Override
    public List<RawItem> fetch(Source source) throws Exception {
        StatusRef statusRef = parse(source.getUrl());
        if (statusRef == null) {
            throw new IllegalArgumentException("不是可识别的 X/Twitter status URL：" + source.getUrl());
        }
        Exception firstFailure = null;
        try {
            return Collections.singletonList(fetchFromFxTwitter(statusRef));
        } catch (Exception ex) {
            firstFailure = ex;
        }
        try {
            return Collections.singletonList(fetchFromVxTwitter(statusRef, firstFailure));
        } catch (Exception ex) {
            if (firstFailure != null) {
                ex.addSuppressed(firstFailure);
            }
            throw ex;
        }
    }

    private RawItem fetchFromFxTwitter(StatusRef ref) throws Exception {
        JsonNode root = objectMapper.readTree(get(fxTwitterBaseUrl + "/" + ref.screenName + "/status/" + ref.statusId));
        JsonNode tweet = root.path("tweet");
        if (tweet.isMissingNode()) {
            throw new IllegalStateException("fxtwitter 响应中没有 tweet 字段");
        }
        JsonNode article = tweet.path("article");
        if (!article.isMissingNode() && !article.isNull()) {
            return fromFxArticle(ref, tweet, article);
        }
        return fromFxTweet(ref, tweet, "x:fxtwitter:tweet");
    }

    private RawItem fromFxArticle(StatusRef ref, JsonNode tweet, JsonNode article) {
        String title = firstNonBlank(text(article, "title"), "X 长文 " + ref.statusId);
        String preview = clean(firstNonBlank(text(article, "preview_text"), firstParagraph(articleBody(article))));
        String author = author(tweet);
        String body = socialBody(author, publishedAt(tweet), metrics(tweet), articleBody(article));
        RawItem item = new RawItem(title, text(tweet, "url", ref.originalUrl), publishedAt(tweet), preview, body);
        item.withExtraction("SOCIAL_POST", "x:fxtwitter:article", quality(preview, body, 95),
                "通过公开 X JSON 适配器解析 status 关联长文内容");
        return item;
    }

    private RawItem fromFxTweet(StatusRef ref, JsonNode tweet, String method) {
        String tweetText = clean(firstNonBlank(text(tweet, "text"), text(tweet.path("raw_text"), "text")));
        String title = "X 帖子 | @" + ref.screenName + "：" + limit(tweetText, 56);
        if (tweetText.isEmpty()) {
            title = "X 帖子 | @" + ref.screenName + " / " + ref.statusId;
        }
        String body = socialBody(author(tweet), publishedAt(tweet), metrics(tweet), tweetText);
        RawItem item = new RawItem(title, text(tweet, "url", ref.originalUrl), publishedAt(tweet), tweetText, body);
        item.withExtraction("SOCIAL_POST", method, quality(tweetText, body, 82),
                "通过公开 X JSON 适配器解析普通帖子内容");
        return item;
    }

    private RawItem fetchFromVxTwitter(StatusRef ref, Exception previousFailure) throws Exception {
        JsonNode root = objectMapper.readTree(get(vxTwitterBaseUrl + "/" + ref.screenName + "/status/" + ref.statusId));
        String tweetText = clean(text(root, "text"));

        // Validate that vxtwitter didn't return link-only content
        if (looksLikeLinkOnly(tweetText)) {
            String message = "vxtwitter 返回的正文仅包含 X Article 链接，无法提取实际内容。原始链接：" + ref.originalUrl;
            if (previousFailure != null) {
                message += "；fxtwitter 失败原因：" + previousFailure.getMessage();
            }
            message += "。建议：该推文可能包含长文内容，请稍后重试或直接访问原链接查看。";
            throw new IllegalStateException(message);
        }

        String title = "X 帖子 | @" + ref.screenName + "：" + limit(tweetText, 56);
        if (tweetText.isEmpty()) {
            String message = "vxtwitter 响应没有正文";
            if (previousFailure != null) {
                message += "；fxtwitter 失败：" + previousFailure.getMessage();
            }
            throw new IllegalStateException(message);
        }
        String author = firstNonBlank(text(root, "user_name"), "") + "(@" + firstNonBlank(text(root, "user_screen_name"), ref.screenName) + ")";
        String body = socialBody(author, publishedAt(root), vxMetrics(root), tweetText);
        RawItem item = new RawItem(title, text(root, "tweetURL", ref.originalUrl), publishedAt(root), tweetText, body);
        item.withExtraction("SOCIAL_POST", "x:vxtwitter:tweet", quality(tweetText, body, 78),
                "通过公开 X JSON 备用适配器解析普通帖子内容");
        return item;
    }

    private String articleBody(JsonNode article) {
        StringBuilder body = new StringBuilder();
        JsonNode blocks = article.path("content").path("blocks");
        if (blocks.isArray()) {
            for (JsonNode block : blocks) {
                String text = clean(text(block, "text"));
                if (!text.isEmpty()) {
                    if (body.length() > 0) {
                        body.append("\n\n");
                    }
                    body.append(text);
                }
            }
        }
        return body.toString();
    }

    private String socialBody(String author, LocalDateTime publishedAt, String metrics, String content) {
        StringBuilder body = new StringBuilder();
        if (!isBlank(author)) {
            body.append("作者：").append(author).append("\n");
        }
        if (publishedAt != null) {
            body.append("发布时间：").append(publishedAt).append("\n");
        }
        if (!isBlank(metrics)) {
            body.append("互动：").append(metrics).append("\n");
        }
        body.append("正文：\n").append(clean(content));
        return body.toString().trim();
    }

    private String author(JsonNode tweet) {
        JsonNode author = tweet.path("author");
        String name = text(author, "name");
        String screenName = text(author, "screen_name");
        if (isBlank(name) && isBlank(screenName)) {
            return "";
        }
        if (isBlank(screenName)) {
            return name;
        }
        return firstNonBlank(name, screenName) + "(@" + screenName + ")";
    }

    private String metrics(JsonNode tweet) {
        return joinMetrics(
                number(tweet, "likes", "likes"),
                number(tweet, "retweets", "retweets"),
                number(tweet, "replies", "replies"),
                number(tweet, "views", "views"));
    }

    private String vxMetrics(JsonNode root) {
        return joinMetrics(
                number(root, "likes", "likes"),
                number(root, "retweets", "retweets"),
                number(root, "replies", "replies"));
    }

    private String joinMetrics(String... metrics) {
        StringBuilder builder = new StringBuilder();
        for (String metric : metrics) {
            if (!isBlank(metric)) {
                if (builder.length() > 0) {
                    builder.append("，");
                }
                builder.append(metric);
            }
        }
        return builder.toString();
    }

    private String number(JsonNode node, String field, String label) {
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return label + "=" + value.asLong();
        }
        return "";
    }

    private LocalDateTime publishedAt(JsonNode node) {
        String createdAt = firstNonBlank(text(node, "created_at"), text(node, "date"));
        if (!isBlank(createdAt)) {
            try {
                return ZonedDateTime.parse(createdAt, X_DATE_FORMATTER).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception ignored) {
                // Continue with epoch fields below.
            }
        }
        JsonNode epoch = node.path("created_timestamp");
        if (epoch.isMissingNode() || !epoch.isNumber()) {
            epoch = node.path("date_epoch");
        }
        if (epoch.isNumber()) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch.asLong()), ZoneId.systemDefault());
        }
        return LocalDateTime.now();
    }

    private String get(String url) throws Exception {
        return acquisitionRuntime.fetch(AcquisitionRequest.get(URI.create(url))
                .purpose("X_PUBLIC_JSON")
                .header("Accept", "application/json,text/plain;q=0.9,*/*;q=0.8")
                .connectTimeoutMs(12000)
                .readTimeoutMs(20000)
                .deadlineMs(30000)
                .build()).getBodyText();
    }

    private StatusRef parse(String url) {
        if (isBlank(url)) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            if (!"x.com".equals(host) && !"twitter.com".equals(host) && !"mobile.twitter.com".equals(host)) {
                return null;
            }
            Matcher matcher = STATUS_PATTERN.matcher(uri.getPath());
            if (!matcher.matches()) {
                return null;
            }
            return new StatusRef(url.trim(), matcher.group(1), matcher.group(2));
        } catch (Exception ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        return text(node, field, "");
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        return value.asText("");
    }

    private String firstParagraph(String value) {
        if (isBlank(value)) {
            return "";
        }
        String[] paragraphs = value.split("\\n\\s*\\n");
        return paragraphs.length == 0 ? value.trim() : paragraphs[0].trim();
    }

    private String clean(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.replace('\u00a0', ' ').replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
    }

    private int quality(String summary, String body, int high) {
        int length = (summary == null ? 0 : summary.length()) + (body == null ? 0 : body.length());
        if (length > 500) {
            return high;
        }
        if (length > 120) {
            return Math.max(82, high - 5);
        }
        return 60;
    }

    private String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? (isBlank(second) ? "" : second.trim()) : first.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.endsWith("/")) {
            return value == null ? "" : value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean looksLikeLinkOnly(String content) {
        String value = content == null ? "" : content.trim();
        if (value.isEmpty()) {
            return false;
        }
        String withoutUrls = value
                .replaceAll("https?://\\S+", "")
                .replaceAll("(?i)\\b(?:x|twitter)\\.com/\\S+", "")
                .trim();
        boolean containsArticleLink = value.toLowerCase(Locale.ROOT).contains("x.com/i/article/")
                || value.toLowerCase(Locale.ROOT).contains("twitter.com/i/article/");
        return containsArticleLink && withoutUrls.length() <= 8;
    }

    private static class StatusRef {
        private final String originalUrl;
        private final String screenName;
        private final String statusId;

        private StatusRef(String originalUrl, String screenName, String statusId) {
            this.originalUrl = originalUrl;
            this.screenName = screenName;
            this.statusId = statusId;
        }
    }
}
