package com.finscope.web.controller;

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

@RestController
@RequestMapping("/api/quant")
public class QuantStrategyController {
    @Resource private QuantStrategyService service;
    @PostMapping("/strategy-drafts")
    public QuantStrategyDraft generate(@RequestBody GenerateQuantStrategyDraftRequest request) {
        if (request == null || request.getDatasetId() == null || request.getPrompt() == null || request.getPrompt().trim().isEmpty())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据集和策略描述不能为空");
        return service.generateDraft(request.getDatasetId(), request.getPrompt());
    }
    @PostMapping("/strategy-drafts/{id}/confirm")
    public ResponseEntity<QuantStrategyVersion> confirm(@PathVariable Long id) {
        QuantStrategyVersion value = service.confirm(id);
        return ResponseEntity.created(URI.create("/api/quant/strategies/" + value.getId())).body(value);
    }
    @GetMapping("/strategies") public List<QuantStrategyVersion> list() { return service.listVersions(); }
    @GetMapping("/strategies/{id}") public QuantStrategyVersion get(@PathVariable Long id) { return service.getVersion(id); }
}
