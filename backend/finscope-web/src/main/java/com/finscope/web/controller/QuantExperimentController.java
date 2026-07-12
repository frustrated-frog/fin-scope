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
    @GetMapping public List<QuantExperiment> list() { return service.list(); }
    @GetMapping("/{id}") public QuantExperiment get(@PathVariable Long id) { return service.get(id); }
    @PostMapping public ResponseEntity<QuantExperiment> create(@RequestBody CreateQuantExperimentRequest request) {
        if (request == null || request.getStrategyVersionId() == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "策略版本不能为空");
        return ResponseEntity.accepted().body(service.create(request.getStrategyVersionId()));
    }
    @PostMapping("/{id}/interpretations")
    public QuantExperiment interpret(@PathVariable Long id) { return service.interpret(id); }
}
