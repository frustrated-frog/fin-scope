package com.finscope.service.article;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.article.ArticleIngestResult;
import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.domain.source.SourceType;
import com.finscope.rpc.source.SourceAdapter;
import com.finscope.rpc.source.SourceAdapterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.util.List;

@Service
@Slf4j
public class UrlIngestService {

    @Resource
    private SourceAdapterRegistry adapterRegistry;
    @Resource
    private ArticleIngestCoordinator articleIngestCoordinator;


    public ArticleIngestResult ingest(String url, String sourceName, String tags) {
        validateUrl(url);
        long start = System.currentTimeMillis();

        // 自动识别来源类型
        SourceType detectedType = SourceType.fromUrl(url);
        String finalSourceName = isBlank(sourceName) ? detectedType.getDisplayName() : sourceName.trim();

        Source source = new Source();
        source.setName(finalSourceName);
        source.setType(detectedType.getCode());
        source.setUrl(url.trim());
        source.setTags(isBlank(tags) ? detectedType.getCategory() : tags.trim());

        try {
            log.info("manual url ingest start url={} sourceName={} tags={}", safeUrl(url), source.getName(), source.getTags());
            SourceAdapter adapter = adapterRegistry.get(source);
            List<RawItem> items = adapter.fetch(source);
            if (items.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "No readable content found: " + url);
            }
            log.info("manual url ingest fetched url={} itemCount={}", safeUrl(url), items.size());
            RawItem item = items.get(0);
            assertReadable(item, url);
            ArticleIngestResult result = articleIngestCoordinator.ingest(source, item);
            log.info("manual url ingest success url={} articleId={} insightCardId={} durationMs={}",
                    safeUrl(url), result.getArticle().getId(), result.getInsightCard().getId(), System.currentTimeMillis() - start);
            return result;
        } catch (BusinessException ex) {
            log.warn("manual url ingest rejected url={} code={} message={}", safeUrl(url), ex.getErrorCode().getCode(), ex.getMessage());
            throw ex;
        } catch (IllegalArgumentException ex) {
            log.warn("manual url ingest rejected url={} message={}", safeUrl(url), ex.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("manual url ingest failed url={} durationMs={}", safeUrl(url), System.currentTimeMillis() - start, ex);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Failed to ingest URL: " + ex.getMessage(), ex);
        }
    }

    private void validateUrl(String url) {
        if (isBlank(url)) {
            throw new IllegalArgumentException("URL must not be empty");
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Only http/https URL is supported");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid URL: " + url, ex);
        }
    }

    private void assertReadable(RawItem item, String url) {
        String title = text(item.getTitle());
        String summary = text(item.getSummary());
        String body = text(item.getBody());
        String combined = (title + " " + summary + " " + body).toLowerCase();
        if (isDynamicShell(title, combined)) {
            throw new IllegalArgumentException("未能读取到可用正文：该页面更像是登录/JavaScript 渲染壳页，无法生成可靠情报卡片。URL: " + url);
        }
        if (isLinkOnlyContent(title, summary, body)) {
            throw new IllegalArgumentException(
                    "未能读取到可用正文：检测到正文仅包含 X Article 链接，缺少实际内容。URL: " + url
                    + "。该推文可能包含长文内容，建议稍后重试或直接访问原链接。");
        }
        String evidence = (summary + " " + body).trim();
        if (evidence.length() < 40) {
            throw new IllegalArgumentException("未能读取到可用正文：页面正文过短，无法生成可靠情报卡片。URL: " + url);
        }
    }

    private boolean isDynamicShell(String title, String combined) {
        if (combined.contains("javascript is disabled") || combined.contains("enable javascript")) {
            return true;
        }
        if (combined.contains("continue using x.com") || combined.contains("continue to x.com")) {
            return true;
        }
        if ("x".equalsIgnoreCase(title) && combined.contains("x.com")) {
            return true;
        }
        if (combined.contains("log in") && combined.contains("sign up") && combined.contains("twitter")) {
            return true;
        }
        return false;
    }

    private boolean isLinkOnlyContent(String title, String summary, String body) {
        String combined = (summary + " " + body).trim();
        if (combined.isEmpty()) {
            return false;
        }
        String withoutUrls = combined
                .replaceAll("https?://\\S+", "")
                .replaceAll("(?i)\\b(?:x|twitter)\\.com/\\S+", "")
                .trim();
        boolean containsArticleLink = combined.toLowerCase().contains("x.com/i/article/")
                || combined.toLowerCase().contains("twitter.com/i/article/");
        return containsArticleLink && withoutUrls.length() <= 8;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeUrl(String url) {
        if (url == null) {
            return "";
        }
        try {
            URI uri = new URI(url.trim());
            StringBuilder builder = new StringBuilder();
            if (uri.getScheme() != null) {
                builder.append(uri.getScheme()).append("://");
            }
            if (uri.getHost() != null) {
                builder.append(uri.getHost());
            }
            if (uri.getPath() != null) {
                builder.append(uri.getPath());
            }
            if (uri.getQuery() != null) {
                builder.append("?...");
            }
            return builder.length() == 0 ? url.trim() : builder.toString();
        } catch (Exception ex) {
            return url.trim();
        }
    }
}
