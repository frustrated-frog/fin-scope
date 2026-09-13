package com.finscope.rpc.desktopths;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.desktopths.ThsCaptureStatus;
import com.finscope.domain.desktopths.ThsSnapshot;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Component
@Slf4j
public class ThsDesktopClient {
    @Resource
    private FinanceHttpClient http;
    @Value("${finscope.desktop-ths.base-url:http://127.0.0.1:18765}")
    private String baseUrl;
    private final ObjectMapper json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ThsSnapshot capture() {
        try {
            URI uri = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/capture");
            FinanceHttpResponse response = http.postJson("THS_DESKTOP", uri, "{}",
                    Map.of("X-FinScope-Desktop", "1"), 18000, 262144);
            if (response.getStatus() != 200) {
                return failure(ThsCaptureStatus.BRIDGE_UNAVAILABLE, "同花顺读取服务暂不可用，请确认本机读取程序已启动。");
            }
            ThsSnapshot snapshot = json.readValue(response.getBody(), ThsSnapshot.class);
            if (snapshot == null || snapshot.getStatus() == null || snapshot.getCapturedAt() == null
                    || !"THS_DESKTOP_AX".equals(snapshot.getSource()) || snapshot.getFields() == null
                    || snapshot.getBuySeats() == null || snapshot.getSellSeats() == null || snapshot.getWarnings() == null) {
                return failure(ThsCaptureStatus.READ_FAILED, "读取结果格式不完整，请重新读取。");
            }
            Instant.parse(snapshot.getCapturedAt());
            return snapshot;
        } catch (JsonProcessingException | IllegalArgumentException error) {
            log.warn("同花顺读取响应格式错误 type={}", error.getClass().getSimpleName());
            return failure(ThsCaptureStatus.READ_FAILED, "读取结果格式不正确，请重新读取。");
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("同花顺读取服务不可用 type={}", error.getClass().getSimpleName());
            return failure(ThsCaptureStatus.BRIDGE_UNAVAILABLE, "未连接到本机读取服务。请运行 desktop-ths/start.sh，再点击读取当前页面。");
        }
    }

    private ThsSnapshot failure(ThsCaptureStatus status, String message) {
        ThsSnapshot result = new ThsSnapshot();
        result.setStatus(status);
        result.setMessage(message);
        result.setSource("THS_DESKTOP_AX");
        result.setCapturedAt(Instant.now().toString());
        return result;
    }
}
