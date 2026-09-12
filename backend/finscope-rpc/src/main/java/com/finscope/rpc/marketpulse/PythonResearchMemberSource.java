package com.finscope.rpc.marketpulse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.marketpulse.ResearchMemberReason;
import com.finscope.common.enums.marketpulse.ResearchMemberStatus;
import com.finscope.domain.marketpulse.ResearchMemberResult;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

/** 隔离Python逐股补齐协议，返回采集后实际覆盖情况而非HTTP成功标记。 */
@Component
public class PythonResearchMemberSource {
    private static final int REQUIRED_BARS = 22;
    private static final int TIMEOUT_MS = 75000;
    private static final int MAX_RESPONSE_BYTES = 65536;
    @Resource
    private FinanceHttpClient http;
    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    private final ObjectMapper json = new ObjectMapper();

    public ResearchMemberResult ensure(LocalDate businessDate, String instrumentCode) {
        try {
            URI uri = URI.create(baseUrl.replaceAll("/+$", "")
                    + "/v1/markets/CN-A/daily-research/members/" + instrumentCode + "?business_date=" + businessDate);
            var response = http.postJson("PYTHON_RESEARCH_MEMBER", uri, "{}",
                    Map.of("Content-Type", "application/json"), TIMEOUT_MS, MAX_RESPONSE_BYTES);
            if (response.getStatus() != 200) {
                throw invalid("成员补齐服务返回失败状态");
            }
            return parse(json.readTree(response.getBody()), businessDate, instrumentCode);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("RESEARCH_MEMBER_FETCH_FAILED", "成员行情补齐服务不可用", true, error);
        }
    }

    private ResearchMemberResult parse(JsonNode root, LocalDate requestedDate, String requestedCode) {
        if (!"research-member-v1".equals(text(root, "schema_version"))
                || !requestedDate.toString().equals(text(root, "business_date"))
                || !requestedCode.equals(text(root, "instrument_code"))) {
            throw invalid("成员补齐版本、日期或股票代码不匹配");
        }
        var value = new ResearchMemberResult();
        value.setInstrumentCode(requestedCode);
        value.setBusinessDate(requestedDate);
        try {
            value.setStatus(ResearchMemberStatus.valueOf(text(root, "status")));
            value.setReason(ResearchMemberReason.valueOf(text(root, "reason")));
        } catch (IllegalArgumentException error) {
            throw invalid("成员补齐状态未知");
        }
        value.setMessage(text(root, "message"));
        value.setValidBars(count(root, "valid_bars"));
        value.setRequiredBars(count(root, "required_bars"));
        if (value.getRequiredBars() != REQUIRED_BARS) {
            throw invalid("成员行情窗口不匹配");
        }
        boolean complete = value.getValidBars() == REQUIRED_BARS;
        if ((value.getStatus() == ResearchMemberStatus.READY) != complete
                || (value.getReason() == ResearchMemberReason.COMPLETE) != complete) {
            throw invalid("成员行情覆盖与状态不一致");
        }
        if (root.hasNonNull("source_code")) {
            value.setSourceCode(text(root, "source_code"));
        }
        return value;
    }

    private int count(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() < 0 || node.asInt() > REQUIRED_BARS) {
            throw invalid("成员行情计数无效");
        }
        return node.asInt();
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isTextual() || node.asText().isBlank() || node.asText().length() > 1000) {
            throw invalid("成员补齐字段无效：" + field);
        }
        return node.asText();
    }

    private ProviderContractException invalid(String message) {
        return new ProviderContractException("RESEARCH_MEMBER_CONTRACT_INVALID", message, false);
    }
}
