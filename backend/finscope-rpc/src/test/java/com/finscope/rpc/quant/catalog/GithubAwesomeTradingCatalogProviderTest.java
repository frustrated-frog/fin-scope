package com.finscope.rpc.quant.catalog;

import com.finscope.domain.quant.catalog.QuantStrategyCatalogSnapshot;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubAwesomeTradingCatalogProviderTest {
    @Test
    void fetchesFixedCommitAndChineseReadmeEndpoints() {
        RecordingRuntime runtime = new RecordingRuntime();
        GithubAwesomeTradingCatalogProvider provider = new GithubAwesomeTradingCatalogProvider(
                runtime, new AwesomeTradingMarkdownParser());

        QuantStrategyCatalogSnapshot value = provider.fetch();

        assertEquals("AWESOME_SYSTEMATIC_TRADING", value.getSourceCode());
        assertEquals("abc123", value.getCommitSha());
        assertEquals(1, value.getEntries().size());
        assertEquals(2, runtime.uris.size());
        assertEquals("api.github.com", runtime.uris.get(0).getHost());
        assertEquals("raw.githubusercontent.com", runtime.uris.get(1).getHost());
        assertTrue(runtime.uris.get(1).getPath().endsWith("/abc123/README_zh.md"));
    }

    private static class RecordingRuntime implements AcquisitionRuntime {
        private final List<URI> uris = new ArrayList<URI>();

        @Override
        public AcquisitionResponse fetch(AcquisitionRequest request) {
            uris.add(request.getUri());
            String body = uris.size() == 1 ? "{\"sha\":\"abc123\"}" : markdown();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            return new AcquisitionResponse(request.getUri(), request.getUri(), 200,
                    Collections.<String, String>emptyMap(), bytes, body,
                    "text/plain", "UTF-8", "hash", 1, 1, Instant.now());
        }

        private String markdown() {
            return "## 股票\n| 标题 | 夏普比率 | 挥发性 | 重新平衡 | 实施 | 来源 |\n"
                    + "|---|---|---|---|---|---|\n"
                    + "| 价值（账面价值）因素 | `0.5` | `10%` | 月度 | [实现](./static/strategies/value.py) | [论文](https://example.com/value) |\n# 书籍";
        }
    }
}
