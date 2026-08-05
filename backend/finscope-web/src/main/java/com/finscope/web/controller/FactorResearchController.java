package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.factorresearch.ResearchFactorDefinition;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.service.factorresearch.CapitalFlowFreezeService;
import com.finscope.service.factorresearch.ResearchFactorCatalog;
import com.finscope.web.request.factorresearch.FreezeCapitalFlowRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/factor-research")
public class FactorResearchController {
    @Resource private ResearchFactorCatalog catalog;
    @Resource private CapitalFlowFreezeService freezeService;

    /**
     * 查询研究因子定义列表。
     *
     * @return 研究因子定义列表。
     */
    @GetMapping("/factors")
    public ApiResponse<List<ResearchFactorDefinition>> factors() {
        return ApiResponses.success(catalog.list());
    }

    /**
     * 查询指定版本的研究因子定义。
     *
     * @param namespace 因子命名空间。
     * @param code 因子编码。
     * @param version 因子版本。
     * @return 指定版本的研究因子定义。
     */
    @GetMapping("/factors/{namespace}/{code}/versions/{version}")
    public ApiResponse<ResearchFactorDefinition> factor(@PathVariable String namespace,
                                           @PathVariable String code,
                                           @PathVariable String version) {
        return ApiResponses.success(catalog.get(namespace, code, version));
    }

    /**
     * 冻结数据集的资金流数据快照。
     *
     * @param datasetId 数据集 ID。
     * @param request 冻结请求，包含起止时间和快照时点，可为空。
     * @return 冻结后的量化数据集。
     */
    @PostMapping("/datasets/{datasetId}/capital-flow-freeze")
    public ResponseEntity<ApiResponse<QuantDataset>> freeze(@PathVariable Long datasetId,
                                               @RequestBody(required = false) FreezeCapitalFlowRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "冻结请求不能为空");
        }
        request.validate();
        QuantDataset dataset = freezeService.freeze(datasetId, request.getFrom(), request.getTo(),
                request.getAsOfTime());
        return ResponseEntity.ok(ApiResponses.success(dataset));
    }
}
