package com.finscope.web.controller;

import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.strategy.QuantStrategyService;
import com.finscope.web.request.quant.GenerateQuantStrategyDraftRequest;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/quant")
public class QuantStrategyController {
    @Resource private QuantStrategyService service;
    @PostMapping("/strategy-drafts")
    public QuantStrategyDraft generate(@RequestBody GenerateQuantStrategyDraftRequest request) {
        return service.generateDraft(request.getDatasetId(), request.getPrompt());
    }
    @PostMapping("/strategy-drafts/{id}/confirm")
    public QuantStrategyVersion confirm(@PathVariable Long id) { return service.confirm(id); }
    @GetMapping("/strategies") public List<QuantStrategyVersion> list() { return service.listVersions(); }
}
