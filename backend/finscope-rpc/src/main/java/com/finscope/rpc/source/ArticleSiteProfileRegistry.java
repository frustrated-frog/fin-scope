package com.finscope.rpc.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArticleSiteProfileRegistry {
    private final List<ArticleSiteProfile> profiles;

    public ArticleSiteProfileRegistry() {
        this(defaultProfiles());
    }

    ArticleSiteProfileRegistry(List<ArticleSiteProfile> profiles) {
        this.profiles = Collections.unmodifiableList(new ArrayList<ArticleSiteProfile>(profiles));
    }

    public ArticleSiteProfile match(String url) {
        for (ArticleSiteProfile profile : profiles) {
            if (profile.matches(url)) {
                return profile;
            }
        }
        return null;
    }

    private static List<ArticleSiteProfile> defaultProfiles() {
        List<ArticleSiteProfile> profiles = new ArrayList<ArticleSiteProfile>();
        profiles.add(ArticleSiteProfile.builder("federal-reserve")
                .hosts("federalreserve.gov")
                .titles("h1", ".heading h1", ".page-title")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".article__time")
                .contents("main#article", "#article", "main", ".col-xs-12")
                .removes("nav", "aside", "script", "style", ".shareDL", ".socialBox", ".breadcrumb")
                .build());
        profiles.add(ArticleSiteProfile.builder("sec")
                .hosts("sec.gov")
                .titles("h1", ".page-title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".datetime")
                .contents("article", ".article-content", ".field--name-body", "main")
                .removes("nav", "aside", "script", "style", ".related-materials", ".views-element-container")
                .build());
        profiles.add(ArticleSiteProfile.builder("gov-cn")
                .hosts("gov.cn")
                .titles("h1", ".article h1", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[name=pubdate]", "meta[name=publishdate]", "meta[name=PubDate]", "time[datetime]")
                .contents(".pages_content", "#UCAP-CONTENT", ".article-content", ".content", "article")
                .removes("nav", "aside", "script", "style", ".editor", ".pages-date", ".share", ".mhide")
                .build());
        profiles.add(ArticleSiteProfile.builder("sina-finance")
                .hosts("finance.sina.com.cn")
                .titles(".main-title", "h1", "meta[property=og:title]")
                .summaries("meta[property=og:description]", "meta[name=description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".date")
                .contents("#artibody", ".article", ".article-content", "article")
                .removes("nav", "aside", "script", "style", ".article-bottom", ".feed-card-page", ".sina_keyword_ad_area2")
                .build());
        return profiles;
    }
}
