package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** 复用行情服务已核验的 A 股休市日历，不以个股停牌记录推断市场休市。 */
@Component
public class PythonTradingCalendarClient {
    @Autowired
    private FinanceHttpClient http;
    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    private final ObjectMapper json = new ObjectMapper();

    /** 交易日偏移 -5..0 和 1..5，0 为首个反应交易日前一日。 */
    public List<LocalDate> eventWindow(LocalDate onOrAfter) {
        try {
            URI uri = URI.create(baseUrl.replaceAll("/+$", "")
                    + "/v1/calendar/event-window?on_or_after=" + onOrAfter);
            FinanceHttpResponse response = http.get("PYTHON_TRADING_CALENDAR", uri, Collections.emptyMap(), 4096, 3000);
            if (response.getStatus() != 200) {
                throw new ProviderContractException("UPSTREAM_UNAVAILABLE", "观察窗口交易日历暂不可用", true);
            }
            JsonNode rows = json.readTree(response.getBody()).path("sessions");
            if (!rows.isArray() || rows.size() != 11) {
                throw new ProviderContractException("SCHEMA_DRIFT", "观察窗口必须包含 11 个交易日", false);
            }
            List<LocalDate> sessions = new ArrayList<>();
            for (JsonNode row : rows) {
                LocalDate day = LocalDate.parse(row.asText());
                if (!sessions.isEmpty() && !day.isAfter(sessions.get(sessions.size() - 1))) {
                    throw new ProviderContractException("SCHEMA_DRIFT", "交易日必须严格递增", false);
                }
                sessions.add(day);
            }
            if (sessions.get(6).isBefore(onOrAfter) || !sessions.get(5).isBefore(onOrAfter)) {
                throw new ProviderContractException("SCHEMA_DRIFT", "交易日窗口起点不匹配", false);
            }
            return sessions;
        } catch (ProviderContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderContractException("PYTHON_SERVICE_ERROR", "观察窗口交易日历请求失败", true, ex);
        }
    }

    public LocalDate previousSession(LocalDate before) {
        try {
            URI uri = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/calendar/previous-session?before=" + before);
            FinanceHttpResponse response = http.get("PYTHON_TRADING_CALENDAR", uri, Collections.emptyMap(), 4096, 3000);
            if (response.getStatus() != 200) {
                throw new ProviderContractException("UPSTREAM_UNAVAILABLE", "交易日历暂不可用", true);
            }
            LocalDate previous = LocalDate.parse(json.readTree(response.getBody()).path("previous_session").asText());
            if (!previous.isBefore(before)) {
                throw new ProviderContractException("SCHEMA_DRIFT", "上一交易日必须早于目标日期", false);
            }
            return previous;
        } catch (ProviderContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderContractException("PYTHON_SERVICE_ERROR", "交易日历请求失败", true, ex);
        }
    }
}
