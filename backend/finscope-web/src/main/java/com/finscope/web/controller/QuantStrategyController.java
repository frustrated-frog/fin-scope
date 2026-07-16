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

    @Resource
    private QuantStrategyService quantStrategyService;

    /**
     * 根据数据集和提示词生成量化策略草稿。
     *
     * @param request 策略草稿生成请求，包含数据集 ID 和策略描述提示词。
     * @return 生成的量化策略草稿。
     */
    @PostMapping("/strategy-drafts")
    public QuantStrategyDraft generate(@RequestBody GenerateQuantStrategyDraftRequest request) {
        if (request == null || request.getDatasetId() == null || request.getPrompt() == null || request.getPrompt().trim().isEmpty())
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "数据集和策略描述不能为空");
        return quantStrategyService.generateDraft(request.getDatasetId(), request.getPrompt());
    }

    /**
     * 确认量化策略草稿并生成策略版本。
     *
     * @param id 策略草稿 ID。
     * @return 201 Created 响应，响应体为确认后的策略版本。
     */
    @PostMapping("/strategy-drafts/{id}/confirm")
    public ResponseEntity<QuantStrategyVersion> confirm(@PathVariable Long id) {
        QuantStrategyVersion value = quantStrategyService.confirm(id);
        return ResponseEntity.created(URI.create("/api/quant/strategies/" + value.getId())).body(value);
    }

    /**
     * 查询量化策略版本列表。
     *
     * @return 已确认的量化策略版本列表。
     */
    @GetMapping("/strategies")
    public List<QuantStrategyVersion> list() {
        return quantStrategyService.listVersions();
    }

    /**
     * 查询量化策略版本详情。
     *
     * @param id 策略版本 ID。
     * @return 指定量化策略版本详情。
     */
    @GetMapping("/strategies/{id}")
    public QuantStrategyVersion get(@PathVariable Long id) {
        return quantStrategyService.getVersion(id);
    }
}
