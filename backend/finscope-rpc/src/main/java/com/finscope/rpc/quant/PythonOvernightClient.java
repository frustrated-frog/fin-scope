package com.finscope.rpc.quant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.domain.quant.overnight.OvernightCapturePlan;
import com.finscope.domain.quant.overnight.OvernightCaptureState;
import com.finscope.domain.quant.overnight.OvernightResearchInput;
import com.finscope.domain.quant.overnight.OvernightResearchReport;
import com.finscope.domain.quant.overnight.OvernightValidationSummary;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class PythonOvernightClient {
    @Resource
    private FinanceHttpClient http;
    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    @Value("${finscope.python-market-data.evaluation-timeout-ms:30000}")
    private int timeoutMs;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public OvernightResearchReport generate(OvernightResearchInput input) {
        try {
            FinanceHttpResponse response = http.postJson("PYTHON_OVERNIGHT", endpoint("generate"),
                    json.writeValueAsString(input), Collections.emptyMap(), timeoutMs);
            OvernightResearchReport report = json.readValue(response.getBody(), OvernightResearchReport.class);
            validate(report);
            if (!input.getInstrumentCode().equals(report.getInstrumentCode())
                    || !input.getSignalDate().toString().equals(report.getSignalDate())
                    || input.getMode() != report.getMode() || !input.getCutoff().equals(report.getCutoff())) {
                throw new IllegalArgumentException("隔夜预测身份不匹配");
            }
            return report;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("SCHEMA_DRIFT", "隔夜研究响应不符合契约", false, error);
        }
    }

    public List<OvernightResearchReport> history(boolean settle) {
        try {
            FinanceHttpResponse response = settle
                    ? http.postJson("PYTHON_OVERNIGHT", endpoint("settle"), "{}", Collections.emptyMap(), timeoutMs)
                    : http.get("PYTHON_OVERNIGHT", endpoint("history"), Collections.emptyMap());
            List<OvernightResearchReport> reports = json.readValue(response.getBody(),
                    new TypeReference<List<OvernightResearchReport>>() { });
            for (OvernightResearchReport report : reports) {
                validate(report);
            }
            return reports;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("SCHEMA_DRIFT", "隔夜研究档案暂不可用", false, error);
        }
    }

    public OvernightCaptureState captureState() {
        return readAudit("capture", OvernightCaptureState.class);
    }

    public OvernightValidationSummary validation() {
        return readAudit("validation", OvernightValidationSummary.class);
    }

    public OvernightCapturePlan configureCapture(OvernightCapturePlan plan) {
        try {
            String body = json.writeValueAsString(Map.of("enabled", plan.isEnabled(),
                    "instrumentCodes", plan.getInstrumentCodes(), "costBps", plan.getCostBps()));
            FinanceHttpResponse response = http.postJson("PYTHON_OVERNIGHT", endpoint("capture"),
                    body, Collections.emptyMap(), timeoutMs);
            return json.readValue(response.getBody(), OvernightCapturePlan.class);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("SCHEMA_DRIFT", "尾盘留档计划保存失败", false, error);
        }
    }

    private <T> T readAudit(String action, Class<T> type) {
        try {
            FinanceHttpResponse response = http.get("PYTHON_OVERNIGHT", endpoint(action), Collections.emptyMap());
            return json.readValue(response.getBody(), type);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("SCHEMA_DRIFT", "隔夜验收信息暂不可用", false, error);
        }
    }

    private URI endpoint(String action) {
        return URI.create(baseUrl.replaceAll("/+$", "") + "/v1/quant/overnight/" + action);
    }

    private void validate(OvernightResearchReport report) {
        if (report == null || report.getMode() == null || report.getStatus() == null
                || report.getEvidenceKind() == null || report.getInstrumentCode() == null
                || report.getSignalDate() == null || report.getDataThrough() == null
                || report.getTargets() == null || report.getWarnings() == null
                || !("overnight-local-v1".equals(report.getModelVersion())
                    || "overnight-local-v2".equals(report.getModelVersion()))
                || report.getInputFingerprint() == null
                || !report.getInputFingerprint().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("隔夜研究缺少日期、版本或审计证据");
        }
    }
}
