package com.finscope.common.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebContentTextTest {
    @Test
    void removesRepeatedNavigationAndBrokenLinksWhilePreservingFinancialNumbers() {
        String menu = "移动版 网页版) [](/) * 首页 * 快讯 * 新闻 您当前的位置：证券时报 > 公司 > 正文 ";
        String result = WebContentText.clean(menu + "长鑫科技成交额4.3亿元。\n" + menu + "成交828.08万股。");
        assertEquals("长鑫科技成交额4.3亿元。\n\n成交828.08万股。", result);
        assertFalse(result.contains("首页"));
    }

    @Test
    void preservesRealReferencesToWebAndMobileProducts() {
        String text = "公司移动版产品贡献收入增长20%。网页版产品上线后，公司披露了正文识别能力。";
        assertEquals(text, WebContentText.clean(text));
    }

    @Test
    void removesStandaloneMenusAndPdfContentsButKeepsFactsAndUniqueParagraphs() {
        String text = "首页 资讯 快讯 行情 日历 APP VIP会员 登录 注册\n研发投入增长20%。\n"
                + "研发投入增长20%。\n第九节 信息披露安排 ................... 154\n3 / 221\n风险仍需观察。";
        String result = WebContentText.clean(text);
        assertEquals("研发投入增长20%。\n\n风险仍需观察。", result);
        assertTrue(WebContentText.clean("[](* 首页 * 资讯 * 快讯 * 行情 * 日历 * APP # 长鑫科技产能增长。")
                .startsWith("长鑫科技产能增长。"));
    }
    @Test
    void stripsRawMarkdownMenusBeforeRecognizingTheArticleBody() {
        String raw = "[移动版](javascript:;) [网页版](javascript:void(0)) [](/) * [首页](/) * [新闻](/news) "
                + "您当前的位置：证券时报 > 公司 > 正文 兆易创新：仅持长鑫科技1.8%股权 控股股东持股降至7% "
                + "来源：证券时报网作者：张三 字号 超大 大 标准 小 点赞 分享 兆易创新仅持长鑫科技1.8%股权。";
        String clean = WebContentText.clean(raw);
        assertEquals("兆易创新仅持长鑫科技1.8%股权。", clean);
        assertFalse(clean.contains("7%"));
    }

}
