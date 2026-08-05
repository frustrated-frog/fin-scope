package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.factorresearch.ResearchDraft;
import com.finscope.service.factorresearch.ResearchDraftService;
import com.finscope.web.request.factorresearch.CapitalResearchDraftRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/factor-research/research-drafts")
public class ResearchDraftController {
    private final ResearchDraftService service;

    public ResearchDraftController(ResearchDraftService service) {
        this.service = service;
    }

    /**
     * 基于资金信号创建研究草稿。
     *
     * @param request 资金研究草稿请求，包含资金信号来源等信息，可为空。
     * @return 201 Created 响应，响应体为新创建的研究草稿。
     */
    @PostMapping("/from-capital-signal")
    public ResponseEntity<ApiResponse<ResearchDraft>> createFromCapitalSignal(
            @RequestBody(required = false) CapitalResearchDraftRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "研究草稿请求不能为空");
        }
        ResearchDraft value = service.createFromCapitalSignal(request.toCommand());
        return ResponseEntity.created(URI.create("/api/factor-research/research-drafts/" + value.getId()))
                .body(ApiResponses.success(value));
    }

    /**
     * 查询研究草稿详情。
     *
     * @param id 研究草稿 ID。
     * @return 指定研究草稿详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<ResearchDraft> get(@PathVariable Long id) {
        return ApiResponses.success(service.get(id));
    }
}
