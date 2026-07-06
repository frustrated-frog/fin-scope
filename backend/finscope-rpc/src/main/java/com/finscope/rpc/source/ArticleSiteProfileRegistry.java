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
        profiles.add(ArticleSiteProfile.builder("bea")
                .hosts("bea.gov")
                .titles("h1", ".page-title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".date-display-single")
                .contents("article", "main", ".article-content", ".field--name-body", ".news-release")
                .removes("nav", "aside", "script", "style", ".related", ".share", ".region-sidebar")
                .build());
        profiles.add(ArticleSiteProfile.builder("bis")
                .hosts("bis.org")
                .titles("h1", ".title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".date")
                .contents("article", "main", ".documentContent", "#cmsContent", ".publication", ".press-release")
                .removes("nav", "aside", "script", "style", ".related", ".share", ".breadcrumb")
                .build());
        profiles.add(ArticleSiteProfile.builder("bank-of-england")
                .hosts("bankofengland.co.uk")
                .titles("h1", ".page-title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".published-date")
                .contents("article", "main", ".article-content", ".page-content", ".main-content")
                .removes("nav", "aside", "script", "style", ".related", ".share", ".breadcrumb")
                .build());
        profiles.add(ArticleSiteProfile.builder("iea")
                .hosts("iea.org")
                .titles("h1", ".article-title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".date")
                .contents("article", "main", ".article-content", ".m-block--content", ".wysiwyg")
                .removes("nav", "aside", "script", "style", ".related", ".share", ".breadcrumb")
                .build());
        profiles.add(ArticleSiteProfile.builder("conference-board")
                .hosts("conference-board.org")
                .titles("h1", ".page-title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".date")
                .contents("article", "main", ".article-content", ".content", ".wysiwyg")
                .removes("nav", "aside", "script", "style", ".related", ".share", ".breadcrumb")
                .build());
        profiles.add(ArticleSiteProfile.builder("gov-cn")
                .hosts("www.gov.cn", "english.www.gov.cn")
                .titles("h1", ".article h1", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[name=pubdate]", "meta[name=publishdate]", "meta[name=PubDate]", "time[datetime]")
                .contents(".pages_content", "#UCAP-CONTENT", ".article-content", ".content", "article")
                .removes("nav", "aside", "script", "style", ".editor", ".pages-date", ".share", ".mhide")
                .build());
        profiles.add(ArticleSiteProfile.builder("nbs-cn")
                .hosts("stats.gov.cn")
                .titles("h1", ".title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[name=pubdate]", "meta[name=publishdate]", "meta[name=PubDate]", "time[datetime]")
                .contents(".TRS_Editor", "#zoom", ".xilan_con", ".article-content", ".content", "article")
                .removes("nav", "aside", "script", "style", ".editor", ".source", ".share", ".mhide")
                .build());
        profiles.add(ArticleSiteProfile.builder("csrc")
                .hosts("csrc.gov.cn")
                .titles("h1", ".title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[name=pubdate]", "meta[name=publishdate]", "meta[name=PubDate]", "time[datetime]")
                .contents(".TRS_Editor", ".detail-news", ".article-content", ".content", "article")
                .removes("nav", "aside", "script", "style", ".editor", ".source", ".share", ".mhide")
                .build());
        profiles.add(ArticleSiteProfile.builder("sina-finance")
                .hosts("finance.sina.com.cn")
                .titles(".main-title", "h1", "meta[property=og:title]")
                .summaries("meta[property=og:description]", "meta[name=description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".date")
                .contents("#artibody", ".article", ".article-content", "article")
                .removes("nav", "aside", "script", "style", ".article-bottom", ".feed-card-page", ".sina_keyword_ad_area2")
                .build());
        profiles.add(ArticleSiteProfile.builder("securities-times")
                .hosts("stcn.com")
                .titles("h1", ".title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".time")
                .contents("#ctrlfscont", ".txt_con", ".article-content", ".detail", ".content", "article")
                .removes("nav", "aside", "script", "style", ".related", ".share", ".breadcrumb")
                .build());
        profiles.add(ArticleSiteProfile.builder("securities-star")
                .hosts("stockstar.com")
                .titles("h1", ".title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".time")
                .contents("#articleContent", ".article-content", ".article", ".content", "article")
                .removes("nav", "aside", "script", "style", ".related", ".share", ".breadcrumb")
                .build());
        profiles.add(ArticleSiteProfile.builder("tonghuashun")
                .hosts("10jqka.com.cn")
                .titles("h1", ".title", "meta[property=og:title]")
                .summaries("meta[name=description]", "meta[property=og:description]")
                .dates("meta[property=article:published_time]", "time[datetime]", ".time")
                .contents(".article-content", ".main-text", "#article", ".content", "article")
                .removes("nav", "aside", "script", "style", ".related", ".share", ".breadcrumb")
                .build());
        return profiles;
    }
}
