package com.finscope.service.article;

import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ExternalServiceException;
import com.finscope.common.util.StringUtils;
import com.finscope.domain.article.ArticleIngestResult;
import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.domain.source.SourceType;
import com.finscope.domain.task.TaskPhase;
import com.finscope.rpc.source.SourceAdapter;
import com.finscope.rpc.source.SourceAdapterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class UrlIngestService {

    @Resource
    private SourceAdapterRegistry adapterRegistry;
    @Resource
    private ArticleIngestCoordinator articleIngestCoordinator;


    public ArticleIngestResult ingest(String url,
                                      String sourceName,
                                      String tags,
                                      String category,
                                      Consumer<TaskPhase> phaseConsumer) {
        validateUrl(url);
        long start = System.currentTimeMillis();

        // 自动识别来源类型
        SourceType detectedType = SourceType.fromUrl(url);
        String finalSourceName = StringUtils.isBlank(sourceName) ? detectedType.getDisplayName() : sourceName.trim();

        Source source = new Source();
        source.setName(finalSourceName);
        source.setType(detectedType.getCode());
        source.setUrl(url.trim());
        source.setTags(StringUtils.isBlank(tags) ? detectedType.getCategory() : tags.trim());

        try {
            log.info("手动链接入库开始 url={} sourceName={} tags={}", safeUrl(url), source.getName(), source.getTags());
            publishPhase(phaseConsumer, TaskPhase.FETCHING);
            SourceAdapter adapter = adapterRegistry.get(source);
            List<RawItem> items = adapter.fetch(source);
            if (items.isEmpty()) {
                throw new BusinessException(BizErrorCode.CONTENT_UNREADABLE, url);
            }
            log.info("手动链接抓取完成 url={} itemCount={}", safeUrl(url), items.size());
            RawItem item = items.get(0);
            publishPhase(phaseConsumer, TaskPhase.PARSING);
            assertReadable(item, url);
            ArticleIngestResult result = articleIngestCoordinator.ingest(source, item, category, phaseConsumer);
            log.info("手动链接入库成功 url={} articleId={} insightCardId={} durationMs={}",
                    safeUrl(url), result.getArticle().getId(), result.getInsightCard().getId(), System.currentTimeMillis() - start);
            return result;
        } catch (BusinessException ex) {
            log.warn("手动链接入库被拒绝 url={} code={} message={}", safeUrl(url), ex.getErrorCode().getCode(), ex.getMessage());
            throw ex;
        } catch (IllegalArgumentException ex) {
            log.warn("手动链接入库被拒绝 url={} message={}", safeUrl(url), ex.getMessage());
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("手动链接入库失败 url={} durationMs={}", safeUrl(url), System.currentTimeMillis() - start, ex);
            throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "URL 摄入失败，请稍后重试", ex);
        }
    }

    public void validateUrl(String url) {
        if (StringUtils.isBlank(url)) {
            throw new BusinessException(BizErrorCode.URL_REQUIRED);
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BusinessException(BizErrorCode.URL_SCHEME_UNSUPPORTED);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(BizErrorCode.URL_MALFORMED,
                    BizErrorCode.URL_MALFORMED.format(url), ex);
        }
    }

    private void publishPhase(Consumer<TaskPhase> phaseConsumer, TaskPhase phase) {
        if (phaseConsumer != null) {
            phaseConsumer.accept(phase);
        }
    }

    private void assertReadable(RawItem item, String url) {
        String title = text(item.getTitle());
        String summary = text(item.getSummary());
        String body = text(item.getBody());
        String combined = (title + " " + summary + " " + body).toLowerCase();
        if (isDynamicShell(title, combined)) {
            throw new BusinessException(BizErrorCode.URL_CONTENT_DYNAMIC_SHELL,
                    BizErrorCode.URL_CONTENT_DYNAMIC_SHELL.format(url), null);
        }
        if (isLinkOnlyContent(title, summary, body)) {
            throw new BusinessException(BizErrorCode.URL_CONTENT_LINK_ONLY,
                    BizErrorCode.URL_CONTENT_LINK_ONLY.format(url), null);
        }
        String evidence = (summary + " " + body).trim();
        if (evidence.length() < 40) {
            throw new BusinessException(BizErrorCode.URL_CONTENT_TOO_SHORT,
                    BizErrorCode.URL_CONTENT_TOO_SHORT.format(url), null);
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
