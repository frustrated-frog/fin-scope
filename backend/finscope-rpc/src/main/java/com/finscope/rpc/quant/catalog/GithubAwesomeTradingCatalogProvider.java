package com.finscope.rpc.quant.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSnapshot;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class GithubAwesomeTradingCatalogProvider implements QuantStrategyCatalogProvider {
    public static final String SOURCE_CODE = "AWESOME_SYSTEMATIC_TRADING";
    private static final String REPOSITORY = "https://github.com/paperswithbacktest/awesome-systematic-trading";
    private static final URI COMMIT_ENDPOINT = URI.create(
            "https://api.github.com/repos/paperswithbacktest/awesome-systematic-trading/commits/main");
    private final AcquisitionRuntime runtime;
    private final AwesomeTradingMarkdownParser parser;
    private final ObjectMapper mapper = new ObjectMapper();

    public GithubAwesomeTradingCatalogProvider(AcquisitionRuntime runtime,
                                               AwesomeTradingMarkdownParser parser) {
        this.runtime = runtime;
        this.parser = parser;
    }

    @Override
    public QuantStrategyCatalogSnapshot fetch() {
        AcquisitionResponse commitResponse = runtime.fetch(request(COMMIT_ENDPOINT, 512 * 1024));
        String sha = commitSha(commitResponse.getBodyText());
        URI readme = URI.create("https://raw.githubusercontent.com/paperswithbacktest/awesome-systematic-trading/"
                + sha + "/README_zh.md");
        AcquisitionResponse readmeResponse = runtime.fetch(request(readme, 2 * 1024 * 1024));

        QuantStrategyCatalogSnapshot value = new QuantStrategyCatalogSnapshot();
        value.setSourceCode(SOURCE_CODE);
        value.setRepositoryUrl(REPOSITORY);
        value.setBranch("main");
        value.setCommitSha(sha);
        value.setFetchedAt(LocalDateTime.ofInstant(readmeResponse.getFetchedAt(), ZoneId.systemDefault()));
        value.setEntries(parser.parse(readmeResponse.getBodyText()));
        return value;
    }

    private AcquisitionRequest request(URI uri, int maxBytes) {
        return AcquisitionRequest.get(uri)
                .purpose("QUANT_STRATEGY_CATALOG")
                .header("Accept", "application/vnd.github+json")
                .connectTimeoutMs(4000)
                .readTimeoutMs(8000)
                .deadlineMs(12000)
                .maxResponseBytes(maxBytes)
                .maxRetries(1)
                .build();
    }

    private String commitSha(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            String sha = node.path("sha").asText("").trim();
            if (!sha.matches("[a-fA-F0-9]{6,64}")) throw new IllegalArgumentException("invalid sha");
            return sha;
        } catch (Exception error) {
            throw new IllegalArgumentException("上游仓库版本响应无效", error);
        }
    }
}
