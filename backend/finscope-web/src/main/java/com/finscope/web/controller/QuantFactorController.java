package com.finscope.web.controller;

import com.finscope.domain.quant.factor.FactorDefinition;
import com.finscope.service.quant.factor.FactorRegistry;
import com.finscope.service.quant.factor.DatasetFactorAnalysisService;
import com.finscope.domain.quant.factor.FactorAnalysis;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/quant/factors")
public class QuantFactorController {
    @Resource private FactorRegistry registry;
    @Resource private DatasetFactorAnalysisService analysis;
    @GetMapping public List<FactorDefinition> list() { return registry.list(); }
    @GetMapping("/{code}/analysis") public FactorAnalysis analyze(@PathVariable String code, @RequestParam Long datasetId) {
        return analysis.analyze(datasetId, code);
    }
}
