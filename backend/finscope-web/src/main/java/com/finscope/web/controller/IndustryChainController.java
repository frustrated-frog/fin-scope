package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.industrychain.IndustryChain;
import com.finscope.domain.industrychain.IndustryChainRevision;
import com.finscope.service.industrychain.IndustryChainService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 产业链图谱工作台 REST 接口。 */
@RestController
@RequestMapping("/api/industry-chains")
public class IndustryChainController {
    private final IndustryChainService service;

    public IndustryChainController(IndustryChainService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<IndustryChain>> list() {
        return ApiResponses.success(service.list());
    }

    @PostMapping
    public ApiResponse<IndustryChainService.Workspace> create(@RequestBody CreateRequest request) {
        return ApiResponses.success(service.create(request == null ? null : request.getName()));
    }

    @GetMapping("/{id}")
    public ApiResponse<IndustryChainService.Workspace> get(@PathVariable Long id) {
        return ApiResponses.success(service.get(id));
    }

    @PostMapping("/{id}/refresh")
    public ApiResponse<IndustryChainRevision> refresh(@PathVariable Long id) {
        return ApiResponses.success(service.refresh(id));
    }

    @GetMapping("/{id}/revisions")
    public ApiResponse<List<IndustryChainRevision>> revisions(@PathVariable Long id) {
        return ApiResponses.success(service.revisions(id));
    }

    @GetMapping("/{id}/focus")
    public ApiResponse<IndustryChainService.FocusResult> focus(
            @PathVariable Long id, @RequestParam String stockCode) {
        return ApiResponses.success(service.focus(id, stockCode));
    }

    public static final class CreateRequest {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
