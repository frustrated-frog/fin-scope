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

    @Resource
    private FactorRegistry factorRegistry;
    @Resource
    private DatasetFactorAnalysisService datasetFactorAnalysisService;
    /**
     * 查询可用因子定义列表。
     *
     * @return 因子定义列表，包含因子编码、名称和计算说明。
     */
    @GetMapping public List<FactorDefinition> list() { return factorRegistry.list(); }
    /**
     * 分析指定数据集上的因子表现。
     *
     * @param code 因子编码。
     * @param datasetId 数据集 ID。
     * @return 因子分析结果，包含覆盖度、分布和表现指标。
     */
    @GetMapping("/{code}/analysis") public FactorAnalysis analyze(@PathVariable String code, @RequestParam Long datasetId) {
        return datasetFactorAnalysisService.analyze(datasetId, code);
    }
}
