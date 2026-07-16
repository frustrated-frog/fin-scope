package com.finscope.web.controller;

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

    @GetMapping("/factors")
    public List<ResearchFactorDefinition> factors() {
        return catalog.list();
    }

    @GetMapping("/factors/{namespace}/{code}/versions/{version}")
    public ResearchFactorDefinition factor(@PathVariable String namespace,
                                           @PathVariable String code,
                                           @PathVariable String version) {
        return catalog.get(namespace, code, version);
    }

    @PostMapping("/datasets/{datasetId}/capital-flow-freeze")
    public ResponseEntity<QuantDataset> freeze(@PathVariable Long datasetId,
                                               @RequestBody(required = false) FreezeCapitalFlowRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "冻结请求不能为空");
        }
        request.validate();
        QuantDataset dataset = freezeService.freeze(datasetId, request.getFrom(), request.getTo(),
                request.getAsOfTime());
        return ResponseEntity.ok(dataset);
    }
}
