package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.factor.FactorAnalysis;
import com.finscope.domain.quant.factor.FactorDefinition;
import com.finscope.service.quant.experiment.QuantExperimentService;
import com.finscope.service.quant.factor.DatasetFactorAnalysisService;
import com.finscope.service.quant.factor.FactorRegistry;
import com.finscope.web.request.quant.CreateQuantExperimentRequest;
import com.finscope.web.response.ApiResponses;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.strategy.QuantStrategyService;
import com.finscope.web.request.quant.GenerateQuantStrategyDraftRequest;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSource;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSyncResult;
import com.finscope.service.quant.catalog.QuantStrategyCandidateDraftService;
import com.finscope.service.quant.catalog.QuantStrategyCatalogService;
import com.finscope.web.request.quant.CreateCatalogStrategyDraftRequest;
import com.finscope.common.exception.BizErrorCode;

@RestController
@RequestMapping("/api/quant")
public class QuantController {

    @Resource
    private QuantStrategyService quantStrategyService;
    @Resource
    private FactorRegistry factorRegistry;
    @Resource
    private DatasetFactorAnalysisService datasetFactorAnalysisService;
    @Resource
    private QuantExperimentService quantExperimentService;
    @Resource
    private QuantStrategyCatalogService quantStrategyCatalogService;
    @Resource
    private QuantStrategyCandidateDraftService quantStrategyCandidateDraftService;

    /**
     * 同步策略素材库。
     *
     * @return 策略素材库同步结果，包含新增和更新的候选数量。
     */
    @PostMapping("/catalog/sync")
    public ApiResponse<QuantStrategyCatalogSyncResult> syncCatalog() {
        return ApiResponses.success(quantStrategyCatalogService.sync());
    }

    /**
     * 查询策略素材库来源信息。
     *
     * @return 策略素材库来源；若尚未同步则抛出资源不存在异常。
     */
    @GetMapping("/catalog/source")
    public ApiResponse<QuantStrategyCatalogSource> catalogSource() {
        return ApiResponses.success(quantStrategyCatalogService.source().orElseThrow(() ->
                new BusinessException(BizErrorCode.STRATEGY_MATERIAL_LIBRARY_NOT_SYNCED)));
    }

    /**
     * 查询策略候选列表。
     *
     * @param compatibility 兼容状态过滤条件，取值 ADAPTABLE、NEEDS_FACTOR 或 UNSUPPORTED，可为空。
     * @param query 关键词过滤条件，可为空。
     * @return 符合条件的策略候选列表。
     */
    @GetMapping("/catalog/candidates")
    public ApiResponse<List<QuantStrategyCandidate>> catalogCandidates(
            @RequestParam(required = false) String compatibility,
            @RequestParam(required = false) String query) {
        if (compatibility != null && !compatibility.trim().isEmpty()
                && !java.util.Arrays.asList("ADAPTABLE", "NEEDS_FACTOR", "UNSUPPORTED").contains(compatibility.trim())) {
            throw new BusinessException(BizErrorCode.UNKNOWN_COMPATIBILITY_STATE);
        }
        return ApiResponses.success(quantStrategyCatalogService.list(compatibility, query));
    }

    /**
     * 查询策略候选详情。
     *
     * @param id 策略候选 ID。
     * @return 指定策略候选详情；若不存在则抛出资源不存在异常。
     */
    @GetMapping("/catalog/candidates/{id}")
    public ApiResponse<QuantStrategyCandidate> catalogCandidate(@PathVariable Long id) {
        return ApiResponses.success(quantStrategyCatalogService.find(id).orElseThrow(() ->
                new BusinessException(BizErrorCode.STRATEGY_CANDIDATE_NOT_FOUND)));
    }

    /**
     * 基于策略候选生成策略草稿。
     *
     * @param id 策略候选 ID。
     * @param request 草稿生成请求，包含数据集 ID。
     * @return 生成的量化策略草稿。
     */
    @PostMapping("/catalog/candidates/{id}/drafts")
    public ApiResponse<QuantStrategyDraft> generateCatalogDraft(@PathVariable Long id,
                                                                 @RequestBody CreateCatalogStrategyDraftRequest request) {
        if (request == null || request.getDatasetId() == null) {
            throw new BusinessException(BizErrorCode.DATASET_REQUIRED);
        }
        return ApiResponses.success(quantStrategyCandidateDraftService.generate(id, request.getDatasetId()));
    }

    /**
     * 根据数据集和提示词生成量化策略草稿。
     *
     * @param request 策略草稿生成请求，包含数据集 ID 和策略描述提示词。
     * @return 生成的量化策略草稿。
     */
    @PostMapping("/strategy-drafts")
    public ApiResponse<QuantStrategyDraft> generate(@RequestBody GenerateQuantStrategyDraftRequest request) {
        if (request == null || request.getDatasetId() == null || request.getPrompt() == null || request.getPrompt().trim().isEmpty())
            throw new BusinessException(BizErrorCode.DATASET_STRATEGY_REQUIRED);
        return ApiResponses.success(quantStrategyService.generateDraft(request.getDatasetId(), request.getPrompt()));
    }

    /**
     * 确认量化策略草稿并生成策略版本。
     *
     * @param id 策略草稿 ID。
     * @return 201 Created 响应，响应体为确认后的策略版本。
     */
    @PostMapping("/strategy-drafts/{id}/confirm")
    public ResponseEntity<ApiResponse<QuantStrategyVersion>> confirm(@PathVariable Long id) {
        QuantStrategyVersion value = quantStrategyService.confirm(id);
        return ResponseEntity.created(URI.create("/api/quant/strategies/" + value.getId())).body(ApiResponses.success(value));
    }

    /**
     * 查询量化策略版本列表。
     *
     * @return 已确认的量化策略版本列表。
     */
    @GetMapping("/strategies")
    public ApiResponse<List<QuantStrategyVersion>> listStrategies() {
        return ApiResponses.success(quantStrategyService.listVersions());
    }

    /**
     * 查询量化策略版本详情。
     *
     * @param id 策略版本 ID。
     * @return 指定量化策略版本详情。
     */
    @GetMapping("/strategies/{id}")
    public ApiResponse<QuantStrategyVersion> getStrategy(@PathVariable Long id) {
        return ApiResponses.success(quantStrategyService.getVersion(id));
    }

    /**
     * 查询可用因子定义列表。
     *
     * @return 因子定义列表，包含因子编码、名称和计算说明。
     */
    @GetMapping("/factors")
    public ApiResponse<List<FactorDefinition>> listFactors() {
        return ApiResponses.success(factorRegistry.list());
    }
    /**
     * 分析指定数据集上的因子表现。
     *
     * @param code 因子编码。
     * @param datasetId 数据集 ID。
     * @return 因子分析结果，包含覆盖度、分布和表现指标。
     */
    @GetMapping("/factors/{code}/analysis")
    public ApiResponse<FactorAnalysis> analyze(@PathVariable String code, @RequestParam Long datasetId) {
        return ApiResponses.success(datasetFactorAnalysisService.analyze(datasetId, code));
    }

    /**
     * 查询量化实验列表。
     *
     * @return 量化实验列表，包含实验状态、回测结果和解释信息。
     */
    @GetMapping("/experiments")
    public ApiResponse<List<QuantExperiment>> listExperiments() {
        return ApiResponses.success(quantExperimentService.list());
    }

    /**
     * 查询量化实验详情。
     *
     * @param id 量化实验 ID。
     * @return 指定量化实验详情。
     */
    @GetMapping("/experiments/{id}")
    public ApiResponse<QuantExperiment> getExperiments(@PathVariable Long id) {
        return ApiResponses.success(quantExperimentService.get(id));
    }

    /**
     * 创建量化实验。
     *
     * @param request 量化实验创建请求，包含策略版本 ID。
     * @return 202 Accepted 响应，响应体为已创建的量化实验。
     */
    @PostMapping("/experiments")
    public ResponseEntity<ApiResponse<QuantExperiment>> create(@RequestBody CreateQuantExperimentRequest request) {
        if (request == null || request.getStrategyVersionId() == null)
            throw new BusinessException(BizErrorCode.STRATEGY_VERSION_REQUIRED);
        return ResponseEntity.accepted().body(ApiResponses.success(quantExperimentService.create(request.getStrategyVersionId())));
    }

    /**
     * 生成或刷新量化实验解读。
     *
     * @param id 量化实验 ID。
     * @return 带有解读结果的量化实验。
     */
    @PostMapping("/experiments/{id}/interpretations")
    public ApiResponse<QuantExperiment> interpret(@PathVariable Long id) {
        return ApiResponses.success(quantExperimentService.interpret(id));
    }
}
