package com.finscope.common.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Cleans navigation and markup without changing factual numbers. */
public final class WebContentText {
    private static final Pattern NAVIGATION_PREFIX = Pattern.compile(
            "移动版\\s*网页版\\)?[^。！？]{0,1800}?"
                    + "您当前的位置[：:]\\s*[^>\\n]{1,40}>\\s*[^>\\n]{1,40}>\\s*正文\\s*");
    private static final String[] MENU_TERMS = {
            "首页", "资讯", "快讯", "行情", "日历", "APP", "VIP会员", "登录", "注册",
            "新闻", "要闻", "金融", "评论", "产经", "创投", "滚动", "视频", "直播", "路演",
            "投资", "新股", "基金", "港美股", "公众号", "电子报", "客户端",
            "股权质押", "并购重组", "股票回购", "重大合同", "关联交易", "十大股东", "股东户数", "限售解禁"
    };

    private WebContentText() {
    }

    public static String clean(String value) {
        String text = value == null ? "" : value.replace("\r", "");
        text = NAVIGATION_PREFIX.matcher(text).replaceAll("");
        text = text.replaceAll("(?m)您当前的位置[：:][^\\n]{0,160}?正文\\s*", "")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
                .replaceAll("\\[([^\\]\\n]+)]\\([^)]*\\)", "$1")
                .replaceAll("\\[\\s*]\\([^)]*\\)", "")
                .replaceAll("(?m)^\\s*\\[S\\d+]\\s*(?:-\\s*\\d+\\s*/)?\\s*\\[?", "")
                .replaceAll("(?i)Image\\s*\\d+\\s*:\\s*qrcode", "")
                .replaceAll("[\\t ]+", " ");
        text = NAVIGATION_PREFIX.matcher(text).replaceAll("");
        text = text.replaceFirst("(?s)^.*?(?:字号\\s*超大\\s*大\\s*标准\\s*小\\s*点赞\\s*分享"
                + "|缩小字体\\s*放大字体\\s*收藏\\s*微博\\s*分享(?:\\s*MD)?\\s*微信\\s*腾讯QQ\\s*QQ空间)\\s*", "")
                .replaceFirst("^[)\\s]+", "");
        Set<String> lines = new LinkedHashSet<String>();
        for (String line : text.split("\\n")) {
            String clean = line.trim();
            if (isMenu(clean)) {
                int heading = clean.indexOf("# ");
                if (heading >= 0) {
                    clean = clean.substring(heading + 2).trim();
                } else if (!clean.matches(".*[。！？].*")) {
                    continue;
                }
            }
            if (clean.isEmpty() || clean.matches(".*\\.{5,}\\s*\\d+\\s*")
                    || clean.matches("\\d+\\s*/\\s*\\d+")
                    || clean.matches("^(责任编辑|相关阅读|相关推荐|Share this page)[：:]?\\s*$")) {
                continue;
            }
            lines.add(clean);
        }
        return String.join("\n\n", lines).trim();
    }

    private static boolean isMenu(String text) {
        int hits = 0;
        for (String term : MENU_TERMS) {
            if (text.contains(term)) {
                hits++;
            }
        }
        return hits >= 6;
    }
}
