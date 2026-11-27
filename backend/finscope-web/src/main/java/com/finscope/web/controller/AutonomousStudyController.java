package com.finscope.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.finscope.common.api.ApiResponse;
import com.finscope.service.quant.study.AutonomousStudyService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.UUID;

@RestController
@RequestMapping("/api/quant/research-center")
public class AutonomousStudyController {
    @Resource
    private AutonomousStudyService studies;

    @GetMapping
    public ApiResponse<JsonNode> list(@RequestParam(defaultValue = "0") int offset) {
        return ApiResponses.success(studies.page(Math.max(0,offset)));
    }

    @GetMapping("/{id}")
    public ApiResponse<JsonNode> detail(@PathVariable long id) {
        return ApiResponses.success(studies.detail(id));
    }

    @PostMapping
    public ApiResponse<JsonNode> enqueue(@RequestHeader("Idempotency-Key") UUID key) {
        return ApiResponses.success(studies.detail(studies.enqueue("manual:" + key)));
    }

    @PostMapping("/{id}/question")
    public ApiResponse<JsonNode> question(@PathVariable long id, @RequestBody JsonNode input) {
        return ApiResponses.success(studies.question(id,input.path("question").textValue()));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<JsonNode> retry(@PathVariable long id) {
        return ApiResponses.success(studies.retry(id));
    }
}
