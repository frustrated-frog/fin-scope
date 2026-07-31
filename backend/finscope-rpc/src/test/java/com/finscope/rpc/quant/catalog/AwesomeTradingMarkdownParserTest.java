package com.finscope.rpc.quant.catalog;

import com.finscope.domain.quant.catalog.QuantStrategyCatalogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwesomeTradingMarkdownParserTest {
    private final AwesomeTradingMarkdownParser parser = new AwesomeTradingMarkdownParser();

    @Test
    void parsesOnlyEquityStrategiesAndExpandsRelativeImplementationLinks() {
        String markdown = "## 货币\n"
                + "| 标题 | 夏普比率 | 挥发性 | 重新平衡 | 实施 | 来源 |\n"
                + "|---|---|---|---|---|---|\n"
                + "| 货币动量 | `1.0` | `8%` | 月度 | [QuantConnect](./static/strategies/fx.py) | [论文](https://example.com/fx) |\n\n"
                + "## 股票\n\n"
                + "| 标题 | 夏普比率 | 挥发性 | 重新平衡 | 实施 | 来源 |\n"
                + "|---|---|---|---|---|---|\n"
                + "| 股票中的低波动因素效应 | `0.717` | `11.5%` | 月度 | [QuantConnect](./static/strategies/low-vol.py) | [论文](https://example.com/paper) |\n\n"
                + "# 书籍\n| 标题 | 评论 |\n|---|---|\n| 一本书 | 好 |";

        List<QuantStrategyCatalogEntry> values = parser.parse(markdown);

        assertEquals(1, values.size());
        QuantStrategyCatalogEntry value = values.get(0);
        assertEquals("股票中的低波动因素效应", value.getTitle());
        assertEquals(0.717d, value.getReportedSharpe());
        assertEquals(0.115d, value.getReportedVolatility());
        assertEquals("月度", value.getRebalanceCadence());
        assertEquals("https://github.com/paperswithbacktest/awesome-systematic-trading/blob/main/static/strategies/low-vol.py",
                value.getImplementationUrl());
        assertEquals("https://example.com/paper", value.getPaperUrl());
        assertTrue(value.getExternalKey().contains("low-vol.py"));
    }

    @Test
    void preservesUnavailableReportedMetricsAsNull() {
        String markdown = equityTable("| 公司申报相似性 | `N/A` | N/A | 月度 |  | [论文](https://example.com/paper) |");

        QuantStrategyCatalogEntry value = parser.parse(markdown).get(0);

        assertEquals(null, value.getReportedSharpe());
        assertEquals(null, value.getReportedVolatility());
        assertEquals("https://example.com/paper", value.getPaperUrl());
    }

    @Test
    void rejectsEquitySectionWithoutCandidateRows() {
        String markdown = "## 股票\n\n| 标题 | 夏普比率 | 挥发性 | 重新平衡 | 实施 | 来源 |\n|---|---|---|---|---|---|\n\n# 书籍";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(markdown));

        assertEquals("上游股票策略目录为空或格式已变化", error.getMessage());
    }

    private String equityTable(String row) {
        return "## 股票\n\n| 标题 | 夏普比率 | 挥发性 | 重新平衡 | 实施 | 来源 |\n"
                + "|---|---|---|---|---|---|\n" + row + "\n\n# 书籍";
    }
}
