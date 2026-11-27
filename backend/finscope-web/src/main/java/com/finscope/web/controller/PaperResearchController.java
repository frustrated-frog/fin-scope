package com.finscope.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.finscope.common.api.ApiResponse;
import com.finscope.common.enums.quant.PaperAccountState;
import com.finscope.domain.quant.paper.PaperResearchInput;
import com.finscope.domain.quant.paper.ClassicPaperResearchInput;
import com.finscope.service.quant.paper.PaperResearchService;
import com.finscope.web.response.ApiResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/quant/paper/versions")
public class PaperResearchController {
    @Resource
    private PaperResearchService service;

    @GetMapping
    public ApiResponse<JsonNode> versions() {
        return ApiResponses.success(service.versions());
    }

    @PostMapping
    public ApiResponse<JsonNode> generate(@RequestBody PaperResearchInput input) {
        return ApiResponses.success(service.generate(input));
    }

    @GetMapping("/factors")
    public ApiResponse<JsonNode> factors() {
        return ApiResponses.success(service.factors());
    }

    @GetMapping("/factors/{id:[a-f0-9]{64}}")
    public ApiResponse<JsonNode> factor(@PathVariable String id) {
        return ApiResponses.success(service.factor(id));
    }

    @GetMapping("/datasets")
    public ApiResponse<JsonNode> datasets() {
        return ApiResponses.success(service.datasets());
    }

    @GetMapping("/jobs")
    public ApiResponse<JsonNode> jobs() {
        return ApiResponses.success(service.jobs());
    }

    @GetMapping("/jobs/{id}/evidence")
    public ApiResponse<JsonNode> evidence(@PathVariable long id) {
        return ApiResponses.success(service.evidence(id));
    }

    @PostMapping("/jobs")
    public ApiResponse<JsonNode> submit(@RequestBody PaperResearchInput input,
            @RequestParam(defaultValue = "false") boolean autoIterate,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID requestId) {
        return ApiResponses.success(service.submitCoding(input, autoIterate, requestId));
    }

    @PostMapping("/jobs/classic")
    public ApiResponse<JsonNode> submitClassic(@RequestBody ClassicPaperResearchInput input,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID requestId) {
        return ApiResponses.success(service.submitClassic(input, requestId));
    }

    @PostMapping("/jobs/ridge")
    public ApiResponse<JsonNode> submitRidge(@RequestBody PaperResearchInput input,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID requestId) {
        return ApiResponses.success(service.submitRidge(input, requestId));
    }

    @PostMapping("/jobs/{id}/retry")
    public ApiResponse<JsonNode> retry(@PathVariable long id) {
        return ApiResponses.success(service.retry(id));
    }

    @PostMapping("/{id}/replay")
    public ApiResponse<JsonNode> replay(@PathVariable long id) {
        return ApiResponses.success(service.replay(id));
    }

    @PostMapping("/{id}/state/{state}")
    public ApiResponse<JsonNode> transition(@PathVariable long id, @PathVariable PaperAccountState state) {
        return ApiResponses.success(service.transition(id, state));
    }

    @GetMapping("/{id}")
    public ApiResponse<JsonNode> detail(@PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ApiResponses.success(service.detail(id, day));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<JsonNode> review(@PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ApiResponses.success(service.review(id, day));
    }

    @PostMapping("/{id}/deploy")
    public ApiResponse<JsonNode> deploy(@PathVariable long id) {
        return ApiResponses.success(service.deploy(id));
    }
}
