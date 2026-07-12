package com.finscope.web.controller;

import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.web.request.quant.CreateLearningDatasetRequest;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/quant/datasets")
public class QuantDatasetController {
    @Resource private QuantDatasetService service;
    @GetMapping public List<QuantDataset> list() { return service.list(); }
    @GetMapping("/{id}") public QuantDataset get(@PathVariable Long id) { return service.get(id); }
    @PostMapping("/learning-sample")
    public QuantDataset createLearningSample(@RequestBody CreateLearningDatasetRequest request) {
        return service.createLearningSample(request.getName());
    }
}
