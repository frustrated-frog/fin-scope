package com.finscope.web.controller;

import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.web.request.quant.CreateLearningDatasetRequest;
import com.finscope.web.request.quant.CreateQuantDatasetRequest;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import org.springframework.http.ResponseEntity;
import java.net.URI;

@RestController
@RequestMapping("/api/quant/datasets")
public class QuantDatasetController {
    @Resource private QuantDatasetService service;
    @GetMapping public List<QuantDataset> list() { return service.list(); }
    @PostMapping public ResponseEntity<QuantDataset> create(@RequestBody CreateQuantDatasetRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        QuantDataset value = service.create(request.getName(), request.getDataKind());
        return ResponseEntity.created(URI.create("/api/quant/datasets/" + value.getId())).body(value);
    }
    @GetMapping("/{id}") public QuantDataset get(@PathVariable Long id) { return service.get(id); }
    @PostMapping("/learning-sample")
    public ResponseEntity<QuantDataset> createLearningSample(@RequestBody CreateLearningDatasetRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        QuantDataset value = service.createLearningSample(request.getName());
        return ResponseEntity.created(URI.create("/api/quant/datasets/" + value.getId())).body(value);
    }
    @PostMapping("/{id}/bars") public QuantDataset importBars(@PathVariable Long id, @RequestBody List<QuantDailyBar> values) {
        return service.importBars(id, values);
    }
    @PostMapping("/{id}/fundamentals") public QuantDataset importFundamentals(@PathVariable Long id, @RequestBody List<QuantFundamentalSnapshot> values) {
        return service.importFundamentals(id, values);
    }
    @PostMapping("/{id}/universe") public QuantDataset importUniverse(@PathVariable Long id, @RequestBody List<QuantUniverseMember> values) {
        return service.importUniverse(id, values);
    }
    @GetMapping("/{id}/quality") public java.util.Map<String, Object> quality(@PathVariable Long id) {
        QuantDataset value = service.get(id); java.util.Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        result.put("datasetId", value.getId()); result.put("status", value.getStatus()); result.put("summary", value.getQualitySummary());
        result.put("fingerprint", value.getFingerprint()); result.put("availableFactors", service.availableFactorCodes(id)); return result;
    }
}
