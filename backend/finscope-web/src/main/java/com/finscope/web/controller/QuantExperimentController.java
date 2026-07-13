package com.finscope.web.controller;

import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.service.quant.experiment.QuantExperimentService;
import com.finscope.web.request.quant.CreateQuantExperimentRequest;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/quant/experiments")
public class QuantExperimentController {
    @Resource private QuantExperimentService service;
    /**
     * 查询量化实验列表。
     *
     * @return 量化实验列表，包含实验状态、回测结果和解释信息。
     */
    @GetMapping public List<QuantExperiment> list() { return service.list(); }
    /**
     * 查询量化实验详情。
     *
     * @param id 量化实验 ID。
     * @return 指定量化实验详情。
     */
    @GetMapping("/{id}") public QuantExperiment get(@PathVariable Long id) { return service.get(id); }
    /**
     * 创建量化实验。
     *
     * @param request 量化实验创建请求，包含策略版本 ID。
     * @return 202 Accepted 响应，响应体为已创建的量化实验。
     */
    @PostMapping public ResponseEntity<QuantExperiment> create(@RequestBody CreateQuantExperimentRequest request) {
        if (request == null || request.getStrategyVersionId() == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "策略版本不能为空");
        return ResponseEntity.accepted().body(service.create(request.getStrategyVersionId()));
    }
    /**
     * 生成或刷新量化实验解读。
     *
     * @param id 量化实验 ID。
     * @return 带有解读结果的量化实验。
     */
    @PostMapping("/{id}/interpretations")
    public QuantExperiment interpret(@PathVariable Long id) { return service.interpret(id); }
}
