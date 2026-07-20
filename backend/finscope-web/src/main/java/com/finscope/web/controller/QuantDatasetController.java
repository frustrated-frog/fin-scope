package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantDataSyncRun;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.data.QuantMarketDataSyncService;
import com.finscope.web.request.quant.CreateLearningDatasetRequest;
import com.finscope.web.request.quant.CreateQuantDatasetRequest;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import org.springframework.http.ResponseEntity;
import java.net.URI;

@RestController
@RequestMapping("/api/quant/datasets")
public class QuantDatasetController {
    @Resource private QuantDatasetService service;
    @Resource private QuantMarketDataSyncService marketDataSync;
    /**
     * 查询量化数据集列表。
     *
     * @return 量化数据集列表，包含数据集基础信息、状态和质量摘要。
     */
    @GetMapping public ApiResponse<List<QuantDataset>> list() { return ApiResponses.success(service.list()); }
    /**
     * 创建量化数据集。
     *
     * @param request 数据集创建请求，包含数据集名称和数据类型。
     * @return 201 Created 响应，响应体为新创建的数据集。
     */
    @PostMapping public ResponseEntity<ApiResponse<QuantDataset>> create(@RequestBody CreateQuantDatasetRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "请求不能为空");
        QuantDataset value = service.create(request.getName(), request.getDataKind());
        return ResponseEntity.created(URI.create("/api/quant/datasets/" + value.getId())).body(ApiResponses.success(value));
    }
    /**
     * 查询量化数据集详情。
     *
     * @param id 数据集 ID。
     * @return 指定数据集详情。
     */
    @GetMapping("/{id}") public ApiResponse<QuantDataset> get(@PathVariable Long id) { return ApiResponses.success(service.get(id)); }
    /**
     * 创建学习样例数据集。
     *
     * @param request 学习样例数据集创建请求，包含数据集名称。
     * @return 201 Created 响应，响应体为新创建的学习样例数据集。
     */
    @PostMapping("/learning-sample")
    public ResponseEntity<ApiResponse<QuantDataset>> createLearningSample(@RequestBody CreateLearningDatasetRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "请求不能为空");
        QuantDataset value = service.createLearningSample(request.getName());
        return ResponseEntity.created(URI.create("/api/quant/datasets/" + value.getId())).body(ApiResponses.success(value));
    }
    /**
     * 导入数据集日线行情。
     *
     * @param id 数据集 ID。
     * @param values 日线行情列表。
     * @return 导入行情后的数据集。
     */
    @PostMapping("/{id}/bars") public ApiResponse<QuantDataset> importBars(@PathVariable Long id, @RequestBody List<QuantDailyBar> values) {
        return ApiResponses.success(service.importBars(id, values));
    }
    /**
     * 导入数据集基本面快照。
     *
     * @param id 数据集 ID。
     * @param values 基本面快照列表。
     * @return 导入基本面后的数据集。
     */
    @PostMapping("/{id}/fundamentals") public ApiResponse<QuantDataset> importFundamentals(@PathVariable Long id, @RequestBody List<QuantFundamentalSnapshot> values) {
        return ApiResponses.success(service.importFundamentals(id, values));
    }
    /**
     * 导入数据集股票池成员。
     *
     * @param id 数据集 ID。
     * @param values 股票池成员列表。
     * @return 导入股票池后的数据集。
     */
    @PostMapping("/{id}/universe") public ApiResponse<QuantDataset> importUniverse(@PathVariable Long id, @RequestBody List<QuantUniverseMember> values) {
        return ApiResponses.success(service.importUniverse(id, values));
    }
    /** Incrementally synchronizes QFQ daily bars for the dataset's PIT universe. */
    @PostMapping("/{id}/market-data-sync") public ApiResponse<QuantDataSyncRun> syncMarketData(@PathVariable Long id) {
        return ApiResponses.success(marketDataSync.sync(id, "MANUAL"));
    }
    /** Returns durable synchronization history, newest first. */
    @GetMapping("/{id}/market-data-sync-runs") public ApiResponse<List<QuantDataSyncRun>> marketDataSyncRuns(@PathVariable Long id) {
        return ApiResponses.success(marketDataSync.runs(id));
    }
    /**
     * 查询数据集质量信息。
     *
     * @param id 数据集 ID。
     * @return 数据集质量 Map，包含 datasetId、status、summary、fingerprint 和 availableFactors。
     */
    @GetMapping("/{id}/quality") public ApiResponse<java.util.Map<String, Object>> quality(@PathVariable Long id) {
        QuantDataset value = service.get(id); java.util.Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        result.put("datasetId", value.getId()); result.put("status", value.getStatus()); result.put("summary", value.getQualitySummary());
        result.put("fingerprint", value.getFingerprint()); result.put("availableFactors", service.availableFactorCodes(id)); return ApiResponses.success(result);
    }
}
