package com.finscope.service.news;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.news.NewsReportRepository;
import com.finscope.dao.news.NewsCategoryRepository;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.domain.news.*;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.research.material.ResearchMaterialGateway;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/** 已采集内容的工作台；查询不触发外部抓取或模型调用。 */
@lombok.extern.slf4j.Slf4j
@Service
public class NewsWindowService {
    @Resource
    private NewsReportRepository reports;
    @Resource
    private NewsCategoryRepository categories;
    @Resource
    private NewsRuleClassifier rules;
    @Resource
    private ReactionSampleRepository reactions;
    @Resource
    private ResearchMaterialGateway gateway;
    private Clock clock = Clock.systemDefaultZone();

    public void ingest(List<ResearchMaterial> materials) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<NewsCategory> enabled = categories.findEnabled();
        List<NewsReport> batch = new ArrayList<>();
        for (ResearchMaterial material : materials) {
            if (material == null || blank(material.getProviderCode()) || blank(material.getTitle()) || blank(material.getContent())) {
                continue;
            }
            if (material.getPublishedAt() != null && (material.getPublishedAt().isBefore(now.minusHours(36))
                    || material.getPublishedAt().isAfter(now.plusMinutes(5)))) {
                continue;
            }
            batch.add(report(material, enabled, now));
            if (batch.size() == 250) {
                reports.ingest(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            reports.ingest(batch);
        }
        // 七天缓冲供停机恢复；已进入观察或大事记的新闻依据不清理。
        reports.prune(now.minusDays(7));
    }

    public NewsWindowPage query(NewsWindowQuery query) {
        normalize(query);
        LocalDateTime now = LocalDateTime.now(clock);
        if (query.getAsOfSequence() == 0) {
            query.setAsOfSequence(reports.latestSequence());
        }
        LocalDateTime anchor = now;
        if (!blank(query.getAsOfTime())) {
            try {
                anchor = LocalDateTime.parse(query.getAsOfTime());
            } catch (java.time.format.DateTimeParseException error) {
                throw invalid("分页时间无效");
            }
            if (anchor.isAfter(now.plusMinutes(1)) || anchor.isBefore(now.minusDays(7))) {
                throw invalid("分页已过期，请重新检索");
            }
        }
        query.setAsOfTime(anchor.toString());
        NewsWindowPage page = reports.query(query, anchor.minusHours(query.getHours()), anchor);
        page.setAsOfTime(anchor.toString());
        try {
            page.setSourceHealth(gateway.readNewsFlashSources(new ResearchMaterialRequest("000001", "", 50)).getSourceHealth());
        } catch (RuntimeException error) {
            log.warn("来源状态暂不可用，继续展示已持久化新闻", error);
            page.setSourceHealth(Collections.emptyList());
        }
        return page;
    }

    public void scan(Consumer<List<NewsReport>> consumer) {
        LocalDateTime now = LocalDateTime.now(clock);
        long after = 0;
        while (true) {
            List<NewsReport> batch = reports.scan(now.minusHours(36), now, after, 250);
            if (batch.isEmpty()) {
                return;
            }
            consumer.accept(batch);
            after = batch.get(batch.size() - 1).getArrivalSequence();
        }
    }

    public NewsFeedSnapshot productionSnapshot() {
        List<NewsFeedItem> items = new ArrayList<>();
        Set<String> sources = new HashSet<>();
        scan(batch -> {
            for (NewsReport report : batch) {
                items.add(feedItem(report));
                sources.add(report.getSourceName());
            }
        });
        return new NewsFeedSnapshot(items, Collections.emptyList(), LocalDateTime.now(clock), sources.size());
    }

    public NewsReportDetail detail(String id) {
        NewsReportDetail detail = new NewsReportDetail();
        detail.setReport(reports.find(id).orElseThrow(() -> invalid("新闻不存在或已超过保留期限")));
        detail.setVersions(reports.versions(id));
        detail.setRelatedReports(reports.related(id));
        detail.setReactions(reactions.findByOrigin("NEWS_ITEM", id));
        return detail;
    }

    public void read(String id, int version) {
        if (!reports.markRead(id, version)) {
            throw invalid("新闻版本不存在，请刷新后重试");
        }
    }

    public void review(String id, String categoryCode, String reason) {
        NewsCategory category = categories.findEnabledByCode(categoryCode).orElseThrow(() -> invalid("分类不存在或已停用"));
        if (!reports.review(id, category.getCode(), category.getName(), clean(reason, 500))) {
            throw invalid("新闻不存在或已超过保留期限");
        }
    }

    public List<NewsSavedFilter> filters() {
        LocalDateTime now = LocalDateTime.now(clock);
        long sequence = reports.latestSequence();
        List<NewsSavedFilter> filters = reports.filters();
        for (NewsSavedFilter filter : filters) {
            NewsWindowQuery query = filter.getQuery();
            boolean unreadOnly = query.isUnreadOnly();
            query.setUnreadOnly(true);
            query.setAsOfSequence(sequence);
            query.setPage(0);
            filter.setUnreadCount(reports.count(query, now.minusHours(query.getHours()), now));
            query.setUnreadOnly(unreadOnly);
            query.setAsOfSequence(0);
        }
        return filters;
    }

    public NewsSavedFilter saveFilter(NewsSavedFilter value) {
        if (value == null || blank(value.getName()) || value.getQuery() == null) {
            throw invalid("请填写筛选名称和条件");
        }
        value.setId(UUID.randomUUID().toString());
        value.setName(clean(value.getName(), 40));
        normalize(value.getQuery());
        value.getQuery().setPage(0);
        value.getQuery().setAsOfSequence(0);
        value.getQuery().setAsOfTime(null);
        if (reports.filters().size() >= 50) {
            throw invalid("最多保存50个筛选，请先删除不再使用的筛选");
        }
        reports.saveFilter(value);
        return value;
    }

    public void deleteFilter(String id) {
        reports.deleteFilter(id);
    }

    private NewsReport report(ResearchMaterial material, List<NewsCategory> enabled, LocalDateTime now) {
        NewsReport report = new NewsReport();
        String externalId = material.getExternalId();
        if (blank(externalId)) {
            String anchor = blank(material.getUrl()) ? material.getTitle() + "|" + material.getPublishedAt() : material.getUrl();
            externalId = "url-" + DigestUtils.md5DigestAsHex(anchor.getBytes(StandardCharsets.UTF_8));
        }
        report.setId(material.getProviderCode() + ":" + externalId);
        report.setProviderCode(material.getProviderCode());
        report.setSourceName(sourceName(material.getProviderFamily()));
        report.setSourceTier(material.getSourceTier());
        report.setKind(material.getProviderCode().endsWith("_DIGEST") ? "ARTICLE" : "FLASH");
        report.setTitle(material.getTitle().trim());
        report.setContent(material.getContent().trim());
        report.setUrl(material.getUrl());
        report.setPublishedAt(material.getPublishedAt());
        report.setFirstSeenAt(now);
        report.setLastSeenAt(now);
        NewsItemClassification classification = rules.classify(new NewsClassificationCandidate(report.getId(),
                report.getTitle(), report.getContent(), report.getSourceName(), report.getPublishedAt()), enabled);
        report.setCategoryCode(classification.getCategoryCode());
        report.setCategoryName(enabled.stream().filter(category -> category.getCode().equals(classification.getCategoryCode()))
                .map(NewsCategory::getName).findFirst().orElse(null));
        report.setClassificationReason(classification.getReason());
        report.setRuleVersion(classification.getModelName());
        return report;
    }

    public NewsFeedItem feedItem(NewsReport report) {
        return new NewsFeedItem(report.getId(), report.getKind(), report.getTitle(), report.getContent(), report.getUrl(),
                report.getPublishedAt(), report.getProviderCode(), report.getSourceName(), report.getSourceTier(),
                report.getCategoryCode(), report.getCategoryName(), null, report.getClassificationReason());
    }

    private void normalize(NewsWindowQuery query) {
        query.setQuery(clean(query.getQuery(), 100));
        query.setExclude(clean(query.getExclude(), 100));
        query.setSource(blank(query.getSource()) ? "ALL" : clean(query.getSource(), 60));
        query.setCategory(blank(query.getCategory()) ? "ALL" : clean(query.getCategory(), 60));
        query.setKind(blank(query.getKind()) ? "ALL" : clean(query.getKind(), 20));
        if (query.getHours() < 1 || query.getHours() > 36 || query.getPage() < 0 || query.getPage() > 10000
                || query.getSize() < 1 || query.getSize() > 100 || query.getAsOfSequence() < 0
                || !List.of("ALL", "FLASH", "ARTICLE").contains(query.getKind())) {
            throw invalid("筛选范围无效：时间为1至36小时，每页1至100条");
        }
    }

    private String sourceName(String family) {
        if ("CLS".equals(family)) {
            return "财联社";
        }
        if ("THS".equals(family)) {
            return "同花顺";
        }
        if ("EASTMONEY".equals(family)) {
            return "东方财富";
        }
        return blank(family) ? "公开资讯" : family;
    }

    private String clean(String value, int max) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max) {
            throw invalid("输入内容过长，最多" + max + "个字符");
        }
        return result;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, message);
    }
}
