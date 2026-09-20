package com.finscope.rpc.investmentobservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.investmentobservation.ReactionStockMatch;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 名称检索仅提供证券身份，不能作为事件与股票的关联证据。 */
@Component
public class ReactionStockNameLookup {
    @Resource
    private FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public List<ReactionStockMatch> search(String name) {
        if (name == null || name.length() < 3 || name.length() > 20) {
            return List.of();
        }
        try {
            URI uri = URI.create("https://searchapi.eastmoney.com/api/suggest/get?type=14&input="
                    + URLEncoder.encode(name, StandardCharsets.UTF_8));
            var response = http.get("REACTION_STOCK_NAME", uri, Map.of(), 256 * 1024, 3000);
            if (response.getStatus() != 200) {
                throw new IllegalArgumentException("证券名称服务状态异常");
            }
            var table = json.readTree(response.getBody()).path("QuotationCodeTable");
            if (!table.path("Status").isInt() || table.path("Status").asInt() != 0) {
                throw new IllegalArgumentException("证券名称响应状态无效");
            }
            var rows = table.path("Data");
            if (rows.isNull()) {
                return List.of();
            }
            if (!rows.isArray() || rows.size() > 100) {
                throw new IllegalArgumentException("证券名称响应格式无效");
            }
            List<ReactionStockMatch> result = new ArrayList<>();
            for (var row : rows) {
                String code = row.path("Code").asText();
                String stockName = row.path("Name").asText();
                if (!"AStock".equals(row.path("Classify").asText()) || !code.matches("[603489][0-9]{5}")
                        || stockName.length() < 3 || stockName.length() > 20) {
                    continue;
                }
                ReactionStockMatch match = new ReactionStockMatch();
                match.setCode(code);
                match.setName(stockName);
                result.add(match);
            }
            return result;
        } catch (Exception ex) {
            throw new ProviderContractException("REACTION_STOCK_LOOKUP_FAILED", "证券名称核对暂不可用", true, ex);
        }
    }
}
