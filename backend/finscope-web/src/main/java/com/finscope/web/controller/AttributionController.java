package com.finscope.web.controller;

import com.finscope.domain.attribution.AttributionReport;
import com.finscope.service.attribution.AttributionService;
import com.finscope.web.request.StartAttributionRequest;
import com.finscope.web.sse.AttributionSseRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attribution")
@Slf4j
public class AttributionController {
    @Resource
    private AttributionService attributionService;
    @Resource
    private AttributionSseRegistry sseRegistry;

    /** 触发深度归因，返回 taskId。 */
    @PostMapping("/start")
    public Map<String, String> start(@RequestBody StartAttributionRequest request) {
        String taskId = attributionService.startAttribution(
                request.getCode(), request.getType(), request.getName(), request.getChangePct());
        Map<String, String> result = new HashMap<>();
        result.put("taskId", taskId);
        return result;
    }

    /** 订阅归因研究进度（SSE 流式）。 */
    @GetMapping("/stream/{taskId}")
    public SseEmitter stream(@PathVariable String taskId) {
        return sseRegistry.subscribe(taskId);
    }

    /** 查询归因报告详情。 */
    @GetMapping("/reports/{reportId}")
    public AttributionReport report(@PathVariable Long reportId) {
        return attributionService.getReport(reportId);
    }

    /** 查询某标的最新归因（卡片摘要徽标用），无则返回空对象。 */
    @GetMapping("/latest")
    public AttributionReport latest(@RequestParam String code) {
        return attributionService.getLatestByCode(code);
    }

    /** 查询某标的归因历史。 */
    @GetMapping("/history")
    public List<AttributionReport> history(@RequestParam String code,
                                           @RequestParam(defaultValue = "10") int limit) {
        List<AttributionReport> list = attributionService.getHistory(code, limit);
        return list == null ? Collections.emptyList() : list;
    }
}