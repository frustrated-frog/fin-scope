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

    /**
     * 触发标的深度归因研究。
     *
     * @param request 归因启动请求，包含标的代码、类型、名称、涨跌幅和行情日期。
     * @return 包含 taskId 和 reportId 的结果；taskId 用于订阅进度，reportId 用于读取报告。
     */
    @PostMapping("/start")
    public Map<String, String> start(@RequestBody StartAttributionRequest request) {
        AttributionService.AttributionStartResult started = attributionService.startAttribution(
                request.getCode(), request.getType(), request.getName(), request.getChangePct(), request.getQuoteDate());
        Map<String, String> result = new HashMap<>();
        result.put("taskId", started.getTaskId());
        result.put("reportId", String.valueOf(started.getReportId()));
        return result;
    }

    /**
     * 订阅归因研究进度。
     *
     * @param taskId 归因任务 ID。
     * @return SSE 连接，用于持续推送归因研究进度事件。
     */
    @GetMapping("/stream/{taskId}")
    public SseEmitter stream(@PathVariable String taskId) {
        return sseRegistry.subscribe(taskId);
    }

    /**
     * 查询归因报告详情。
     *
     * @param reportId 归因报告 ID。
     * @return 归因报告详情，包含驱动因素、证据和结论。
     */
    @GetMapping("/reports/{reportId}")
    public AttributionReport report(@PathVariable Long reportId) {
        return attributionService.getReport(reportId);
    }

    /**
     * 查询归因 Harness 运行状态。
     *
     * @param reportId 归因报告 ID。
     * @return 归因研究运行视图，包含整体状态和各研究轨道步骤。
     */
    @GetMapping("/reports/{reportId}/run")
    public AttributionService.AttributionResearchRunView researchRun(@PathVariable Long reportId) {
        return attributionService.getResearchRun(reportId);
    }

    /**
     * 查询某标的最新归因报告。
     *
     * @param code 标的代码。
     * @param type 标的类型。
     * @return 最新归因报告；若不存在则由服务层返回空结果。
     */
    @GetMapping("/latest")
    public AttributionReport latest(@RequestParam String code, @RequestParam String type) {
        return attributionService.getLatestByIdentity(code, type);
    }

    /**
     * 查询某标的归因历史。
     *
     * @param code 标的代码。
     * @param type 标的类型。
     * @param limit 返回条数上限。
     * @return 归因报告历史列表；服务层无结果时返回空列表。
     */
    @GetMapping("/history")
    public List<AttributionReport> history(@RequestParam String code,
                                           @RequestParam String type,
                                           @RequestParam(defaultValue = "10") int limit) {
        List<AttributionReport> list = attributionService.getHistory(code, type, limit);
        return list == null ? Collections.emptyList() : list;
    }
}
