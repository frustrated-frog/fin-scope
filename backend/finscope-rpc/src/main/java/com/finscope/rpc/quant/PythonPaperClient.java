package com.finscope.rpc.quant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.quant.PaperAccountState;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ExternalServiceException;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
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

    public JsonNode jobs() {
        return call("/jobs", null);
    }

    public JsonNode submitJob(JsonNode request) {
        return call("/jobs", request);
    }

    public JsonNode retry(long id) {
        return call("/jobs/" + id + "/retry", json.createObjectNode());
    }

    public JsonNode replay(long id) {
        return call("/" + id + "/replay", json.createObjectNode());
    }

    public JsonNode transition(long id, PaperAccountState state) {
        return call("/" + id + "/state/" + state.name(), json.createObjectNode());
    }

    public JsonNode pendingReviews() {
        return call("/pending-reviews", null);
    }

    public JsonNode deploy(long id) {
        return call("/" + id + "/deploy", json.createObjectNode());
    }

    public JsonNode saveReview(long id, JsonNode review) {
        return call("/" + id + "/review", review);
    }

    public JsonNode research(JsonNode spec, boolean autoStart) {
        return call(autoStart ? "?auto_start=true" : "", spec);
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
        } catch (ProviderContractException error) {
            if ("HTTP_422".equals(error.getErrorType())) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                        "回测未能执行，请检查规则阈值、因子窗口与股票池的五分钟行情覆盖", error);
            }
            if ("HTTP_404".equals(error.getErrorType())) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "未找到模拟实验，请检查行情服务版本", error);
            }
            throw new ExternalServiceException(ErrorCode.MARKET_DATA_UNAVAILABLE, "模拟行情请求未完成，请重试", error);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "盘中实验请求失败，请检查行情服务", error);
        }
    }
}
