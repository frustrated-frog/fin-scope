package com.finscope.service.financials;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerResearchTextCleanerTest {

    @Test
    void removesBrokerCoverNoiseAndRejoinsWrappedChineseParagraphs() {
        String raw = "半导体行业\n" +
                "深 （可公开）国内企业级存储领军企业，AI打开后续\n" +
                "度买入 （维持）\n" +
                "成长空间\n" +
                "德明利（001309.SZ）深度报告\n" +
                "2025 年 10月 28日\n" +
                "投资要点：\n" +
                "刘梦麟\n" +
                "SAC 执业证书编号：S0340521070002  公司已实现存储模组全产品线布局，在企业级存\n" +
                "电话：0769-22110619 储领域布局领先。公司主要从事存储模组产品的研发与销售，并具备闪存主控\n" +
                "邮箱：analyst@example.com 芯片设计与开发能力，致力于提供完整解决方案。\n" +
                "目录\n" +
                "1. 国内存储模组头部企业 ........................................ 4\n" +
                "风险提示：原材料价格波动风险、终端需求不及预期。\n" +
                "本报告的风险等级为中风险。 2\n" +
                "请务必阅读末页声明。\n";

        String cleaned = new BrokerResearchTextCleaner().clean(raw);

        assertFalse(cleaned.contains("SAC 执业证书编号"));
        assertFalse(cleaned.contains("0769-22110619"));
        assertFalse(cleaned.contains("analyst@example.com"));
        assertFalse(cleaned.contains("请务必阅读末页声明"));
        assertFalse(cleaned.contains("......"));
        assertFalse(cleaned.contains("深 （可公开）"));
        assertTrue(cleaned.contains("公司已实现存储模组全产品线布局，在企业级存储领域布局领先。"));
        assertTrue(cleaned.contains("公司主要从事存储模组产品的研发与销售，并具备闪存主控芯片设计与开发能力"));
        assertTrue(cleaned.contains("风险提示：原材料价格波动风险、终端需求不及预期。"));
    }

    @Test
    void preservesSectionAndBulletBoundariesForFallbackAnalysis() {
        String raw = "投资要点：\n" +
                " AI驱动企业级存储需求增长，公司产品已经批量出货。\n" +
                " 投资建议：预计盈利改善，维持增持评级。\n" +
                " 风险提示：产品价格下降风险。\n";

        String cleaned = new BrokerResearchTextCleaner().clean(raw);

        assertTrue(cleaned.contains("投资要点：\n"));
        assertTrue(cleaned.contains("AI驱动企业级存储需求增长，公司产品已经批量出货。\n"));
        assertTrue(cleaned.contains("投资建议：预计盈利改善，维持增持评级。\n"));
        assertTrue(cleaned.contains("风险提示：产品价格下降风险。"));
    }
}
