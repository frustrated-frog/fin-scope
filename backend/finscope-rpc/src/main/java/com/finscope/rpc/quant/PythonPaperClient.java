package com.finscope.rpc.quant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ExternalServiceException;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

@Component
public class PythonPaperClient {
    @Resource
    private FinanceHttpClient http;
    @Resource
    private ObjectMapper json;
    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    @Value("${finscope.python-market-data.evaluation-timeout-ms:30000}")
    private int timeoutMs;

    public JsonNode versions() {
        return call("", null);
    }

    public JsonNode detail(long id, LocalDate day) {
        return call("/" + id + (day == null ? "" : "?day=" + day), null);
    }

    public JsonNode deploy(long id) {
        return call("/" + id + "/deploy", json.createObjectNode());
    }

    public JsonNode research(JsonNode spec) {
        return call("", spec);
    }

    private JsonNode call(String suffix, JsonNode payload) {
        URI uri = URI.create(baseUrl + "/v1/paper/versions" + suffix);
        try {
            FinanceHttpResponse response = payload == null
                    ? http.get("PYTHON_PAPER", uri, Map.of(), 8 * 1024 * 1024, timeoutMs)
                    : http.postJson("PYTHON_PAPER", uri, payload.toString(), Map.of(), timeoutMs);
            JsonNode result = json.readTree(response.getBody());
            if (response.getStatus() == 422 || response.getStatus() == 404) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                        result.path("detail").asText("盘中策略未通过校验，请调整假设或股票池"));
            }
            if (response.getStatus() != 200 || result == null) {
                throw new ExternalServiceException("盘中实验服务暂不可用");
            }
            return result;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "盘中实验请求失败，请检查行情服务", error);
        }
    }
}
