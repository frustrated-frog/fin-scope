package com.finscope.web.controller;

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

    @PostMapping("/from-capital-signal")
    public ResponseEntity<ResearchDraft> createFromCapitalSignal(
            @RequestBody(required = false) CapitalResearchDraftRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "研究草稿请求不能为空");
        }
        ResearchDraft value = service.createFromCapitalSignal(request.toCommand());
        return ResponseEntity.created(URI.create("/api/factor-research/research-drafts/" + value.getId()))
                .body(value);
    }

    @GetMapping("/{id}")
    public ResearchDraft get(@PathVariable Long id) {
        return service.get(id);
    }
}
